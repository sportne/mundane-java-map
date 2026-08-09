package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapHitResults;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.LineEndpointBearings;
import io.github.mundanej.map.core.LineTangents;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.ScreenGeometryHits;
import io.github.mundanej.map.core.SymbolTransforms;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** Immutable-scene, toolkit-neutral hit predicates for the closed browser symbol profile. */
final class BrowserSceneHits {
    private static final double CURVE_FLATNESS_PIXELS = 0.125;
    private static final int MAX_CURVE_DEPTH = 16;
    private static final int MAX_FLATTENED_POINTS = 2_000_000;
    private static final int MAX_RASTER_SAMPLES = 2_000_000;
    private static final int MAX_HATCH_SEGMENTS = 200_000;

    private BrowserSceneHits() {}

    static MapHitResults hitTest(
            List<? extends Layer> layers,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance) {
        if (!Double.isFinite(queryX)
                || !Double.isFinite(queryY)
                || !Double.isFinite(tolerance)
                || tolerance < 0.0) {
            throw new IllegalArgumentException("Hit coordinates and tolerance must be finite");
        }
        if (queryX < 0.0
                || queryX >= viewport.width()
                || queryY < 0.0
                || queryY >= viewport.height()) {
            return MapHitResults.of(List.of());
        }
        double capped = Math.min(tolerance, Math.hypot(viewport.width(), viewport.height()));
        List<MapHit> hits = new ArrayList<>();
        Set<MapHit> retained = new HashSet<>();
        HitBudget budget = new HitBudget();
        for (int layerIndex = layers.size() - 1; layerIndex >= 0; layerIndex--) {
            Layer layer = layers.get(layerIndex);
            List<Feature> features = layer.features();
            for (int featureIndex = features.size() - 1; featureIndex >= 0; featureIndex--) {
                Feature feature = features.get(featureIndex);
                if (hitFeature(feature, viewport, queryX, queryY, capped, budget)) {
                    MapHit hit = new MapHit(layer.id(), feature.id());
                    if (retained.add(hit)) {
                        hits.add(hit);
                    }
                }
            }
        }
        return MapHitResults.of(hits);
    }

