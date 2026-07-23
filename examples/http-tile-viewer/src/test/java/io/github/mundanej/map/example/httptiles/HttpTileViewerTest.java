package io.github.mundanej.map.example.httptiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.awt.MapView;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HttpTileViewerTest {
    @Test
    void acquiresOffEdtThenRendersDetachedMosaicWithBroadColorEvidence() throws Exception {
        RasterSource source = HttpTileViewer.acquireDemo();
        assertEquals(512, source.metadata().width());
        assertEquals("HTTP_TILE_MISSING", source.openingDiagnostics().entries().getFirst().code());
        assertEquals(
                0,
                source.read(
                                new RasterRequest(
                                        new RasterWindow(300, 300, 1, 1), 1, 1, Optional.empty()),
                                CancellationToken.none())
                        .pixels()
                        .rgbaAt(0, 0));
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(HttpTileViewer.createView(source)));
        BufferedImage image = new BufferedImage(800, 500, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(
                () -> {
                    Graphics2D graphics = image.createGraphics();
                    try {
                        view.get().paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                });
        boolean red = false;
        boolean blue = false;
        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                Color color = new Color(image.getRGB(x, y), true);
                red |= color.getRed() > 180 && color.getBlue() < 100;
                blue |= color.getBlue() > 180 && color.getRed() < 100;
            }
        }
        assertTrue(red);
        assertTrue(blue);
        SwingUtilities.invokeAndWait(() -> view.get().close());
        assertTrue(source.isClosed());
    }

    @Test
    void rejectsAcquisitionOnTheEventThread() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () ->
                        failure.set(
                                assertThrows(
                                        IllegalStateException.class, HttpTileViewer::acquireDemo)));
        assertFalse(failure.get().getMessage().isBlank());
    }

    @Test
    void workerSessionInstallsAndClosesItsOwnedView() throws Exception {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        JLabel status = new JLabel();
        AtomicReference<HttpTileViewer.ViewerSession> session = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> session.set(HttpTileViewer.startLoading(panel, status)));
        assertTrue(session.get().await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(status.getText().startsWith("Detached 2 × 2"));
        assertTrue(session.get().installedView() != null);
        SwingUtilities.invokeAndWait(session.get()::close);
        assertTrue(session.get().installedView() == null);
        assertThrows(IllegalStateException.class, () -> HttpTileViewer.startLoading(panel, status));
    }
}
