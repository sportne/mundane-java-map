package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.SymbolTransforms;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class SvgExportWriter {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final VectorExportSnapshot snapshot;
    private final SvgExportLimits limits;
    private final CancellationToken cancellation;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(8_192);
    private MapScreenBasis basis;
    private long elementCount;
    private long pathCommandCount;

    SvgExportWriter(
            VectorExportSnapshot snapshot, SvgExportLimits limits, CancellationToken cancellation) {
        this.snapshot = snapshot;
        this.limits = limits;
        this.cancellation = cancellation;
    }

    byte[] encode() {
        checkCancelled();
        try {
            basis = createBasis(snapshot.viewFrame());
        } catch (IllegalArgumentException exception) {
            throw frameFailure(exception);
        }
        element();
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"");
        integer(snapshot.widthPixels());
        append("\" height=\"");
        integer(snapshot.heightPixels());
        append("\" viewBox=\"0 0 ");
        integer(snapshot.widthPixels());
        append(" ");
        integer(snapshot.heightPixels());
        append("\">\n");

        element();
        append("  <defs>\n");
        element();
        append("    <clipPath id=\"v0\" clipPathUnits=\"userSpaceOnUse\">\n");
        element();
        append("      <rect x=\"0\" y=\"0\" width=\"");
        integer(snapshot.widthPixels());
        append("\" height=\"");
        integer(snapshot.heightPixels());
        append("\"/>\n");
        append("    </clipPath>\n");
        append("  </defs>\n");

        element();
        append("  <g clip-path=\"url(#v0)\">\n");
        writeBackground();
        for (VectorExportSnapshot.Primitive primitive : snapshot.primitives()) {
            checkCancelled();
            try {
                if (primitive.screenGeometry() instanceof PointGeometry point) {
                    writeMarker(point.coordinate(), (VectorMarkerSymbol) primitive.symbol(), 1.0);
                } else if (primitive.screenGeometry() instanceof LineStringGeometry line) {
                    writeLine(line.coordinates(), (SolidLineSymbol) primitive.symbol(), 1.0);
                } else {
                    writePolygon(
                            (PolygonGeometry) primitive.screenGeometry(),
                            (SolidFillSymbol) primitive.symbol(),
                            1.0);
                }
            } catch (ArithmeticException | IllegalArgumentException | SymbolException exception) {
                throw valueFailure(primitive, "symbolTransform", exception);
            }
        }
        for (int index = 0; index < snapshot.labels().size(); index++) {
            checkCancelled();
            writeLabel(snapshot.labels().get(index));
        }
        append("  </g>\n");
        append("</svg>\n");
        checkCancelled();
        byte[] result = output.toByteArray();
        checkOwnedBytes(result.length);
        checkCancelled();
        return result;
    }

    private void writeBackground() {
        element();
        append("    <rect x=\"0\" y=\"0\" width=\"");
        integer(snapshot.widthPixels());
        append("\" height=\"");
        integer(snapshot.heightPixels());
        append("\" fill=\"");
        color(snapshot.background());
        append("\"");
        opacity("fill-opacity", snapshot.background().alpha() / 255.0);
        append("/>\n");
    }

    private void writeMarker(Coordinate point, VectorMarkerSymbol symbol, double inheritedOpacity) {
        MarkerTransform transform =
                SymbolTransforms.marker(symbol.viewBox(), symbol.placement(), point, basis);
        double symbolOpacity = inheritedOpacity * symbol.opacity();
        if (hasClosedSubpath(symbol.path())) {
            element();
            append("    <path d=\"");
            markerPath(symbol.path(), transform, true);
            append("\" fill=\"");
            color(symbol.fill());
            append("\" fill-rule=\"evenodd\"");
            opacity("fill-opacity", symbolOpacity * symbol.fill().alpha() / 255.0);
            append("/>\n");
        }
        if (symbol.stroke().isPresent()) {
            SymbolStroke stroke = symbol.stroke().orElseThrow();
            element();
            append("    <path d=\"");
            markerPath(symbol.path(), transform, false);
            append("\" fill=\"none\" stroke=\"");
            color(stroke.color());
            append("\" stroke-width=\"");
            number(SymbolTransforms.screenLength(stroke.width(), basis));
            append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
            opacity("stroke-opacity", symbolOpacity * stroke.color().alpha() / 255.0);
            append("/>\n");
        }
    }

    private void writeLine(
            CoordinateSequence coordinates, SolidLineSymbol symbol, double inheritedOpacity) {
        if (allCoincident(coordinates)) {
            return;
        }
        SymbolStroke stroke = symbol.stroke();
        element();
        append("    <path d=\"");
        sequencePath(coordinates, false);
        append("\" fill=\"none\" stroke=\"");
        color(stroke.color());
        append("\" stroke-width=\"");
        number(SymbolTransforms.screenLength(stroke.width(), basis));
        append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
        opacity(
                "stroke-opacity",
                inheritedOpacity * symbol.opacity() * stroke.color().alpha() / 255.0);
        append("/>\n");
    }

    private void writePolygon(
            PolygonGeometry polygon, SolidFillSymbol symbol, double inheritedOpacity) {
        element();
        append("    <path d=\"");
        ringPath(polygon.exterior());
        for (CoordinateSequence hole : polygon.holes()) {
            append(" ");
            ringPath(hole);
        }
        append("\" fill=\"");
        color(symbol.fill());
        append("\" fill-rule=\"evenodd\"");
        double fillOpacity = inheritedOpacity * symbol.opacity();
        opacity("fill-opacity", fillOpacity * symbol.fill().alpha() / 255.0);
        append("/>\n");
        if (symbol.outline().isPresent()) {
            SolidLineSymbol outline = (SolidLineSymbol) symbol.outline().orElseThrow();
            writeRingOutline(polygon.exterior(), outline, fillOpacity);
            for (CoordinateSequence hole : polygon.holes()) {
                writeRingOutline(hole, outline, fillOpacity);
            }
        }
    }

    private void writeRingOutline(
            CoordinateSequence ring, SolidLineSymbol outline, double inheritedOpacity) {
        SymbolStroke stroke = outline.stroke();
        element();
        append("    <path d=\"");
        ringPath(ring);
        append("\" fill=\"none\" stroke=\"");
        color(stroke.color());
        append("\" stroke-width=\"");
        number(SymbolTransforms.screenLength(stroke.width(), basis));
        append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
        opacity(
                "stroke-opacity",
                inheritedOpacity * outline.opacity() * stroke.color().alpha() / 255.0);
        append("/>\n");
    }

    private void writeLabel(VectorExportSnapshot.Label label) {
        element();
        append("    <text x=\"");
        number(label.baselineX());
        append("\" y=\"");
        number(label.baselineY());
        append("\" fill=\"");
        color(label.style().color());
        append("\"");
        opacity("fill-opacity", label.style().color().alpha() / 255.0);
        append(" font-family=\"sans-serif\" font-size=\"");
        number(label.style().sizePixels());
        append("\" font-weight=\"");
        append(label.style().weight() == LabelWeight.BOLD ? "bold" : "normal");
        append("\" textLength=\"");
        number(label.measuredAdvance());
        append("\" lengthAdjust=\"spacingAndGlyphs\" xml:space=\"preserve\">");
        xmlText(label.text());
        append("</text>\n");
    }

    private void markerPath(VectorPath path, MarkerTransform transform, boolean closedOnly) {
        int ordinate = 0;
        int command = 0;
        boolean firstOutput = true;
        while (command < path.commandCount()) {
            int startCommand = command;
            int startOrdinate = ordinate;
            boolean closed = false;
            do {
                VectorPathCommand current = path.commandAt(command++);
                ordinate += current.arity();
                if (current == VectorPathCommand.CLOSE) {
                    closed = true;
                }
            } while (command < path.commandCount()
                    && path.commandAt(command) != VectorPathCommand.MOVE_TO);
            if (!closedOnly || closed) {
                if (!firstOutput) {
                    append(" ");
                }
                writeMarkerSubpath(path, startCommand, command, startOrdinate, transform);
                firstOutput = false;
            }
        }
    }

    private void writeMarkerSubpath(
            VectorPath path,
            int startCommand,
            int endCommand,
            int startOrdinate,
            MarkerTransform transform) {
        int ordinate = startOrdinate;
        boolean first = true;
        for (int index = startCommand; index < endCommand; index++) {
            VectorPathCommand command = path.commandAt(index);
            pathCommand();
            if (!first) {
                append(" ");
            }
            append(
                    switch (command) {
                        case MOVE_TO -> "M";
                        case LINE_TO -> "L";
                        case QUADRATIC_TO -> "Q";
                        case CUBIC_TO -> "C";
                        case CLOSE -> "Z";
                    });
            for (int offset = 0; offset < command.arity(); offset += 2) {
                append(" ");
                transformedNumber(
                        path.ordinateAt(ordinate + offset),
                        path.ordinateAt(ordinate + offset + 1),
                        transform);
            }
            ordinate += command.arity();
            first = false;
        }
    }

    private void transformedNumber(double x, double y, MarkerTransform transform) {
        double screenX = transform.m00() * x + transform.m01() * y + transform.m02();
        double screenY = transform.m10() * x + transform.m11() * y + transform.m12();
        number(screenX);
        append(" ");
        number(screenY);
    }

    private void sequencePath(CoordinateSequence sequence, boolean close) {
        pathCommand();
        append("M ");
        coordinate(sequence, 0);
        int end = close ? sequence.size() - 1 : sequence.size();
        for (int index = 1; index < end; index++) {
            pathCommand();
            append(" L ");
            coordinate(sequence, index);
        }
        if (close) {
            pathCommand();
            append(" Z");
        }
    }

    private void ringPath(CoordinateSequence ring) {
        sequencePath(ring, true);
    }

    private void coordinate(CoordinateSequence sequence, int index) {
        number(sequence.x(index));
        append(" ");
        number(sequence.y(index));
    }

    private static boolean allCoincident(CoordinateSequence sequence) {
        double x = sequence.x(0);
        double y = sequence.y(0);
        for (int index = 1; index < sequence.size(); index++) {
            if (Double.compare(x, sequence.x(index)) != 0
                    || Double.compare(y, sequence.y(index)) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasClosedSubpath(VectorPath path) {
        for (int index = 0; index < path.commandCount(); index++) {
            if (path.commandAt(index) == VectorPathCommand.CLOSE) {
                return true;
            }
        }
        return false;
    }

    private void opacity(String name, double opacity) {
        if (opacity != 1.0) {
            append(" ");
            append(name);
            append("=\"");
            number(opacity);
            append("\"");
        }
    }

    private void color(Rgba color) {
        append("#");
        appendHex(color.red());
        appendHex(color.green());
        appendHex(color.blue());
    }

    private void appendHex(int value) {
        append(Character.toString(HEX[value >>> 4]));
        append(Character.toString(HEX[value & 0x0f]));
    }

    private void xmlText(String text) {
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            String replacement =
                    switch (value) {
                        case '&' -> "&amp;";
                        case '<' -> "&lt;";
                        case '>' -> "&gt;";
                        default -> null;
                    };
            if (replacement != null) {
                append(text.substring(start, index));
                append(replacement);
                start = index + 1;
            }
        }
        append(text.substring(start));
    }

    private void number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("SVG coordinate must be finite");
        }
        append(Double.toString(value == 0.0 ? 0.0 : value));
    }

    private void integer(int value) {
        append(Integer.toString(value));
    }

    private void element() {
        elementCount++;
        limit("elements", limits.maximumElements(), elementCount);
    }

    private void pathCommand() {
        pathCommandCount++;
        limit("pathCommands", limits.maximumPathCommands(), pathCommandCount);
    }

    private void append(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long requested = (long) output.size() + bytes.length;
        limit("outputBytes", limits.maximumOutputBytes(), requested);
        output.writeBytes(bytes);
    }

    private void checkOwnedBytes(int finalLength) {
        long requested = 64L + finalLength * 2L;
        limit("ownedBytes", limits.maximumOwnedBytes(), requested);
    }

    private void limit(String name, long maximum, long requested) {
        if (requested > maximum) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("scope", "writer");
            context.put("limit", name);
            context.put("maximum", Long.toString(maximum));
            context.put("requested", Long.toString(requested));
            throw new SvgExportException(
                    "An SVG export limit was exceeded",
                    new SvgExportProblem("SVG_EXPORT_LIMIT_EXCEEDED", context));
        }
    }

    private void checkCancelled() {
        if (cancellation.isCancellationRequested()) {
            throw new SvgExportException(
                    "SVG export was cancelled",
                    new SvgExportProblem("SVG_EXPORT_CANCELLED", Map.of()));
        }
    }

    private SvgExportException valueFailure(
            VectorExportSnapshot.Primitive primitive, String field, RuntimeException cause) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("field", field);
        context.put("reason", "nonFinite");
        context.put("layerIndex", Integer.toString(primitive.layerIndex()));
        context.put("featureIndex", Integer.toString(primitive.featureIndex()));
        context.put("symbolOrdinal", "0");
        return new SvgExportException(
                "An SVG export value could not be transformed",
                new SvgExportProblem("SVG_EXPORT_VALUE_INVALID", context),
                cause);
    }

    private SvgExportException frameFailure(RuntimeException cause) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("field", "viewFrame");
        context.put("reason", "range");
        return new SvgExportException(
                "The SVG export view frame is outside the supported range",
                new SvgExportProblem("SVG_EXPORT_VALUE_INVALID", context),
                cause);
    }

    private static MapScreenBasis createBasis(VectorExportSnapshot.ViewFrame frame) {
        double radians = StrictMath.toRadians(frame.mapXAxisScreenBearingDegrees());
        double cosine = StrictMath.cos(radians);
        double sine = StrictMath.sin(radians);
        double scale = frame.screenPixelsPerMapUnit();
        return MapScreenBasis.of(
                new Coordinate(scale * cosine, scale * sine),
                new Coordinate(scale * sine, -scale * cosine));
    }
}
