package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.LineEndpointBearings;
import io.github.mundanej.map.core.LineTangents;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.SymbolTransforms;
import java.util.LinkedHashMap;
import java.util.Map;

final class SvgExportWriter {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final VectorExportSnapshot snapshot;
    private final SvgExportLimits limits;
    private final CancellationToken cancellation;
    private final SvgByteSink output;
    private MapScreenBasis basis;
    private long elementCount;
    private long pathCommandCount;
    private long hatchCandidateCount;
    private int nextClipId = 1;
    private int currentSymbolOrdinal;

    SvgExportWriter(
            VectorExportSnapshot snapshot, SvgExportLimits limits, CancellationToken cancellation) {
        this.snapshot = snapshot;
        this.limits = limits;
        this.cancellation = cancellation;
        this.output =
                new SvgByteSink(
                        limits.maximumOutputBytes(), limits.maximumOwnedBytes(), 64, cancellation);
    }

    byte[] encode() {
        checkCancelled();
        output.begin();
        chargeOwned(64);
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
        for (VectorExportSnapshot.Primitive primitive : snapshot.primitives()) {
            checkCancelled();
            try {
                writeHatchDefinitions(
                        primitive.screenGeometry(), primitive.symbol(), new int[] {0});
            } catch (ArithmeticException | IllegalArgumentException | SymbolException exception) {
                throw valueFailure(primitive, "hatchLayout", exception);
            }
        }
        append("  </defs>\n");

        element();
        append("  <g clip-path=\"url(#v0)\">\n");
        writeBackground();
        nextClipId = 1;
        for (VectorExportSnapshot.Primitive primitive : snapshot.primitives()) {
            checkCancelled();
            try {
                writeGeometry(primitive.screenGeometry(), primitive.symbol(), 1.0, new int[] {0});
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
        return output.finish();
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

    private void writeGeometry(
            Geometry geometry, Symbol symbol, double inheritedOpacity, int[] nextOrdinal) {
        checkCancelled();
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            writeMarkerGeometry(geometry, symbol, inheritedOpacity, nextOrdinal);
        } else if (geometry instanceof LineStringGeometry
                || geometry instanceof MultiLineStringGeometry) {
            writeLineGeometry(geometry, symbol, inheritedOpacity, nextOrdinal);
        } else {
            writeFillGeometry(geometry, symbol, inheritedOpacity, nextOrdinal);
        }
    }

    private void writeMarkerGeometry(
            Geometry geometry, Symbol symbol, double inheritedOpacity, int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writeMarkerGeometry(
                        geometry, child, inheritedOpacity * composite.opacity(), nextOrdinal);
            }
            return;
        }
        currentSymbolOrdinal = ordinal;
        VectorMarkerSymbol marker = (VectorMarkerSymbol) symbol;
        if (geometry instanceof PointGeometry point) {
            writeMarker(point.coordinate(), marker, inheritedOpacity);
            return;
        }
        CoordinateSequence coordinates = ((MultiPointGeometry) geometry).coordinates();
        for (int index = 0; index < coordinates.size(); index++) {
            checkCancelled();
            writeMarker(coordinates.coordinate(index), marker, inheritedOpacity);
        }
    }

