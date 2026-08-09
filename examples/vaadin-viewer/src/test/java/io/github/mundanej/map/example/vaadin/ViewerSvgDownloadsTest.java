package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.streams.DownloadEvent;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.io.svg.SvgMapExports;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ViewerSvgDownloadsTest {
    @Test
    void canonicalExportBytesAreDefensivelyOwnedServedAndExpired() throws Exception {
        AtomicLong clock = new AtomicLong(100);
        ViewerSvgDownloads downloads = new ViewerSvgDownloads(clock::get);
        byte[] bytes = SvgMapExports.encode(emptySnapshot());
        downloads.publish(bytes);
        bytes[0] = 'x';

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DownloadEvent event = event(output);
        downloads.handler().handleDownloadRequest(event);

        assertTrue(new String(output.toByteArray(), StandardCharsets.UTF_8).startsWith("<?xml"));
        verify(event).setFileName("mundane-map.svg");
        verify(event).setContentType("image/svg+xml; charset=UTF-8");
        verify(event).setContentLength(output.size());
        verify(event.getResponse()).setHeader("Cache-Control", "private, no-store");
        verify(event.getResponse()).setHeader("X-Content-Type-Options", "nosniff");
        verify(event.getResponse()).setHeader("Content-Security-Policy", "sandbox");

        clock.addAndGet(ViewerSvgDownloads.LIFETIME_NANOS);
        DownloadEvent expired = event(new ByteArrayOutputStream());
        downloads.handler().handleDownloadRequest(expired);
        verify(expired.getResponse()).setStatus(HttpStatusCode.GONE.getCode());
        assertFalse(downloads.current().isPresent());

        downloads.close();
        assertThrows(IllegalStateException.class, () -> downloads.publish(new byte[] {1}));
    }

    @Test
    void returnedBytesCannotMutatePublishedDownloadAndSessionReportsPendingCapture() {
        ViewerSvgDownloads downloads = new ViewerSvgDownloads(() -> 0);
        downloads.publish(new byte[] {1, 2, 3});
        byte[] first = downloads.current().orElseThrow();
        first[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, downloads.current().orElseThrow());
        downloads.close();

        ViewerSession session = new ViewerSession();
        assertTrue(session.prepareSvgExport(emptySnapshot()));
        assertTrue(session.downloads().current().isPresent());
        assertFalse(session.prepareSvgExport());
        assertTrue(session.diagnosticText().startsWith("VECTOR_EXPORT_"));
        assertFalse(session.downloads().current().isPresent());
        assertEquals(
                ViewerSvgDownloads.MAXIMUM_BYTES,
                ViewerSession.SVG_EXPORT_LIMITS.maximumOutputBytes());
        session.close();
    }

    private static VectorExportSnapshot emptySnapshot() {
        return VectorExportSnapshot.of(
                100,
                80,
                Rgba.rgb(255, 255, 255),
                new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                1,
                List.of(),
                List.of());
    }

    private static DownloadEvent event(ByteArrayOutputStream output) {
        DownloadEvent event = mock(DownloadEvent.class);
        VaadinResponse response = mock(VaadinResponse.class);
        when(event.getResponse()).thenReturn(response);
        when(event.getOutputStream()).thenReturn(output);
        return event;
    }
}
