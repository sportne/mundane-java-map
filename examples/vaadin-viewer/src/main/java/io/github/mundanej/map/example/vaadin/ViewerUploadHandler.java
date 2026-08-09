package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.ElementRequestHandler;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Flow request adapter for one route-owned bounded upload staging area. */
@SuppressWarnings({"serial", "SE_TRANSIENT_FIELD_NOT_RESTORED"})
@SuppressFBWarnings(
        value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
        justification = "Flow stream handlers are route-scoped and are re-registered on attachment")
final class ViewerUploadHandler implements ElementRequestHandler {
    private static final long serialVersionUID = 1L;
    private static final long REQUEST_OVERHEAD_BYTES = 1024L * 1024;

    private final transient ViewerSession viewer;
    private final transient Consumer<Runnable> dispatcher;
    private final transient Transfer transfer;

    ViewerUploadHandler(ViewerSession viewer, Consumer<Runnable> dispatcher) {
        this(
                viewer,
                dispatcher,
                (handler, request, response, session, owner) ->
                        handler.handleRequest(request, response, session, owner));
    }

    ViewerUploadHandler(ViewerSession viewer, Consumer<Runnable> dispatcher, Transfer transfer) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
    }

    @Override
    public void handleRequest(
            VaadinRequest request, VaadinResponse response, VaadinSession session, Element owner)
            throws IOException {
        ViewerUploadStaging.UploadKind uploadKind;
        String requestedKind = request.getHeader("X-Mundane-Upload-Kind");
        if (requestedKind == null) {
            respond(response, "UPLOAD_TYPE_INVALID", 422, true);
            return;
        }
        try {
            uploadKind = ViewerUploadStaging.UploadKind.valueOf(requestedKind);
        } catch (IllegalArgumentException failure) {
            respond(response, "UPLOAD_TYPE_INVALID", 422, true);
            return;
        }
        ViewerUploadStaging.Batch batch;
        try {
            batch = viewer.uploads().begin(uploadKind);
        } catch (ViewerUploadStaging.ViewerUploadException failure) {
            respond(response, failure.code(), 422, true);
            return;
        }
        try (batch) {
            transfer.handle(requestHandler(batch), request, response, session, owner);
        }
    }

    UploadHandler requestHandler(ViewerUploadStaging.Batch batch) {
        return new RequestUploadHandler(batch);
    }

    private void processResponse(
            ViewerUploadStaging.Batch batch, String rejectedCode, UploadResult result) {
        if (result.exception() != null || !result.allAccepted()) {
            respond(
                    result.response(),
                    rejectedCode == null ? "UPLOAD_REJECTED" : rejectedCode,
                    422,
                    true);
            return;
        }
        try {
            ViewerUploadStaging.UploadSelection selection = batch.commit();
            dispatcher.accept(() -> viewer.openUploaded(selection));
            // The source workflow owns diagnostics after the upload is handed off. Publishing an
            // accepted status here could race with and overwrite a fast, stable open failure.
            respond(result.response(), "UPLOAD_ACCEPTED", HttpStatusCode.OK.getCode(), false);
        } catch (ViewerUploadStaging.ViewerUploadException failure) {
            respond(result.response(), failure.code(), 422, true);
        } catch (RuntimeException | Error failure) {
            respond(result.response(), "UPLOAD_OPEN_FAILED", 500, true);
        }
    }

    private void respond(VaadinResponse response, String code, int status, boolean report) {
        try {
            response.setStatus(status);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write("{\"code\":\"" + code + "\"}");
            if (report) {
                dispatcher.accept(() -> viewer.reportDiagnostic(code));
            }
        } catch (IOException failure) {
            response.setStatus(500);
        } catch (RuntimeException | Error ignored) {
            response.setStatus(500);
        }
    }

    @SuppressWarnings("serial")
    private final class RequestUploadHandler implements UploadHandler {
        private final ViewerUploadStaging.Batch batch;
        private String failureCode;

        private RequestUploadHandler(ViewerUploadStaging.Batch batch) {
            this.batch = batch;
        }

        @Override
        public void handleUploadRequest(UploadEvent event) {
            try {
                batch.add(event.getFileName(), event.getFileSize(), event.getInputStream());
            } catch (ViewerUploadStaging.ViewerUploadException failure) {
                failureCode = failure.code();
                event.reject(failureCode);
            }
        }

        @Override
        public void responseHandled(UploadResult result) {
            processResponse(batch, failureCode, result);
        }

        @Override
        public long getRequestSizeMax() {
            return ViewerUploadStaging.MAXIMUM_BATCH_BYTES + REQUEST_OVERHEAD_BYTES;
        }

        @Override
        public long getFileSizeMax() {
            return ViewerUploadStaging.MAXIMUM_FILE_BYTES;
        }

        @Override
        public long getFileCountMax() {
            return ViewerUploadStaging.MAXIMUM_FILES_PER_BATCH;
        }
    }

    @FunctionalInterface
    interface Transfer {
        void handle(
                UploadHandler handler,
                VaadinRequest request,
                VaadinResponse response,
                VaadinSession session,
                Element owner)
                throws IOException;
    }
}