    private void writeLineGeometry(
            Geometry geometry, Symbol symbol, double inheritedOpacity, int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writeLineGeometry(
                        geometry, child, inheritedOpacity * composite.opacity(), nextOrdinal);
            }
            return;
        }
        SolidLineSymbol lineSymbol = (SolidLineSymbol) symbol;
        int startOrdinal = reserveTree(lineSymbol.startMarker().orElse(null), nextOrdinal);
        int endOrdinal = reserveTree(lineSymbol.endMarker().orElse(null), nextOrdinal);
        if (geometry instanceof LineStringGeometry line) {
            writeLine(
                    line.coordinates(),
                    0,
                    line.coordinates().size(),
                    0,
                    lineSymbol,
                    inheritedOpacity,
                    ordinal,
                    startOrdinal,
                    endOrdinal);
            return;
        }
        MultiLineStringGeometry lines = (MultiLineStringGeometry) geometry;
        for (int part = 0; part < lines.partCount(); part++) {
            checkCancelled();
            writeLine(
                    lines.coordinates(),
                    lines.partOffset(part),
                    lines.partOffset(part + 1),
                    part,
                    lineSymbol,
                    inheritedOpacity,
                    ordinal,
                    startOrdinal,
                    endOrdinal);
        }
    }

    private void writeFillGeometry(
            Geometry geometry, Symbol symbol, double inheritedOpacity, int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writeFillGeometry(
                        geometry, child, inheritedOpacity * composite.opacity(), nextOrdinal);
            }
            return;
        }
        Symbol outline =
                symbol instanceof SolidFillSymbol solid
                        ? solid.outline().orElse(null)
                        : ((HatchFillSymbol) symbol).outline().orElse(null);
        int outlineOrdinal = reserveTree(outline, nextOrdinal);
        if (geometry instanceof PolygonGeometry polygon) {
            writeFill(polygon, symbol, inheritedOpacity, ordinal, outlineOrdinal);
            return;
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
            checkCancelled();
            writeFill(polygons, polygon, symbol, inheritedOpacity, ordinal, outlineOrdinal);
        }
    }

    private void writeFill(
            PolygonGeometry polygon,
            Symbol symbol,
            double inheritedOpacity,
            int ordinal,
            int outlineOrdinal) {
        checkCancelled();
        currentSymbolOrdinal = ordinal;
        if (symbol instanceof SolidFillSymbol solid) {
            writePolygon(polygon, solid, inheritedOpacity, outlineOrdinal);
        } else {
            writeHatch(polygon, (HatchFillSymbol) symbol, inheritedOpacity, outlineOrdinal);
        }
    }

    private void writeFill(
            MultiPolygonGeometry polygons,
            int polygonIndex,
            Symbol symbol,
            double inheritedOpacity,
            int ordinal,
            int outlineOrdinal) {
        checkCancelled();
        currentSymbolOrdinal = ordinal;
        if (symbol instanceof SolidFillSymbol solid) {
            writePolygon(polygons, polygonIndex, solid, inheritedOpacity, outlineOrdinal);
        } else {
            writeHatch(
                    polygons,
                    polygonIndex,
                    (HatchFillSymbol) symbol,
                    inheritedOpacity,
                    outlineOrdinal);
        }
    }

    private void writeMarkerTreeAtBearing(
            Coordinate point,
            Symbol symbol,
            double inheritedOpacity,
            double screenBearing,
            int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writeMarkerTreeAtBearing(
                        point,
                        child,
                        inheritedOpacity * composite.opacity(),
                        screenBearing,
                        nextOrdinal);
            }
            return;
        }
        currentSymbolOrdinal = ordinal;
        writeMarkerAtBearing(point, (VectorMarkerSymbol) symbol, inheritedOpacity, screenBearing);
    }

    private void writePolygonOutline(
            PolygonGeometry polygon, Symbol outline, double inheritedOpacity, int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (outline instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writePolygonOutline(
                        polygon, child, inheritedOpacity * composite.opacity(), nextOrdinal);
            }
            return;
        }
        currentSymbolOrdinal = ordinal;
        SolidLineSymbol line = (SolidLineSymbol) outline;
        writeRingOutline(polygon.exterior(), line, inheritedOpacity);
        for (CoordinateSequence hole : polygon.holes()) {
            checkCancelled();
            writeRingOutline(hole, line, inheritedOpacity);
        }
    }

    private void writePolygonOutline(
            MultiPolygonGeometry polygons,
            int polygonIndex,
            Symbol outline,
            double inheritedOpacity,
            int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (outline instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writePolygonOutline(
                        polygons,
                        polygonIndex,
                        child,
                        inheritedOpacity * composite.opacity(),
                        nextOrdinal);
            }
            return;
        }
        currentSymbolOrdinal = ordinal;
        SolidLineSymbol line = (SolidLineSymbol) outline;
        int firstRing = polygons.polygonRingOffset(polygonIndex);
        int lastRing = polygons.polygonRingOffset(polygonIndex + 1);
        for (int ring = firstRing; ring < lastRing; ring++) {
            checkCancelled();
            writeRingOutline(
                    polygons.coordinates(),
                    polygons.ringOffset(ring),
                    polygons.ringOffset(ring + 1),
                    line,
                    inheritedOpacity);
        }
    }

    private void writeHatchDefinitions(Geometry geometry, Symbol symbol, int[] nextOrdinal) {
        checkCancelled();
        int ordinal = nextOrdinal[0]++;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                checkCancelled();
                writeHatchDefinitions(geometry, child, nextOrdinal);
            }
            return;
        }
        if (symbol instanceof SolidLineSymbol line) {
            line.startMarker()
                    .ifPresent(marker -> writeHatchDefinitions(geometry, marker, nextOrdinal));
            line.endMarker()
                    .ifPresent(marker -> writeHatchDefinitions(geometry, marker, nextOrdinal));
            return;
        }
        if (symbol instanceof SolidFillSymbol solid) {
            solid.outline()
                    .ifPresent(outline -> writeHatchDefinitions(geometry, outline, nextOrdinal));
            return;
        }
        if (!(symbol instanceof HatchFillSymbol hatchFillSymbol)) {
            return;
        }
        currentSymbolOrdinal = ordinal;
        long symbolCandidates = 0;
        if (geometry instanceof PolygonGeometry polygon) {
            writeHatchClipIfNeeded(polygon, hatchFillSymbol, 0);
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
                checkCancelled();
                long candidates = hatchCandidateCount(polygons, polygon, hatchFillSymbol);
                if (candidates == 0) {
                    continue;
                }
                symbolCandidates = add(symbolCandidates, candidates);
                hatchLimit("symbol", hatchFillSymbol.maxSegments(), symbolCandidates);
                reserveHatch(candidates);
                writeHatchClip(polygons, polygon, nextClipId++);
            }
        }
        hatchFillSymbol
                .outline()
                .ifPresent(outline -> writeHatchDefinitions(geometry, outline, nextOrdinal));
    }

    private void writeHatchClipIfNeeded(
            PolygonGeometry polygon, HatchFillSymbol symbol, long symbolCandidates) {
        long candidates = hatchCandidateCount(polygon, symbol);
        if (candidates == 0) {
            return;
        }
        long requested = add(symbolCandidates, candidates);
        hatchLimit("symbol", symbol.maxSegments(), requested);
        reserveHatch(candidates);
        writeHatchClip(polygon, nextClipId++);
    }

    private long hatchCandidateCount(PolygonGeometry polygon, HatchFillSymbol symbol) {
        checkCancelled();
        Envelope bounds = hatchBounds(polygon);
        if (bounds == null) {
            return 0;
        }
        double spacing = SymbolTransforms.screenLength(symbol.spacing(), basis);
        return HatchLayouts.candidateSegmentCount(
                symbol.pattern(),
                bounds,
                hatchOrigin(symbol),
                hatchBearing(symbol),
                spacing,
                "vector-export");
    }

    private long hatchCandidateCount(
            MultiPolygonGeometry polygons, int polygonIndex, HatchFillSymbol symbol) {
        checkCancelled();
        Envelope bounds = hatchBounds(polygons, polygonIndex);
        if (bounds == null) {
            return 0;
        }
        double spacing = SymbolTransforms.screenLength(symbol.spacing(), basis);
        return HatchLayouts.candidateSegmentCount(
                symbol.pattern(),
                bounds,
                hatchOrigin(symbol),
                hatchBearing(symbol),
                spacing,
                "vector-export");
    }

    private void reserveHatch(long candidates) {
        hatchCandidateCount = add(hatchCandidateCount, candidates);
        hatchLimit("writer", limits.maximumHatchSegments(), hatchCandidateCount);
        chargeOwned(add(64, multiply(32, candidates)));
    }

    private void writeHatchClip(PolygonGeometry polygon, int clipId) {
        element();
        append("    <clipPath id=\"c");
        integer(clipId);
        append("\" clipPathUnits=\"userSpaceOnUse\">\n");
        element();
        append("      <path d=\"");
        polygonPath(polygon);
        append("\" clip-rule=\"evenodd\"/>\n");
        append("    </clipPath>\n");
    }

    private void writeHatchClip(MultiPolygonGeometry polygons, int polygonIndex, int clipId) {
        element();
        append("    <clipPath id=\"c");
        integer(clipId);
        append("\" clipPathUnits=\"userSpaceOnUse\">\n");
        element();
        append("      <path d=\"");
        polygonPath(polygons, polygonIndex);
        append("\" clip-rule=\"evenodd\"/>\n");
        append("    </clipPath>\n");
    }

    private void writeHatch(
            PolygonGeometry polygon,
            HatchFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        checkCancelled();
        Envelope bounds = hatchBounds(polygon);
        writeHatchPath(bounds, symbol, inheritedOpacity);
        writeHatchOutline(polygon, symbol, inheritedOpacity, outlineOrdinal);
    }

    private void writeHatch(
            MultiPolygonGeometry polygons,
            int polygonIndex,
            HatchFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        checkCancelled();
        Envelope bounds = hatchBounds(polygons, polygonIndex);
        writeHatchPath(bounds, symbol, inheritedOpacity);
        writeHatchOutline(polygons, polygonIndex, symbol, inheritedOpacity, outlineOrdinal);
    }

    private void writeHatchPath(Envelope bounds, HatchFillSymbol symbol, double inheritedOpacity) {
        if (bounds == null) {
            return;
        }
        double spacing = SymbolTransforms.screenLength(symbol.spacing(), basis);
        double bearing = hatchBearing(symbol);
        Coordinate origin = hatchOrigin(symbol);
        long candidates =
                HatchLayouts.candidateSegmentCount(
                        symbol.pattern(), bounds, origin, bearing, spacing, "vector-export");
        if (candidates == 0) {
            return;
        }
        int clipId = nextClipId++;
        checkCancelled();
        HatchSegments segments =
                HatchLayouts.cover(
                        symbol.pattern(),
                        bounds,
                        origin,
                        bearing,
                        spacing,
                        Math.toIntExact(candidates),
                        "vector-export");
        if (segments.segmentCount() == 0) {
            return;
        }
        element();
        SymbolStroke stroke = symbol.stroke();
        append("    <path d=\"");
        for (int index = 0; index < segments.segmentCount(); index++) {
            if (index > 0) {
                append(" ");
            }
            pathCommand();
            append("M ");
            number(segments.x1(index));
            append(" ");
            number(segments.y1(index));
            pathCommand();
            append(" L ");
            number(segments.x2(index));
            append(" ");
            number(segments.y2(index));
        }
        append("\" fill=\"none\" stroke=\"");
        color(stroke.color());
        append("\" stroke-width=\"");
        number(SymbolTransforms.screenLength(stroke.width(), basis));
        append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
        opacity(
                "stroke-opacity",
                inheritedOpacity * symbol.opacity() * stroke.color().alpha() / 255.0);
        append(" clip-path=\"url(#c");
        integer(clipId);
        append(")\"/>\n");
    }

    private void writeHatchOutline(
            PolygonGeometry polygon,
            HatchFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        symbol.outline()
                .ifPresent(
                        outline ->
                                writePolygonOutline(
                                        polygon,
                                        outline,
                                        inheritedOpacity * symbol.opacity(),
                                        new int[] {outlineOrdinal}));
    }

    private void writeHatchOutline(
            MultiPolygonGeometry polygons,
            int polygonIndex,
            HatchFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        symbol.outline()
                .ifPresent(
                        outline ->
                                writePolygonOutline(
                                        polygons,
                                        polygonIndex,
                                        outline,
                                        inheritedOpacity * symbol.opacity(),
                                        new int[] {outlineOrdinal}));
    }

    private double hatchBearing(HatchFillSymbol symbol) {
        return symbol.rotationMode() == SymbolRotationMode.SCREEN_RELATIVE
                ? 0.0
                : basis.xAxisScreenBearingDegrees();
    }

    private Coordinate hatchOrigin(HatchFillSymbol symbol) {
        return symbol.rotationMode() == SymbolRotationMode.SCREEN_RELATIVE
                ? new Coordinate(0.0, 0.0)
                : snapshot.viewFrame().mapOriginScreen();
    }

    private Envelope hatchBounds(PolygonGeometry polygon) {
        Envelope bounds = polygon.envelope();
        double minimumX = Math.max(0.0, bounds.minX());
        double minimumY = Math.max(0.0, bounds.minY());
        double maximumX = Math.min(snapshot.widthPixels(), bounds.maxX());
        double maximumY = Math.min(snapshot.heightPixels(), bounds.maxY());
        return minimumX > maximumX || minimumY > maximumY
                ? null
                : new Envelope(minimumX, minimumY, maximumX, maximumY);
    }

    private Envelope hatchBounds(MultiPolygonGeometry polygons, int polygonIndex) {
        int firstRing = polygons.polygonRingOffset(polygonIndex);
        int lastRing = polygons.polygonRingOffset(polygonIndex + 1);
        int start = polygons.ringOffset(firstRing);
        int end = polygons.ringOffset(lastRing);
        CoordinateSequence coordinates = polygons.coordinates();
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (int index = start; index < end; index++) {
            minimumX = Math.min(minimumX, coordinates.x(index));
            minimumY = Math.min(minimumY, coordinates.y(index));
            maximumX = Math.max(maximumX, coordinates.x(index));
            maximumY = Math.max(maximumY, coordinates.y(index));
        }
        minimumX = Math.max(0.0, minimumX);
        minimumY = Math.max(0.0, minimumY);
        maximumX = Math.min(snapshot.widthPixels(), maximumX);
        maximumY = Math.min(snapshot.heightPixels(), maximumY);
        return minimumX > maximumX || minimumY > maximumY
                ? null
                : new Envelope(minimumX, minimumY, maximumX, maximumY);
    }

    private void writeMarker(Coordinate point, VectorMarkerSymbol symbol, double inheritedOpacity) {
        chargeOwned(64);
        MarkerTransform transform =
                SymbolTransforms.marker(symbol.viewBox(), symbol.placement(), point, basis);
        writeMarker(symbol, transform, inheritedOpacity);
    }

    private void writeMarkerAtBearing(
            Coordinate point,
            VectorMarkerSymbol symbol,
            double inheritedOpacity,
            double screenBearing) {
        chargeOwned(64);
        MarkerTransform transform =
                SymbolTransforms.markerAtScreenBearing(
                        symbol.viewBox(), symbol.placement(), point, basis, screenBearing);
        writeMarker(symbol, transform, inheritedOpacity);
    }

    private void writeMarker(
            VectorMarkerSymbol symbol, MarkerTransform transform, double inheritedOpacity) {
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
            CoordinateSequence coordinates,
            int startInclusive,
            int endExclusive,
            int partIndex,
            SolidLineSymbol symbol,
            double inheritedOpacity,
            int lineOrdinal,
            int startOrdinal,
            int endOrdinal) {
        if (allCoincident(coordinates, startInclusive, endExclusive)) {
            return;
        }
        currentSymbolOrdinal = lineOrdinal;
        SymbolStroke stroke = symbol.stroke();
        element();
        append("    <path d=\"");
        sequencePath(coordinates, startInclusive, endExclusive, false);
        append("\" fill=\"none\" stroke=\"");
        color(stroke.color());
        append("\" stroke-width=\"");
        number(SymbolTransforms.screenLength(stroke.width(), basis));
        append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
        opacity(
                "stroke-opacity",
                inheritedOpacity * symbol.opacity() * stroke.color().alpha() / 255.0);
        append("/>\n");
        chargeOwned(64);
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(
                        coordinates, startInclusive, endExclusive, "vector-export", partIndex);
        if (symbol.startMarker().isPresent() && bearings.startBearingDegrees().isPresent()) {
            writeMarkerTreeAtBearing(
                    coordinates.coordinate(startInclusive),
                    symbol.startMarker().orElseThrow(),
                    inheritedOpacity * symbol.opacity(),
                    bearings.startBearingDegrees().getAsDouble(),
                    new int[] {startOrdinal});
        }
        if (symbol.endMarker().isPresent() && bearings.endBearingDegrees().isPresent()) {
            writeMarkerTreeAtBearing(
                    coordinates.coordinate(endExclusive - 1),
                    symbol.endMarker().orElseThrow(),
                    inheritedOpacity * symbol.opacity(),
                    bearings.endBearingDegrees().getAsDouble(),
                    new int[] {endOrdinal});
        }
    }

    private void writePolygon(
            PolygonGeometry polygon,
            SolidFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        element();
        append("    <path d=\"");
        ringPath(polygon.exterior());
        for (CoordinateSequence hole : polygon.holes()) {
            checkCancelled();
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
            writePolygonOutline(
                    polygon,
                    symbol.outline().orElseThrow(),
                    fillOpacity,
                    new int[] {outlineOrdinal});
        }
    }

    private void writePolygon(
            MultiPolygonGeometry polygons,
            int polygonIndex,
            SolidFillSymbol symbol,
            double inheritedOpacity,
            int outlineOrdinal) {
        element();
        append("    <path d=\"");
        polygonPath(polygons, polygonIndex);
        append("\" fill=\"");
        color(symbol.fill());
        append("\" fill-rule=\"evenodd\"");
        double fillOpacity = inheritedOpacity * symbol.opacity();
        opacity("fill-opacity", fillOpacity * symbol.fill().alpha() / 255.0);
        append("/>\n");
        if (symbol.outline().isPresent()) {
            writePolygonOutline(
                    polygons,
                    polygonIndex,
                    symbol.outline().orElseThrow(),
                    fillOpacity,
                    new int[] {outlineOrdinal});
        }
    }

    private void writeRingOutline(
            CoordinateSequence ring, SolidLineSymbol outline, double inheritedOpacity) {
        writeRingOutline(ring, 0, ring.size(), outline, inheritedOpacity);
    }

    private void writeRingOutline(
            CoordinateSequence coordinates,
            int startInclusive,
            int endExclusive,
            SolidLineSymbol outline,
            double inheritedOpacity) {
        SymbolStroke stroke = outline.stroke();
        element();
        append("    <path d=\"");
        sequencePath(coordinates, startInclusive, endExclusive, true);
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
        sequencePath(sequence, 0, sequence.size(), close);
    }

    private void sequencePath(
            CoordinateSequence sequence, int startInclusive, int endExclusive, boolean close) {
        pathCommand();
        append("M ");
        coordinate(sequence, startInclusive);
        int end = close ? endExclusive - 1 : endExclusive;
        for (int index = startInclusive + 1; index < end; index++) {
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

    private void polygonPath(PolygonGeometry polygon) {
        ringPath(polygon.exterior());
        for (CoordinateSequence hole : polygon.holes()) {
            checkCancelled();
            append(" ");
            ringPath(hole);
        }
    }

    private void polygonPath(MultiPolygonGeometry polygons, int polygonIndex) {
        int firstRing = polygons.polygonRingOffset(polygonIndex);
        int lastRing = polygons.polygonRingOffset(polygonIndex + 1);
        for (int ring = firstRing; ring < lastRing; ring++) {
            checkCancelled();
            if (ring > firstRing) {
                append(" ");
            }
            sequencePath(
                    polygons.coordinates(),
                    polygons.ringOffset(ring),
                    polygons.ringOffset(ring + 1),
                    true);
        }
    }

    private void coordinate(CoordinateSequence sequence, int index) {
        number(sequence.x(index));
        append(" ");
        number(sequence.y(index));
    }

    private static boolean allCoincident(
            CoordinateSequence sequence, int startInclusive, int endExclusive) {
        double x = sequence.x(startInclusive);
        double y = sequence.y(startInclusive);
        for (int index = startInclusive + 1; index < endExclusive; index++) {
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
        output.appendAscii(HEX[value >>> 4]);
        output.appendAscii(HEX[value & 0x0f]);
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
                output.append(text, start, index);
                append(replacement);
                start = index + 1;
            }
        }
        output.append(text, start, text.length());
    }

    private void number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("SVG coordinate must be finite");
        }
        chargeOwned(64);
        String token = Double.toString(value == 0.0 ? 0.0 : value);
        if (token.length() > 32) {
            throw new IllegalArgumentException(
                    "SVG coordinate token is outside the supported range");
        }
        append(token);
    }

    private void integer(int value) {
        if (value == 0) {
            output.appendAscii('0');
            return;
        }
        int divisor = 1;
        while (value / divisor >= 10) {
            divisor *= 10;
        }
        int remaining = value;
        while (divisor > 0) {
            output.appendAscii((char) ('0' + remaining / divisor));
            remaining %= divisor;
            divisor /= 10;
        }
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
        output.append(text);
    }

    private void chargeOwned(long bytes) {
        output.charge(bytes);
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

    private void hatchLimit(String scope, long maximum, long requested) {
        if (requested > maximum) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("scope", scope);
            context.put("limit", "hatchSegments");
            context.put("maximum", Long.toString(maximum));
            context.put("requested", Long.toString(requested));
            throw new SvgExportException(
                    "An SVG hatch limit was exceeded",
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
        context.put("symbolOrdinal", Integer.toString(currentSymbolOrdinal));
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

    private static int reserveTree(Symbol symbol, int[] nextOrdinal) {
        if (symbol == null) {
            return -1;
        }
        int first = nextOrdinal[0];
        nextOrdinal[0] = Math.addExact(nextOrdinal[0], symbolNodeCount(symbol));
        return first;
    }

    private static int symbolNodeCount(Symbol symbol) {
        int count = 1;
        if (symbol instanceof CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                count = Math.addExact(count, symbolNodeCount(child));
            }
        } else if (symbol instanceof SolidLineSymbol line) {
            if (line.startMarker().isPresent()) {
                count = Math.addExact(count, symbolNodeCount(line.startMarker().orElseThrow()));
            }
            if (line.endMarker().isPresent()) {
                count = Math.addExact(count, symbolNodeCount(line.endMarker().orElseThrow()));
            }
        } else if (symbol instanceof SolidFillSymbol fill && fill.outline().isPresent()) {
            count = Math.addExact(count, symbolNodeCount(fill.outline().orElseThrow()));
        } else if (symbol instanceof HatchFillSymbol fill && fill.outline().isPresent()) {
            count = Math.addExact(count, symbolNodeCount(fill.outline().orElseThrow()));
        }
        return count;
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long multiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
