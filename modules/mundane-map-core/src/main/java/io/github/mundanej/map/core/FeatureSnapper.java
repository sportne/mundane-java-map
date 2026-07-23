package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.FeatureEditProblem;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapQueryResult;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapResult;
import io.github.mundanej.map.api.SnapTargetType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Stateless bounded linear resolver for explicit same-CRS snap references. */
public final class FeatureSnapper {
    /** Resolves one query without retaining an index or reference snapshot. */
    public FeatureSnapper() {}

    /**
     * Finds the deterministic closest target within the inclusive tolerance.
     *
     * @param query complete immutable operation input
     * @return snapped, unsnapped, or stably rejected result
     */
    public SnapQueryResult find(SnapQuery query) {
        Objects.requireNonNull(query, "query");
        try {
            return new Resolver(query).find();
        } catch (Rejected rejected) {
            return SnapQueryResult.rejected(rejected.problem());
        }
    }

    private static final class Resolver {
        private final SnapQuery query;
        private Candidate best;
        private long layers;
        private long features;
        private long coordinates;
        private long segments;

        private Resolver(SnapQuery query) {
            this.query = query;
        }

        private SnapQueryResult find() {
            checkCancellation(Map.of());
            for (int layerIndex = 0;
                    layerIndex < query.references().layers().size();
                    layerIndex++) {
                SnapReferenceLayer layer = query.references().layers().get(layerIndex);
                Map<String, String> layerLocation =
                        Map.of("layerIndex", Integer.toString(layerIndex));
                checkCancellation(layerLocation);
                layers = increment(layers, query.limits().maximumLayers(), layerLocation);
                for (int featureIndex = 0; featureIndex < layer.features().size(); featureIndex++) {
                    SnapFeature feature = layer.features().get(featureIndex);
                    Location location = new Location(layerIndex, featureIndex, 0, 0, 0);
                    checkCancellation(location.context(false));
                    if (query.exclusions()
                            .contains(new FeatureSelection(layer.layerId(), feature.featureId()))) {
                        continue;
                    }
                    features =
                            increment(
                                    features,
                                    query.limits().maximumFeatures(),
                                    location.context(false));
                    visitGeometry(layer, feature, location);
                }
            }
            checkCancellation(Map.of());
            return best == null
                    ? SnapQueryResult.unsnapped()
                    : SnapQueryResult.snapped(best.result());
        }

        private void visitGeometry(SnapReferenceLayer layer, SnapFeature feature, Location base) {
            Geometry geometry = feature.geometry();
            switch (geometry) {
                case PointGeometry point -> visitPoint(layer, feature, base, point.coordinate());
                case MultiPointGeometry points -> {
                    for (int component = 0; component < points.coordinates().size(); component++) {
                        visitPoint(
                                layer,
                                feature,
                                base.withGeometry(component, 0, 0),
                                points.coordinates().coordinate(component));
                    }
                }
                case LineStringGeometry line ->
                        visitSequence(layer, feature, base, line.coordinates(), false);
                case MultiLineStringGeometry lines -> {
                    for (int component = 0; component < lines.partCount(); component++) {
                        visitPackedSequence(
                                layer,
                                feature,
                                base.withGeometry(component, 0, 0),
                                lines.coordinates(),
                                lines.partOffset(component),
                                lines.partOffset(component + 1),
                                false);
                    }
                }
                case PolygonGeometry polygon -> {
                    visitSequence(layer, feature, base, polygon.exterior(), true);
                    for (int ring = 0; ring < polygon.holes().size(); ring++) {
                        visitSequence(
                                layer,
                                feature,
                                base.withGeometry(0, ring + 1, 0),
                                polygon.holes().get(ring),
                                true);
                    }
                }
                case MultiPolygonGeometry polygons -> {
                    for (int component = 0; component < polygons.polygonCount(); component++) {
                        int firstRing = polygons.polygonRingOffset(component);
                        int endRing = polygons.polygonRingOffset(component + 1);
                        for (int ring = firstRing; ring < endRing; ring++) {
                            visitPackedSequence(
                                    layer,
                                    feature,
                                    base.withGeometry(component, ring - firstRing, 0),
                                    polygons.coordinates(),
                                    polygons.ringOffset(ring),
                                    polygons.ringOffset(ring + 1),
                                    true);
                        }
                    }
                }
            }
        }

