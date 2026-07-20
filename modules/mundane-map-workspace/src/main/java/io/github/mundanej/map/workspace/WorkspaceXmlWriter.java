package io.github.mundanej.map.workspace;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class WorkspaceXmlWriter {
    private final BoundedSink sink;

    private WorkspaceXmlWriter(WorkspaceDocument document, WorkspaceLimits limits) {
        sink =
                new BoundedSink(
                        limits,
                        WorkspaceDocument.logicalModelBytes(document.view(), document.layers()));
        document(document);
    }

    static byte[] encode(WorkspaceDocument document, WorkspaceLimits limits) {
        return new WorkspaceXmlWriter(document, limits).sink.bytes();
    }

    private void document(WorkspaceDocument document) {
        line("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        line("<workspace xmlns=\"urn:mundanej:map:workspace\" version=\"1\">");
        WorkspaceViewState view = document.view();
        text("  <view map-crs=\"");
        attribute(view.mapCrsKey());
        text("\" display-crs=\"");
        attribute(view.displayCrsKey());
        line("\"");
        text("        center-x=\"");
        attribute(Double.toString(view.centerX()));
        text("\" center-y=\"");
        attribute(Double.toString(view.centerY()));
        text("\" units-per-pixel=\"");
        attribute(Double.toString(view.unitsPerPixel()));
        line("\"/>");
        if (document.layers().isEmpty()) {
            line("  <layers/>");
        } else {
            line("  <layers>");
            for (WorkspaceLayerDefinition layer : document.layers()) {
                if (layer instanceof WorkspaceFeatureLayer feature) {
                    feature(feature);
                } else if (layer instanceof WorkspaceRasterLayer raster) {
                    raster(raster);
                } else {
                    throw new AssertionError("unreachable workspace layer variant");
                }
            }
            line("  </layers>");
        }
        line("</workspace>");
    }

    private void feature(WorkspaceFeatureLayer layer) {
        text("    <feature-layer id=\"");
        attribute(layer.id());
        text("\" name=\"");
        attribute(layer.name());
        line("\">");
        source(layer.source());
        WorkspaceSymbolReferences symbols = layer.symbols();
        text("      <symbols catalog=\"");
        attribute(symbols.catalogId());
        text("\" marker=\"");
        attribute(symbols.markerName());
        text("\" line=\"");
        attribute(symbols.lineName());
        text("\" fill=\"");
        attribute(symbols.fillName());
        line("\"/>");
        line("    </feature-layer>");
    }

    private void raster(WorkspaceRasterLayer layer) {
        text("    <raster-layer id=\"");
        attribute(layer.id());
        text("\" name=\"");
        attribute(layer.name());
        text("\" interpolation=\"");
        attribute(layer.interpolation().name());
        text("\" opacity=\"");
        attribute(Double.toString(layer.opacity()));
        line("\">");
        source(layer.source());
        line("    </raster-layer>");
    }

    private void source(WorkspaceSourceReference source) {
        text("      <source opener=\"");
        attribute(source.openerId());
        text("\" id=\"");
        attribute(source.identity().id());
        line("\"");
        text("              name=\"");
        attribute(source.identity().displayName());
        text("\" path=\"");
        attribute(source.path().value());
        line("\"/>");
    }

    private void attribute(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            switch (codePoint) {
                case '&' -> text("&amp;");
                case '<' -> text("&lt;");
                case '>' -> text("&gt;");
                case '"' -> text("&quot;");
                case '\t' -> text("&#9;");
                case '\n' -> text("&#10;");
                case '\r' -> text("&#13;");
                default -> text(new String(Character.toChars(codePoint)));
            }
            offset += Character.charCount(codePoint);
        }
    }

    private void line(String value) {
        text(value);
        text("\n");
    }

    private void text(String value) {
        sink.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class BoundedSink {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final WorkspaceLimits limits;
        private final long modelBytes;

        private BoundedSink(WorkspaceLimits limits, long modelBytes) {
            this.limits = limits;
            this.modelBytes = modelBytes;
            if (modelBytes > limits.operationBytes()) {
                throw WorkspaceFailures.limit(
                        "operationBytes", modelBytes, limits.operationBytes());
            }
        }

        private void write(byte[] bytes) {
            long requestedOutput = (long) output.size() + bytes.length;
            if (requestedOutput > limits.inputOutputBytes()) {
                throw WorkspaceFailures.limit(
                        "outputBytes", requestedOutput, limits.inputOutputBytes());
            }
            long requestedOperation = modelBytes + requestedOutput;
            if (requestedOperation > limits.operationBytes()) {
                throw WorkspaceFailures.limit(
                        "operationBytes", requestedOperation, limits.operationBytes());
            }
            output.writeBytes(bytes);
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
