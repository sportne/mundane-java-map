package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ViewerUploadHandlerTest {
    @Test
    void elementRequestCreatesAndAlwaysClosesOneFreshBatch() throws Exception {
        ViewerSession session = new ViewerSession();
        ViewerUploadHandler handler =
                new ViewerUploadHandler(
                        session,
                        Runnable::run,
                        (upload, request, response, vaadinSession, owner) ->
                                assertEquals(
                                        ViewerUploadStaging.MAXIMUM_FILES_PER_BATCH,
                                        upload.getFileCountMax()));
        try {
            VaadinRequest request = mock(VaadinRequest.class);
            when(request.getHeader("X-Mundane-Upload-Kind")).thenReturn("WORKSPACE");
            handler.handleRequest(
                    request,
                    mock(VaadinResponse.class),
                    mock(VaadinSession.class),
                    new Element("form"));
        } finally {
            session.close();
        }
    }

    @Test
    void elementRequestRejectsUnclosedSourceKindsBeforeCreatingAFile() throws Exception {
        ViewerSession session = new ViewerSession();
        ViewerUploadHandler handler = new ViewerUploadHandler(session, Runnable::run);
        VaadinRequest request = mock(VaadinRequest.class);
        when(request.getHeader("X-Mundane-Upload-Kind")).thenReturn("ARBITRARY");
        Response response = response();
        try {
            handler.handleRequest(
                    request, response.vaadin(), mock(VaadinSession.class), new Element("form"));
            assertTrue(response.body().toString().contains("UPLOAD_TYPE_INVALID"));
            verify(response.vaadin()).setStatus(422);
        } finally {
            session.close();
        }
    }

    @Test
    void requestAdapterPublishesAcceptedBatchWithClosedTransportLimits() throws Exception {
        ViewerSession session = new ViewerSession();
        ViewerUploadHandler handler = new ViewerUploadHandler(session, Runnable::run);
        CountDownLatch sourceSettled = new CountDownLatch(1);
        session.addObserver(
                () -> {
                    if (!session.sourceBusy()
                            && !"No source diagnostics".equals(session.diagnosticText())) {
                        sourceSettled.countDown();
                    }
                });
        try (ViewerUploadStaging.Batch batch =
                session.uploads().begin(ViewerUploadStaging.UploadKind.RASTER)) {
            UploadHandler request = handler.requestHandler(batch);
            UploadEvent event = event("map.tif", new byte[] {1, 2, 3});
            request.handleUploadRequest(event);
            Response response = response();

            request.responseHandled(
                    new UploadResult(true, response.vaadin(), null, List.of("map.tif"), List.of()));

            assertEquals(ViewerUploadStaging.MAXIMUM_FILE_BYTES, request.getFileSizeMax());
            assertEquals(ViewerUploadStaging.MAXIMUM_FILES_PER_BATCH, request.getFileCountMax());
            assertTrue(request.getRequestSizeMax() > ViewerUploadStaging.MAXIMUM_BATCH_BYTES);
            assertTrue(response.body().toString().contains("UPLOAD_ACCEPTED"));
            verify(response.vaadin()).setStatus(200);
            assertTrue(sourceSettled.await(5, TimeUnit.SECONDS));
            assertNotEquals("UPLOAD_ACCEPTED", session.diagnosticText());
        } finally {
            session.close();
        }
    }

    @Test
    void realisticMultipartTransportStagesEveryShapefilePart() throws Exception {
        ViewerSession session = new ViewerSession();
        ViewerUploadHandler handler = new ViewerUploadHandler(session, Runnable::run);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getHeader("X-Mundane-Upload-Kind")).thenReturn("SHAPEFILE");
        when(servlet.getMethod()).thenReturn("POST");
        when(servlet.getContentType()).thenReturn("multipart/form-data; boundary=closed");
        when(servlet.getContentLengthLong()).thenReturn(3L);
        List<Part> parts =
                List.of(
                        part("roads.shp", new byte[] {1}),
                        part("roads.shx", new byte[] {2}),
                        part("roads.dbf", new byte[] {3}));
        when(servlet.getParts()).thenReturn(parts);
        VaadinServletRequest request =
                new VaadinServletRequest(servlet, mock(VaadinServletService.class));
        Response response = response();

        try {
            handler.handleRequest(
                    request,
                    response.vaadin(),
                    mock(VaadinSession.class),
                    new Owner().getElement());

            assertTrue(response.body().toString().contains("UPLOAD_ACCEPTED"));
            verify(response.vaadin()).setStatus(200);
        } finally {
            session.close();
        }
    }

    @Test
    void requestAdapterRejectsNamesWithoutEchoingThem() throws Exception {
        ViewerSession session = new ViewerSession();
        ViewerUploadHandler handler = new ViewerUploadHandler(session, Runnable::run);
        try (ViewerUploadStaging.Batch batch =
                session.uploads().begin(ViewerUploadStaging.UploadKind.SHAPEFILE)) {
            UploadHandler request = handler.requestHandler(batch);
            UploadEvent event = event("../secret.shp", new byte[] {1});
            request.handleUploadRequest(event);
            verify(event).reject("UPLOAD_NAME_INVALID");
            Response response = response();

            request.responseHandled(
                    new UploadResult(
                            false,
                            response.vaadin(),
                            null,
                            List.of(),
                            List.of(
                                    new UploadResult.RejectedFile(
                                            "../secret.shp", "untrusted value"))));

            assertTrue(response.body().toString().contains("UPLOAD_NAME_INVALID"));
            assertTrue(!response.body().toString().contains("secret"));
            verify(response.vaadin()).setStatus(422);
        } finally {
            session.close();
        }
    }

    private static UploadEvent event(String name, byte[] bytes) {
        UploadEvent event = mock(UploadEvent.class);
        when(event.getFileName()).thenReturn(name);
        when(event.getFileSize()).thenReturn((long) bytes.length);
        when(event.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return event;
    }

    private static Part part(String name, byte[] bytes) throws Exception {
        Part part = mock(Part.class);
        when(part.getSubmittedFileName()).thenReturn(name);
        when(part.getSize()).thenReturn((long) bytes.length);
        when(part.getContentType()).thenReturn("application/octet-stream");
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return part;
    }

    private static Response response() throws Exception {
        VaadinResponse response = mock(VaadinResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new Response(response, body);
    }

    private record Response(VaadinResponse vaadin, StringWriter body) {}

    @Tag("form")
    private static final class Owner extends Component {
        private static final long serialVersionUID = 1L;
    }
}