        private void visitPoint(
                SnapReferenceLayer layer,
                SnapFeature feature,
                Location location,
                Coordinate coordinate) {
            checkCancellation(location.context(true));
            coordinates =
                    increment(
                            coordinates,
                            query.limits().maximumCoordinates(),
                            location.context(true));
            boolean repeating = query.repeatsLayer(layer.layerId());
            double referenceX =
                    query.viewport().screenToWorld(query.screenX(), query.screenY()).x();
            Coordinate screen =
                    toScreen(coordinate, location, repeating, referenceX, null).screen();
            considerVertex(
                    layer,
                    feature,
                    location,
                    canonicalReference(coordinate, location, repeating),
                    screen.x(),
                    screen.y());
        }

        private void visitSequence(
                SnapReferenceLayer layer,
                SnapFeature feature,
                Location base,
                CoordinateSequence sequence,
                boolean closed) {
            visitPackedSequence(layer, feature, base, sequence, 0, sequence.size(), closed);
        }

        private void visitPackedSequence(
                SnapReferenceLayer layer,
                SnapFeature feature,
                Location base,
                CoordinateSequence sequence,
                int start,
                int end,
                boolean closed) {
            int size = end - start;
            int vertexCount = closed ? size - 1 : size;
            boolean repeating = query.repeatsLayer(layer.layerId());
            boolean geographicShortestPath =
                    repeating
                            && query.coordinatesToDisplay()
                                    .sourceCrs()
                                    .equals(CrsDefinitions.EPSG_4326);
            double referenceX =
                    query.viewport().screenToWorld(query.screenX(), query.screenY()).x();
            Double fixedOffsetX = null;
            Coordinate previousCoordinate = null;
            Coordinate previousScreen = null;
            for (int element = 0; element < size; element++) {
                Location location = base.withElement(element);
                Coordinate coordinate = sequence.coordinate(start + element);
                checkCancellation(location.context(true));
                coordinates =
                        increment(
                                coordinates,
                                query.limits().maximumCoordinates(),
                                location.context(true));
                boolean hasSegment =
                        previousCoordinate != null
                                && (Double.compare(previousCoordinate.x(), coordinate.x()) != 0
                                        || Double.compare(previousCoordinate.y(), coordinate.y())
                                                != 0);
                Location segmentLocation = base.withElement(Math.max(0, element - 1));
                if (hasSegment) {
                    checkCancellation(segmentLocation.context(true));
                    segments =
                            increment(
                                    segments,
                                    query.limits().maximumSegments(),
                                    segmentLocation.context(true));
                }
                ScreenCoordinate transformed =
                        toScreen(coordinate, location, repeating, referenceX, fixedOffsetX);
                if (repeating) {
                    if (geographicShortestPath) {
                        referenceX = transformed.worldX();
                    } else if (fixedOffsetX == null) {
                        fixedOffsetX = transformed.worldX() - transformed.sourceDisplayX();
                    }
                }
                if (element < vertexCount) {
                    considerVertex(
                            layer,
                            feature,
                            location,
                            canonicalReference(coordinate, location, repeating),
                            transformed.screen().x(),
                            transformed.screen().y());
                }
                if (hasSegment) {
                    considerSegment(
                            layer,
                            feature,
                            segmentLocation,
                            previousScreen.x(),
                            previousScreen.y(),
                            transformed.screen().x(),
                            transformed.screen().y(),
                            repeating);
                }
                previousCoordinate = coordinate;
                previousScreen = transformed.screen();
            }
        }

        private void considerVertex(
                SnapReferenceLayer layer,
                SnapFeature feature,
                Location location,
                Coordinate coordinate,
                double screenX,
                double screenY) {
            double deltaX = screenX - query.screenX();
            double deltaY = screenY - query.screenY();
            if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
                rejectCoordinate(location);
            }
            if (Math.abs(deltaX) > query.tolerancePixels()
                    || Math.abs(deltaY) > query.tolerancePixels()) {
                return;
            }
            double distanceSquared = deltaX * deltaX + deltaY * deltaY;
            if (distanceSquared > query.tolerancePixels() * query.tolerancePixels()) {
                return;
            }
            consider(
                    new Candidate(
                            new SnapResult(
                                    coordinate,
                                    Math.sqrt(distanceSquared),
                                    SnapTargetType.VERTEX,
                                    layer.layerId(),
                                    feature.featureId(),
                                    location.componentIndex(),
                                    location.partIndex(),
                                    location.elementIndex()),
                            distanceSquared,
                            location.layerIndex(),
                            location.featureIndex()));
        }

