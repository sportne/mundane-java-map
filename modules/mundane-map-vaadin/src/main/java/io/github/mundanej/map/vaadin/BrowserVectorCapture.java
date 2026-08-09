package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.api.VectorExportSnapshotLimits;
import io.github.mundanej.map.api.VectorExportSnapshotProblem;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Failure-atomic conversion of an acknowledged browser scene into the API export boundary. */
final class BrowserVectorCapture {
    private BrowserVectorCapture() {}

    static VectorExportSnapshot capture(
            List<? extends Layer> layers,
            List<PlacedPointLabel> placedLabels,
            MapViewport viewport,
            Rgba background,
            VectorExportSnapshotLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(placedLabels, "placedLabels");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(cancellation);
        try {
            VectorExportSnapshot.of(
                    viewport.width(),
                    viewport.height(),
                    background,
                    new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                    layers.size(),
                    List.of(),
                    List.of(),
                    limits,
                    cancellation);
            requireLimit("labels", limits.maximumLabels(), placedLabels.size());
            List<VectorExportSnapshot.Label> labels = new ArrayList<>(placedLabels.size());
            for (PlacedPointLabel label : placedLabels) {
                checkCancelled(cancellation);
                labels.add(
                        new VectorExportSnapshot.Label(
                                label.text(),
                                label.style(),
                                label.baselineX(),
                                label.baselineY(),
                                label.advance(),
                                label.ordinaryPaintOrdinal()));
            }
            List<VectorExportSnapshot.Primitive> retained = new ArrayList<>();
            long coordinates = 0;
            for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                Layer layer = Objects.requireNonNull(layers.get(layerIndex), "layer");
                List<io.github.mundanej.map.api.Feature> features = List.copyOf(layer.features());
                for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
                    checkCancelled(cancellation);
                    io.github.mundanej.map.api.Feature feature = features.get(featureIndex);
                    requireLimit("features", limits.maximumFeatures(), (long) retained.size() + 1);
                    coordinates = add(coordinates, coordinateCount(feature.geometry()));
                    requireLimit("coordinates", limits.maximumCoordinates(), coordinates);
                    retained.add(
                            new VectorExportSnapshot.Primitive(
                                    layerIndex,
                                    featureIndex,
                                    feature.geometry(),
                                    feature.symbol()));
                }
            }
            double scale = 1.0 / viewport.worldUnitsPerPixel();
            Coordinate origin = screen(new Coordinate(0, 0), viewport);
            VectorExportSnapshot.ViewFrame frame =
                    new VectorExportSnapshot.ViewFrame(scale, 0.0, origin);
            VectorExportSnapshot.of(
                    viewport.width(),
                    viewport.height(),
                    background,
                    frame,
                    layers.size(),
                    retained,
                    labels,
                    limits,
                    cancellation);
            List<VectorExportSnapshot.Primitive> primitives = new ArrayList<>(retained.size());
            for (VectorExportSnapshot.Primitive primitive : retained) {
                checkCancelled(cancellation);
                primitives.add(
                        new VectorExportSnapshot.Primitive(
                                primitive.layerIndex(),
                                primitive.featureIndex(),
                                screenGeometry(primitive.screenGeometry(), viewport, cancellation),
                                primitive.symbol()));
            }
            return VectorExportSnapshot.of(
                    viewport.width(),
                    viewport.height(),
                    background,
                    frame,
                    layers.size(),
                    primitives,
                    labels,
                    limits,
                    cancellation);
        } catch (VectorExportSnapshotException exception) {
            throw exception;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw invalid("screenGeometry", exception);
        }
    }

    private static Geometry screenGeometry(
            Geometry geometry, MapViewport viewport, CancellationToken cancellation) {
        checkCancelled(cancellation);
        if (geometry instanceof PointGeometry point) {
            return new PointGeometry(screen(point.coordinate(), viewport));
        }
        if (geometry instanceof MultiPointGeometry points) {
            return new MultiPointGeometry(screen(points.coordinates(), viewport, cancellation));
        }
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(screen(line.coordinates(), viewport, cancellation));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    screen(lines.coordinates(), viewport, cancellation), lines.partOffsets());
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return MultiPolygonGeometry.of(
                    screen(polygons.coordinates(), viewport, cancellation),
                    polygons.ringOffsets(),
                    polygons.polygonRingOffsets());
        }
        PolygonGeometry polygon = (PolygonGeometry) geometry;
        List<CoordinateSequence> holes = new ArrayList<>(polygon.holes().size());
        for (CoordinateSequence hole : polygon.holes()) {
            checkCancelled(cancellation);
            holes.add(screen(hole, viewport, cancellation));
        }
        return new PolygonGeometry(screen(polygon.exterior(), viewport, cancellation), holes);
    }

    private static CoordinateSequence screen(
            CoordinateSequence coordinates, MapViewport viewport, CancellationToken cancellation) {
        double[] result = new double[Math.multiplyExact(coordinates.size(), 2)];
        for (int index = 0; index < coordinates.size(); index++) {
            checkCancelled(cancellation);
            Coordinate point = screen(coordinates.coordinate(index), viewport);
            result[index * 2] = point.x();
            result[index * 2 + 1] = point.y();
        }
        return CoordinateSequence.of(result);
    }

    private static long coordinateCount(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 1;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return points.coordinates().size();
        }
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.coordinates().size();
        }
        PolygonGeometry polygon = (PolygonGeometry) geometry;
        long count = polygon.exterior().size();
        for (CoordinateSequence hole : polygon.holes()) {
            count = add(count, hole.size());
        }
        return count;
    }

    private static Coordinate screen(Coordinate coordinate, MapViewport viewport) {
        return new Coordinate(
                viewport.width() / 2.0
                        + (coordinate.x() - viewport.centerX()) / viewport.worldUnitsPerPixel(),
                viewport.height() / 2.0
                        - (coordinate.y() - viewport.centerY()) / viewport.worldUnitsPerPixel());
    }

    private static void checkCancelled(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw new VectorExportSnapshotException(
                    "Vector-export snapshot construction was cancelled",
                    new VectorExportSnapshotProblem("VECTOR_EXPORT_SNAPSHOT_CANCELLED", Map.of()));
        }
    }

    private static void requireLimit(String name, long maximum, long requested) {
        if (requested <= maximum) {
            return;
        }
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("limit", name);
        context.put("maximum", Long.toString(maximum));
        context.put("requested", Long.toString(requested));
        throw new VectorExportSnapshotException(
                "A vector-export snapshot limit was exceeded",
                new VectorExportSnapshotProblem("VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED", context));
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static VectorExportSnapshotException invalid(String field, RuntimeException cause) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("field", field);
        context.put("reason", "nonFinite");
        return new VectorExportSnapshotException(
                "The browser vector-export snapshot contains an invalid value",
                new VectorExportSnapshotProblem("VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID", context),
                cause);
    }
}