    private static boolean hitFeature(
            Feature feature,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance,
            HitBudget budget) {
        Symbol symbol = feature.symbol();
        if (feature.geometry() instanceof PointGeometry point) {
            return hitMarker(
                    symbol,
                    viewport.worldToScreen(point.coordinate()),
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    1.0,
                    OptionalDouble.empty(),
                    budget);
        }
        if (feature.geometry() instanceof MultiPointGeometry points) {
            for (int index = points.coordinates().size() - 1; index >= 0; index--) {
                if (hitMarker(
                        symbol,
                        viewport.worldToScreen(points.coordinates().coordinate(index)),
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        1.0,
                        OptionalDouble.empty(),
                        budget)) {
                    return true;
                }
            }
            return false;
        }
        if (feature.geometry() instanceof LineStringGeometry line) {
            return hitLine(
                    symbol,
                    line.coordinates(),
                    feature.id(),
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    1.0,
                    false,
                    0,
                    budget);
        }
        if (feature.geometry() instanceof MultiLineStringGeometry lines) {
            for (int part = lines.partCount() - 1; part >= 0; part--) {
                if (hitLine(
                        symbol,
                        slice(
                                lines.coordinates(),
                                lines.partOffset(part),
                                lines.partOffset(part + 1)),
                        feature.id(),
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        1.0,
                        false,
                        part,
                        budget)) {
                    return true;
                }
            }
            return false;
        }
        if (feature.geometry() instanceof PolygonGeometry polygon) {
            return hitFill(
                    symbol,
                    rings(polygon),
                    feature.id(),
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    1.0,
                    budget);
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) feature.geometry();
        for (int polygon = polygons.polygonCount() - 1; polygon >= 0; polygon--) {
            List<CoordinateSequence> rings = new ArrayList<>();
            for (int ring = polygons.polygonRingOffset(polygon + 1) - 1;
                    ring >= polygons.polygonRingOffset(polygon);
                    ring--) {
                rings.add(
                        0,
                        slice(
                                polygons.coordinates(),
                                polygons.ringOffset(ring),
                                polygons.ringOffset(ring + 1)));
            }
            if (hitFill(
                    symbol,
                    rings,
                    feature.id(),
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    1.0,
                    budget)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitMarker(
            Symbol symbol,
            Coordinate anchor,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance,
            double inheritedOpacity,
            OptionalDouble bearing,
            HitBudget budget) {
        if (symbol instanceof CompositeSymbol composite) {
            double opacity = inheritedOpacity * composite.opacity();
            if (opacity == 0.0) {
                return false;
            }
            List<Symbol> children = composite.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                if (hitMarker(
                        children.get(index),
                        anchor,
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        opacity,
                        bearing,
                        budget)) {
                    return true;
                }
            }
            return false;
        }
        MapScreenBasis basis = basis(viewport);
        if (symbol instanceof VectorMarkerSymbol marker) {
            if (inheritedOpacity * marker.opacity() == 0.0) {
                return false;
            }
            MarkerTransform transform =
                    bearing.isPresent()
                            ? SymbolTransforms.markerAtScreenBearing(
                                    marker.viewBox(),
                                    marker.placement(),
                                    anchor,
                                    basis,
                                    bearing.getAsDouble())
                            : SymbolTransforms.marker(
                                    marker.viewBox(), marker.placement(), anchor, basis);
            List<PathPart> parts = flatten(marker.path(), transform, budget);
            if (marker.stroke().isPresent() && marker.stroke().orElseThrow().color().alpha() > 0) {
                double radius =
                        SymbolTransforms.screenLength(marker.stroke().orElseThrow().width(), basis)
                                        / 2.0
                                + tolerance;
                for (PathPart part : parts) {
                    if (pathBoundaryWithin(part, queryX, queryY, radius)) {
                        return true;
                    }
                }
            }
            if (marker.fill().alpha() == 0) {
                return false;
            }
            return evenOddPathHit(parts, queryX, queryY, tolerance);
        }
        RasterIconSymbol icon = (RasterIconSymbol) symbol;
        if (inheritedOpacity * icon.opacity() == 0.0) {
            return false;
        }
        Envelope viewBox = new Envelope(0, 0, icon.width(), icon.height());
        MarkerTransform transform =
                bearing.isPresent()
                        ? SymbolTransforms.markerAtScreenBearing(
                                viewBox, icon.placement(), anchor, basis, bearing.getAsDouble())
                        : SymbolTransforms.marker(viewBox, icon.placement(), anchor, basis);
        double expansion = icon.interpolation() == RasterInterpolation.BILINEAR ? 0.5 : 0.0;
        for (int pixel : budget.rasterSupport(icon).opaquePixels()) {
            budget.takeRasterTest();
            int x = pixel % icon.width();
            int y = pixel / icon.width();
            double minX = Math.max(0.0, x - expansion);
            double minY = Math.max(0.0, y - expansion);
            double maxX = Math.min(icon.width(), x + 1.0 + expansion);
            double maxY = Math.min(icon.height(), y + 1.0 + expansion);
            if (ScreenGeometryHits.convexQuadWithin(
                    quad(transform, minX, minY, maxX, maxY), queryX, queryY, tolerance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitLine(
            Symbol symbol,
            CoordinateSequence source,
            String featureId,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance,
            double inheritedOpacity,
            boolean closed,
            int partIndex,
            HitBudget budget) {
        if (symbol instanceof CompositeSymbol composite) {
            double opacity = inheritedOpacity * composite.opacity();
            List<Symbol> children = composite.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                if (hitLine(
                        children.get(index),
                        source,
                        featureId,
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        opacity,
                        closed,
                        partIndex,
                        budget)) {
                    return true;
                }
            }
            return false;
        }
        SolidLineSymbol line = (SolidLineSymbol) symbol;
        double opacity = inheritedOpacity * line.opacity();
        CoordinateSequence screen = screen(source, viewport);
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(screen, featureId, partIndex);
        if (bearings.startBearingDegrees().isEmpty() && bearings.endBearingDegrees().isEmpty()) {
            return false;
        }
        if (!closed
                && line.endMarker().isPresent()
                && bearings.endBearingDegrees().isPresent()
                && hitMarker(
                        line.endMarker().orElseThrow(),
                        screen.coordinate(screen.size() - 1),
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        opacity,
                        bearings.endBearingDegrees(),
                        budget)) {
            return true;
        }
        if (!closed
                && line.startMarker().isPresent()
                && bearings.startBearingDegrees().isPresent()
                && hitMarker(
                        line.startMarker().orElseThrow(),
                        screen.coordinate(0),
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        opacity,
                        bearings.startBearingDegrees(),
                        budget)) {
            return true;
        }
        return opacity > 0.0
                && line.stroke().color().alpha() > 0
                && ScreenGeometryHits.polylineWithin(
                        screen,
                        closed,
                        queryX,
                        queryY,
                        SymbolTransforms.screenLength(line.stroke().width(), basis(viewport)) / 2.0
                                + tolerance);
    }

    private static boolean hitFill(
            Symbol symbol,
            List<CoordinateSequence> sourceRings,
            String featureId,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance,
            double inheritedOpacity,
            HitBudget budget) {
        if (symbol instanceof CompositeSymbol composite) {
            double opacity = inheritedOpacity * composite.opacity();
            List<Symbol> children = composite.children();
            for (int index = children.size() - 1; index >= 0; index--) {
                if (hitFill(
                        children.get(index),
                        sourceRings,
                        featureId,
                        viewport,
                        queryX,
                        queryY,
                        tolerance,
                        opacity,
                        budget)) {
                    return true;
                }
            }
            return false;
        }
        List<CoordinateSequence> screenRings =
                sourceRings.stream().map(ring -> screen(ring, viewport)).toList();
        if (symbol instanceof SolidFillSymbol fill) {
            double opacity = inheritedOpacity * fill.opacity();
            if (hitOutline(
                    fill.outline(),
                    sourceRings,
                    featureId,
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    opacity,
                    budget)) {
                return true;
            }
            return opacity > 0.0
                    && fill.fill().alpha() > 0
                    && ScreenGeometryHits.filledPolygonWithin(
                            screenRings.getFirst(),
                            screenRings.subList(1, screenRings.size()),
                            queryX,
                            queryY,
                            tolerance);
        }
        HatchFillSymbol hatch = (HatchFillSymbol) symbol;
        double opacity = inheritedOpacity * hatch.opacity();
        if (hitOutline(
                hatch.outline(),
                sourceRings,
                featureId,
                viewport,
                queryX,
                queryY,
                tolerance,
                opacity,
                budget)) {
            return true;
        }
        if (opacity == 0.0 || hatch.stroke().color().alpha() == 0) {
            return false;
        }
        Envelope polygonBounds = bounds(screenRings);
        double minimumX = Math.max(0.0, polygonBounds.minX());
        double minimumY = Math.max(0.0, polygonBounds.minY());
        double maximumX = Math.min(viewport.width(), polygonBounds.maxX());
        double maximumY = Math.min(viewport.height(), polygonBounds.maxY());
        if (minimumX >= maximumX || minimumY >= maximumY) {
            return false;
        }
        Envelope bounds = new Envelope(minimumX, minimumY, maximumX, maximumY);
        if (bounds.width() == 0.0 || bounds.height() == 0.0) {
            return false;
        }
        boolean mapRelative = hatch.rotationMode() == SymbolRotationMode.MAP_RELATIVE;
        Coordinate origin =
                mapRelative ? viewport.worldToScreen(new Coordinate(0, 0)) : new Coordinate(0, 0);
        boolean polygonHit =
                ScreenGeometryHits.filledPolygonWithin(
                        screenRings.getFirst(),
                        screenRings.subList(1, screenRings.size()),
                        queryX,
                        queryY,
                        tolerance);
        if (!polygonHit) {
            return false;
        }
        int segmentLimit = Math.min(hatch.maxSegments(), budget.remainingHatchSegments());
        if (segmentLimit == 0) {
            throw budget.limit("hatchSegments");
        }
        HatchSegments segments =
                HatchLayouts.cover(
                        hatch.pattern(),
                        bounds,
                        origin,
                        mapRelative ? basis(viewport).xAxisScreenBearingDegrees() : 0.0,
                        SymbolTransforms.screenLength(hatch.spacing(), basis(viewport)),
                        segmentLimit,
                        featureId);
        budget.takeHatchSegments(segments.segmentCount());
        double radius =
                SymbolTransforms.screenLength(hatch.stroke().width(), basis(viewport)) / 2.0
                        + tolerance;
        for (int index = 0; index < segments.segmentCount(); index++) {
            double x1 = segments.x1(index);
            double y1 = segments.y1(index);
            double x2 = segments.x2(index);
            double y2 = segments.y2(index);
            if (ScreenGeometryHits.polylineWithin(
                            CoordinateSequence.of(x1, y1, x2, y2), false, queryX, queryY, radius)
                    && clippedSegmentWithin(
                            screenRings, x1, y1, x2, y2, queryX, queryY, radius, budget)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitOutline(
            Optional<? extends Symbol> outline,
            List<CoordinateSequence> rings,
            String featureId,
            MapViewport viewport,
            double queryX,
            double queryY,
            double tolerance,
            double opacity,
            HitBudget budget) {
        if (outline.isEmpty()) {
            return false;
        }
        for (int index = rings.size() - 1; index >= 0; index--) {
            if (hitLine(
                    outline.orElseThrow(),
                    rings.get(index),
                    featureId,
                    viewport,
                    queryX,
                    queryY,
                    tolerance,
                    opacity,
                    true,
                    index,
                    budget)) {
                return true;
            }
        }
        return false;
    }

    private static List<PathPart> flatten(
            VectorPath path, MarkerTransform transform, HitBudget budget) {
        List<PathPart> result = new ArrayList<>();
        List<Coordinate> current = null;
        List<Boolean> currentApproximatedSegments = null;
        Coordinate cursor = null;
        int ordinate = 0;
        for (int commandIndex = 0; commandIndex < path.commandCount(); commandIndex++) {
            VectorPathCommand command = path.commandAt(commandIndex);
            switch (command) {
                case MOVE_TO -> {
                    if (current != null) {
                        result.add(new PathPart(current, false, currentApproximatedSegments));
                    }
                    current = new ArrayList<>();
                    currentApproximatedSegments = new ArrayList<>();
                    cursor =
                            new Coordinate(
                                    path.ordinateAt(ordinate), path.ordinateAt(ordinate + 1));
                    budget.take();
                    current.add(transform(transform, cursor));
                }
                case LINE_TO -> {
                    cursor =
                            new Coordinate(
                                    path.ordinateAt(ordinate), path.ordinateAt(ordinate + 1));
                    budget.take();
                    current.add(transform(transform, cursor));
                    currentApproximatedSegments.add(false);
                }
                case QUADRATIC_TO -> {
                    Coordinate control =
                            new Coordinate(
                                    path.ordinateAt(ordinate), path.ordinateAt(ordinate + 1));
                    Coordinate end =
                            new Coordinate(
                                    path.ordinateAt(ordinate + 2), path.ordinateAt(ordinate + 3));
                    int firstNewPoint = current.size();
                    flattenQuadratic(current, transform, cursor, control, end, budget);
                    addApproximationFlags(
                            currentApproximatedSegments, current.size() - firstNewPoint);
                    cursor = end;
                }
                case CUBIC_TO -> {
                    Coordinate first =
                            new Coordinate(
                                    path.ordinateAt(ordinate), path.ordinateAt(ordinate + 1));
                    Coordinate second =
                            new Coordinate(
                                    path.ordinateAt(ordinate + 2), path.ordinateAt(ordinate + 3));
                    Coordinate end =
                            new Coordinate(
                                    path.ordinateAt(ordinate + 4), path.ordinateAt(ordinate + 5));
                    int firstNewPoint = current.size();
                    flattenCubic(current, transform, cursor, first, second, end, budget);
                    addApproximationFlags(
                            currentApproximatedSegments, current.size() - firstNewPoint);
                    cursor = end;
                }
                case CLOSE -> {
                    currentApproximatedSegments.add(false);
                    result.add(new PathPart(current, true, currentApproximatedSegments));
                    current = null;
                    currentApproximatedSegments = null;
                }
            }
            ordinate += command.arity();
        }
        if (current != null) {
            result.add(new PathPart(current, false, currentApproximatedSegments));
        }
        return List.copyOf(result);
    }

    private static void flattenQuadratic(
            List<Coordinate> target,
            MarkerTransform transform,
            Coordinate start,
            Coordinate control,
            Coordinate end,
            HitBudget budget) {
        flattenQuadraticScreen(
                target,
                transform(transform, start),
                transform(transform, control),
                transform(transform, end),
                0,
                budget);
    }

    private static void flattenCubic(
            List<Coordinate> target,
            MarkerTransform transform,
            Coordinate start,
            Coordinate first,
            Coordinate second,
            Coordinate end,
            HitBudget budget) {
        flattenCubicScreen(
                target,
                transform(transform, start),
                transform(transform, first),
                transform(transform, second),
                transform(transform, end),
                0,
                budget);
    }

    private static void flattenQuadraticScreen(
            List<Coordinate> target,
            Coordinate start,
            Coordinate control,
            Coordinate end,
            int depth,
            HitBudget budget) {
        if (pointSegmentDistance(control, start, end) <= CURVE_FLATNESS_PIXELS) {
            budget.take();
            target.add(end);
            return;
        }
        if (depth >= MAX_CURVE_DEPTH) {
            throw budget.limit("curveFlatness");
        }
        Coordinate first = midpoint(start, control);
        Coordinate second = midpoint(control, end);
        Coordinate middle = midpoint(first, second);
        flattenQuadraticScreen(target, start, first, middle, depth + 1, budget);
        flattenQuadraticScreen(target, middle, second, end, depth + 1, budget);
    }

    private static void flattenCubicScreen(
            List<Coordinate> target,
            Coordinate start,
            Coordinate first,
            Coordinate second,
            Coordinate end,
            int depth,
            HitBudget budget) {
        double flatness =
                Math.max(
                        pointSegmentDistance(first, start, end),
                        pointSegmentDistance(second, start, end));
        if (flatness <= CURVE_FLATNESS_PIXELS) {
            budget.take();
            target.add(end);
            return;
        }
        if (depth >= MAX_CURVE_DEPTH) {
            throw budget.limit("curveFlatness");
        }
        Coordinate firstHalf = midpoint(start, first);
        Coordinate middleHalf = midpoint(first, second);
        Coordinate lastHalf = midpoint(second, end);
        Coordinate firstQuarter = midpoint(firstHalf, middleHalf);
        Coordinate lastQuarter = midpoint(middleHalf, lastHalf);
        Coordinate middle = midpoint(firstQuarter, lastQuarter);
        flattenCubicScreen(target, start, firstHalf, firstQuarter, middle, depth + 1, budget);
        flattenCubicScreen(target, middle, lastQuarter, lastHalf, end, depth + 1, budget);
    }

    private static Coordinate midpoint(Coordinate first, Coordinate second) {
        return new Coordinate(
                first.x() + (second.x() - first.x()) * 0.5,
                first.y() + (second.y() - first.y()) * 0.5);
    }

    private static double pointSegmentDistance(Coordinate point, Coordinate start, Coordinate end) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double length = Math.hypot(deltaX, deltaY);
        if (length == 0.0) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        double along =
                (point.x() - start.x()) * (deltaX / length)
                        + (point.y() - start.y()) * (deltaY / length);
        if (along <= 0.0) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        if (along >= length) {
            return Math.hypot(point.x() - end.x(), point.y() - end.y());
        }
        double closestX = start.x() + along * (deltaX / length);
        double closestY = start.y() + along * (deltaY / length);
        return Math.hypot(point.x() - closestX, point.y() - closestY);
    }

    private static boolean evenOddPathHit(
            List<PathPart> parts, double queryX, double queryY, double tolerance) {
        boolean inside = false;
        for (PathPart part : parts) {
            if (!part.closed() || part.points().size() < 3) {
                continue;
            }
            CoordinateSequence ring = sequence(part.points());
            if (pathBoundaryWithin(part, queryX, queryY, tolerance)) {
                return true;
            }
            if (ScreenGeometryHits.filledPolygonWithin(ring, List.of(), queryX, queryY, 0.0)) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static MapScreenBasis basis(MapViewport viewport) {
        double scale = 1.0 / viewport.worldUnitsPerPixel();
        return MapScreenBasis.of(new Coordinate(scale, 0), new Coordinate(0, -scale));
    }

    private static CoordinateSequence screen(CoordinateSequence source, MapViewport viewport) {
        double[] packed = new double[source.size() * 2];
        for (int index = 0; index < source.size(); index++) {
            Coordinate coordinate = viewport.worldToScreen(source.coordinate(index));
            packed[index * 2] = coordinate.x();
            packed[index * 2 + 1] = coordinate.y();
        }
        return CoordinateSequence.of(packed);
    }

    private static CoordinateSequence sequence(List<Coordinate> points) {
        double[] packed = new double[points.size() * 2];
        for (int index = 0; index < points.size(); index++) {
            packed[index * 2] = points.get(index).x();
            packed[index * 2 + 1] = points.get(index).y();
        }
        return CoordinateSequence.of(packed);
    }

    private static boolean pathBoundaryWithin(
            PathPart part, double queryX, double queryY, double tolerance) {
        for (int index = 0; index < part.approximatedSegments().size(); index++) {
            int next = index + 1 == part.points().size() ? 0 : index + 1;
            Coordinate start = part.points().get(index);
            Coordinate end = part.points().get(next);
            double edgeTolerance =
                    tolerance
                            + (part.approximatedSegments().get(index)
                                    ? CURVE_FLATNESS_PIXELS
                                    : 0.0);
            if (ScreenGeometryHits.polylineWithin(
                    CoordinateSequence.of(start.x(), start.y(), end.x(), end.y()),
                    false,
                    queryX,
                    queryY,
                    edgeTolerance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean clippedSegmentWithin(
            List<CoordinateSequence> rings,
            double x1,
            double y1,
            double x2,
            double y2,
            double queryX,
            double queryY,
            double radius,
            HitBudget budget) {
        Coordinate closest = closestPoint(x1, y1, x2, y2, queryX, queryY);
        boolean inside = false;
        boolean boundaryWithin = false;
        for (CoordinateSequence ring : rings) {
            for (int index = 0; index < ring.size(); index++) {
                budget.takeGeometryTest();
                int next = index + 1 == ring.size() ? 0 : index + 1;
                double boundaryX1 = ring.x(index);
                double boundaryY1 = ring.y(index);
                double boundaryX2 = ring.x(next);
                double boundaryY2 = ring.y(next);
                if ((boundaryY1 > closest.y()) != (boundaryY2 > closest.y())
                        && closest.x()
                                < (boundaryX2 - boundaryX1)
                                                * (closest.y() - boundaryY1)
                                                / (boundaryY2 - boundaryY1)
                                        + boundaryX1) {
                    inside = !inside;
                }
                if (!boundaryWithin
                        && intersectionWithin(
                                x1,
                                y1,
                                x2,
                                y2,
                                boundaryX1,
                                boundaryY1,
                                boundaryX2,
                                boundaryY2,
                                queryX,
                                queryY,
                                radius)) {
                    boundaryWithin = true;
                }
            }
        }
        return inside || boundaryWithin;
    }

    private static Coordinate closestPoint(
            double x1, double y1, double x2, double y2, double queryX, double queryY) {
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        double parameter =
                lengthSquared == 0.0
                        ? 0.0
                        : Math.max(
                                0.0,
                                Math.min(
                                        1.0,
                                        ((queryX - x1) * deltaX + (queryY - y1) * deltaY)
                                                / lengthSquared));
        return new Coordinate(x1 + parameter * deltaX, y1 + parameter * deltaY);
    }

    private static boolean intersectionWithin(
            double x1,
            double y1,
            double x2,
            double y2,
            double x3,
            double y3,
            double x4,
            double y4,
            double queryX,
            double queryY,
            double radius) {
        double firstX = x2 - x1;
        double firstY = y2 - y1;
        double secondX = x4 - x3;
        double secondY = y4 - y3;
        double denominator = firstX * secondY - firstY * secondX;
        if (denominator == 0.0) {
            double cross = (x3 - x1) * firstY - (y3 - y1) * firstX;
            if (cross != 0.0) {
                return false;
            }
            double thirdParameter;
            double fourthParameter;
            if (Math.abs(firstX) >= Math.abs(firstY)) {
                if (firstX == 0.0) {
                    return Math.hypot(x1 - queryX, y1 - queryY) <= radius;
                }
                thirdParameter = (x3 - x1) / firstX;
                fourthParameter = (x4 - x1) / firstX;
            } else {
                thirdParameter = (y3 - y1) / firstY;
                fourthParameter = (y4 - y1) / firstY;
            }
            double minimum = Math.max(0.0, Math.min(thirdParameter, fourthParameter));
            double maximum = Math.min(1.0, Math.max(thirdParameter, fourthParameter));
            return minimum <= maximum
                    && ScreenGeometryHits.polylineWithin(
                            CoordinateSequence.of(
                                    x1 + minimum * firstX,
                                    y1 + minimum * firstY,
                                    x1 + maximum * firstX,
                                    y1 + maximum * firstY),
                            false,
                            queryX,
                            queryY,
                            radius);
        }
        double offsetX = x3 - x1;
        double offsetY = y3 - y1;
        double firstParameter = (offsetX * secondY - offsetY * secondX) / denominator;
        double secondParameter = (offsetX * firstY - offsetY * firstX) / denominator;
        if (firstParameter < 0.0
                || firstParameter > 1.0
                || secondParameter < 0.0
                || secondParameter > 1.0) {
            return false;
        }
        return Math.hypot(
                        x1 + firstParameter * firstX - queryX,
                        y1 + firstParameter * firstY - queryY)
                <= radius;
    }

    private static void addApproximationFlags(List<Boolean> target, int count) {
        for (int index = 0; index < count; index++) {
            target.add(true);
        }
    }

    private static Coordinate transform(MarkerTransform transform, Coordinate point) {
        return new Coordinate(
                transform.m00() * point.x() + transform.m01() * point.y() + transform.m02(),
                transform.m10() * point.x() + transform.m11() * point.y() + transform.m12());
    }

    private static double[] quad(
            MarkerTransform transform, double minX, double minY, double maxX, double maxY) {
        double[] result = new double[8];
        Coordinate[] corners = {
            new Coordinate(minX, minY),
            new Coordinate(maxX, minY),
            new Coordinate(maxX, maxY),
            new Coordinate(minX, maxY)
        };
        for (int index = 0; index < corners.length; index++) {
            Coordinate transformed = transform(transform, corners[index]);
            result[index * 2] = transformed.x();
            result[index * 2 + 1] = transformed.y();
        }
        return result;
    }

    private static Envelope bounds(List<CoordinateSequence> rings) {
        Envelope result = null;
        for (CoordinateSequence ring : rings) {
            for (int index = 0; index < ring.size(); index++) {
                Envelope point =
                        new Envelope(ring.x(index), ring.y(index), ring.x(index), ring.y(index));
                result = result == null ? point : result.union(point);
            }
        }
        return result;
    }

    private static List<CoordinateSequence> rings(PolygonGeometry polygon) {
        List<CoordinateSequence> result = new ArrayList<>();
        result.add(polygon.exterior());
        result.addAll(polygon.holes());
        return List.copyOf(result);
    }

    private static CoordinateSequence slice(CoordinateSequence source, int start, int end) {
        double[] packed = new double[(end - start) * 2];
        for (int index = start; index < end; index++) {
            packed[(index - start) * 2] = source.x(index);
            packed[(index - start) * 2 + 1] = source.y(index);
        }
        return CoordinateSequence.of(packed);
    }

    private record PathPart(
            List<Coordinate> points, boolean closed, List<Boolean> approximatedSegments) {
        private PathPart {
            points = List.copyOf(points);
            approximatedSegments = List.copyOf(approximatedSegments);
        }
    }

    private static final class RasterSupport {
        private final int[] opaquePixels;

        private RasterSupport(int[] opaquePixels) {
            this.opaquePixels = opaquePixels;
        }

        private int[] opaquePixels() {
            return opaquePixels;
        }
    }

    private static final class HitBudget {
        private int remainingFlattenedPoints = MAX_FLATTENED_POINTS;
        private int remainingRasterSamples = MAX_RASTER_SAMPLES;
        private int remainingRasterTests = MAX_RASTER_SAMPLES;
        private int remainingHatchSegments = MAX_HATCH_SEGMENTS;
        private int remainingGeometryTests = MAX_FLATTENED_POINTS;
        private final IdentityHashMap<RasterIconSymbol, RasterSupport> rasterSupport =
                new IdentityHashMap<>();

        private void take() {
            if (remainingFlattenedPoints == 0) {
                throw limit("flattenedPoints");
            }
            remainingFlattenedPoints--;
        }

        private RasterSupport rasterSupport(RasterIconSymbol icon) {
            RasterSupport cached = rasterSupport.get(icon);
            if (cached != null) {
                return cached;
            }
            long pixelCount = (long) icon.width() * icon.height();
            int[] opaque = new int[(int) Math.min(pixelCount, remainingRasterSamples)];
            int count = 0;
            for (int pixel = 0; pixel < pixelCount; pixel++) {
                if (remainingRasterSamples == 0) {
                    throw limit("rasterSamples");
                }
                remainingRasterSamples--;
                int x = pixel % icon.width();
                int y = pixel / icon.width();
                if ((icon.rgbaAt(x, y) & 0xff) != 0) {
                    opaque[count++] = pixel;
                }
            }
            RasterSupport support = new RasterSupport(Arrays.copyOf(opaque, count));
            rasterSupport.put(icon, support);
            return support;
        }

        private int remainingHatchSegments() {
            return remainingHatchSegments;
        }

        private void takeRasterTest() {
            if (remainingRasterTests == 0) {
                throw limit("rasterTests");
            }
            remainingRasterTests--;
        }

        private void takeHatchSegments(int count) {
            remainingHatchSegments -= count;
        }

        private void takeGeometryTest() {
            if (remainingGeometryTests == 0) {
                throw limit("geometryTests");
            }
            remainingGeometryTests--;
        }

        private MundaneMapException limit(String name) {
            return new MundaneMapException(
                    MundaneMapException.LIMIT_EXCEEDED,
                    "Browser hit-test work budget exceeded",
                    Map.of("limit", name));
        }
    }
}