        private void considerSegment(
                SnapReferenceLayer layer,
                SnapFeature feature,
                Location location,
                double ax,
                double ay,
                double bx,
                double by,
                boolean repeating) {
            double pointerX = query.screenX();
            double pointerY = query.screenY();
            double tolerance = query.tolerancePixels();
            double minimumX = Math.min(ax, bx);
            double maximumX = Math.max(ax, bx);
            double minimumY = Math.min(ay, by);
            double maximumY = Math.max(ay, by);
            if ((pointerX < minimumX && minimumX - pointerX > tolerance)
                    || (pointerX > maximumX && pointerX - maximumX > tolerance)
                    || (pointerY < minimumY && minimumY - pointerY > tolerance)
                    || (pointerY > maximumY && pointerY - maximumY > tolerance)) {
                return;
            }
            double scale =
                    Math.max(
                            1,
                            Math.max(
                                    Math.max(Math.abs(ax), Math.abs(ay)),
                                    Math.max(
                                            Math.max(Math.abs(bx), Math.abs(by)),
                                            Math.max(Math.abs(pointerX), Math.abs(pointerY)))));
            double normalizedAx = ax / scale;
            double normalizedAy = ay / scale;
            double normalizedBx = bx / scale;
            double normalizedBy = by / scale;
            double normalizedPointerX = pointerX / scale;
            double normalizedPointerY = pointerY / scale;
            double deltaX = normalizedBx - normalizedAx;
            double deltaY = normalizedBy - normalizedAy;
            double denominator = deltaX * deltaX + deltaY * deltaY;
            if (!Double.isFinite(denominator) || denominator <= 0) {
                rejectCoordinate(location);
            }
            double fraction =
                    ((normalizedPointerX - normalizedAx) * deltaX
                                    + (normalizedPointerY - normalizedAy) * deltaY)
                            / denominator;
            fraction = Math.max(0, Math.min(1, fraction));
            double closestX = normalizedAx + fraction * deltaX;
            double closestY = normalizedAy + fraction * deltaY;
            double normalizedDistance =
                    Math.hypot(closestX - normalizedPointerX, closestY - normalizedPointerY);
            if (!Double.isFinite(normalizedDistance)) {
                rejectCoordinate(location);
            }
            double normalizedTolerance = tolerance / scale;
            if (normalizedDistance > normalizedTolerance) {
                return;
            }
            double screenX = fraction == 0 ? ax : fraction == 1 ? bx : closestX * scale;
            double screenY = fraction == 0 ? ay : fraction == 1 ? by : closestY * scale;
            double pixelDeltaX = screenX - pointerX;
            double pixelDeltaY = screenY - pointerY;
            if (!Double.isFinite(screenX)
                    || !Double.isFinite(screenY)
                    || !Double.isFinite(pixelDeltaX)
                    || !Double.isFinite(pixelDeltaY)) {
                rejectCoordinate(location);
            }
            if (Math.abs(pixelDeltaX) > tolerance || Math.abs(pixelDeltaY) > tolerance) {
                return;
            }
            double distanceSquared = pixelDeltaX * pixelDeltaX + pixelDeltaY * pixelDeltaY;
            if (distanceSquared > tolerance * tolerance) {
                return;
            }
            double distance = Math.sqrt(distanceSquared);
            Coordinate coordinate = toReference(screenX, screenY, location, repeating);
            consider(
                    new Candidate(
                            new SnapResult(
                                    coordinate,
                                    distance,
                                    SnapTargetType.SEGMENT,
                                    layer.layerId(),
                                    feature.featureId(),
                                    location.componentIndex(),
                                    location.partIndex(),
                                    location.elementIndex()),
                            distanceSquared,
                            location.layerIndex(),
                            location.featureIndex()));
        }

        private ScreenCoordinate toScreen(
                Coordinate coordinate,
                Location location,
                boolean repeating,
                double referenceX,
                Double fixedOffsetX) {
            try {
                Coordinate display = query.coordinatesToDisplay().transform(coordinate);
                double sourceDisplayX = display.x();
                if (repeating) {
                    HorizontalWrap wrap = query.horizontalWrap().orElseThrow();
                    double placedX;
                    if (fixedOffsetX == null) {
                        placedX = wrap.nearestEquivalent(display.x(), referenceX);
                    } else {
                        placedX = display.x() + fixedOffsetX;
                        wrap.canonicalize(placedX);
                    }
                    display = new Coordinate(placedX, display.y());
                }
                return new ScreenCoordinate(
                        query.viewport().worldToScreen(display), display.x(), sourceDisplayX);
            } catch (CrsException failure) {
                translateCrs(failure, location);
                throw failure;
            } catch (IllegalArgumentException failure) {
                rejectCoordinate(location);
                throw failure;
            }
        }

        private Coordinate toReference(
                double screenX, double screenY, Location location, boolean repeating) {
            try {
                Coordinate display = query.viewport().screenToWorld(screenX, screenY);
                if (repeating) {
                    display =
                            new Coordinate(
                                    query.horizontalWrap()
                                            .orElseThrow()
                                            .canonicalize(display.x())
                                            .canonicalX(),
                                    display.y());
                }
                return query.displayToCoordinates().transform(display);
            } catch (CrsException failure) {
                translateCrs(failure, location);
                throw failure;
            } catch (IllegalArgumentException failure) {
                rejectCoordinate(location);
                throw failure;
            }
        }

        private Coordinate canonicalReference(
                Coordinate coordinate, Location location, boolean repeating) {
            if (!repeating) {
                return coordinate;
            }
            try {
                Coordinate display = query.coordinatesToDisplay().transform(coordinate);
                double canonicalX =
                        query.horizontalWrap().orElseThrow().canonicalize(display.x()).canonicalX();
                if (Double.compare(canonicalX, display.x()) == 0) {
                    return coordinate;
                }
                display = new Coordinate(canonicalX, display.y());
                Coordinate canonical = query.displayToCoordinates().transform(display);
                return query.coordinatesToDisplay().sourceCrs().equals(CrsDefinitions.EPSG_4326)
                        ? new Coordinate(canonical.x(), coordinate.y())
                        : canonical;
            } catch (CrsException failure) {
                translateCrs(failure, location);
                throw failure;
            } catch (IllegalArgumentException failure) {
                rejectCoordinate(location);
                throw failure;
            }
        }

        private void translateCrs(CrsException failure, Location location) {
            String code = failure.problem().code();
            if (code.equals("CRS_COORDINATE_OUT_OF_DOMAIN")
                    || code.equals("CRS_TRANSFORM_NON_FINITE")) {
                rejectCoordinate(location);
            }
        }

        private void rejectCoordinate(Location location) {
            throw new Rejected(
                    new FeatureEditProblem(
                            "EDIT_SNAP_COORDINATE_UNREPRESENTABLE",
                            "Snap coordinate cannot be represented through the captured transform",
                            location.context(true)));
        }

        private void checkCancellation(Map<String, String> context) {
            if (query.cancellation().isCancellationRequested()) {
                throw new Rejected(
                        new FeatureEditProblem(
                                "EDIT_SNAP_CANCELLED", "Snap query was cancelled", context));
            }
        }

        private long increment(long current, long maximum, Map<String, String> location) {
            long next;
            try {
                next = Math.addExact(current, 1);
            } catch (ArithmeticException ignored) {
                next = Long.MAX_VALUE;
            }
            if (next > maximum) {
                Map<String, String> context = new LinkedHashMap<>(location);
                context.put("maximum", Long.toString(maximum));
                context.put("actual", Long.toString(next));
                throw new Rejected(
                        new FeatureEditProblem(
                                "EDIT_SNAP_LIMIT_EXCEEDED", "Snap query limit exceeded", context));
            }
            return next;
        }

        private void consider(Candidate candidate) {
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
    }

    private record Location(
            int layerIndex, int featureIndex, int componentIndex, int partIndex, int elementIndex) {
        private Location withGeometry(int component, int part, int element) {
            return new Location(layerIndex, featureIndex, component, part, element);
        }

        private Location withElement(int element) {
            return withGeometry(componentIndex, partIndex, element);
        }

        private Map<String, String> context(boolean geometry) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("layerIndex", Integer.toString(layerIndex));
            result.put("featureIndex", Integer.toString(featureIndex));
            if (geometry) {
                result.put("componentIndex", Integer.toString(componentIndex));
                result.put("partIndex", Integer.toString(partIndex));
                result.put("elementIndex", Integer.toString(elementIndex));
            }
            return result;
        }
    }

    private record ScreenCoordinate(Coordinate screen, double worldX, double sourceDisplayX) {}

    private record Candidate(
            SnapResult result, double distanceSquared, int layerIndex, int featureIndex)
            implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate other) {
            int comparison = Double.compare(distanceSquared, other.distanceSquared);
            if (comparison != 0) {
                return comparison;
            }
            comparison = result.targetType().compareTo(other.result.targetType());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(other.layerIndex, layerIndex);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(other.featureIndex, featureIndex);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(result.componentIndex(), other.result.componentIndex());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(result.partIndex(), other.result.partIndex());
            return comparison != 0
                    ? comparison
                    : Integer.compare(result.elementIndex(), other.result.elementIndex());
        }
    }

    @SuppressWarnings("serial")
    private static final class Rejected extends RuntimeException {
        private final FeatureEditProblem problem;

        private Rejected(FeatureEditProblem problem) {
            super(null, null, false, false);
            this.problem = problem;
        }

        private FeatureEditProblem problem() {
            return problem;
        }
    }
}
