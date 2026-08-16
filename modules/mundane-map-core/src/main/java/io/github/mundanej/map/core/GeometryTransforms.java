package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryLimits;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dimension-preserving coordinate transforms for immutable geometry trees. */
public final class GeometryTransforms {
    private GeometryTransforms() {}

    /** Pure deterministic transform of one x/y position. */
    @FunctionalInterface
    public interface XyTransform {
        /**
         * Transforms one finite x/y position.
         *
         * @param coordinate source x/y
         * @return finite transformed x/y
         */
        Coordinate transform(Coordinate coordinate);
    }

    /**
     * Transforms every x/y position using default prospective limits.
     *
     * <p>Geometry family, typed emptiness, packed fenceposts, collection nesting, and encounter
     * order are preserved. Z and M ordinates are copied unchanged. The transform must be pure and
     * deterministic, including for repeated ring-closure positions.
     *
     * @param geometry immutable source geometry
     * @param transform pure deterministic x/y transform
     * @return complete transformed geometry
     */
    public static Geometry mapXy(Geometry geometry, XyTransform transform) {
        return mapXy(geometry, transform, GeometryTopologyLimits.DEFAULT);
    }

    /**
     * Transforms every x/y position under explicit prospective input/output limits.
     *
     * @param geometry immutable source geometry
     * @param transform pure deterministic x/y transform
     * @param limits prospective limits
     * @return complete transformed geometry
     * @throws GeometryTopologyException when a limit is exceeded before publication
     */
    public static Geometry mapXy(
            Geometry geometry, XyTransform transform, GeometryTopologyLimits limits) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(transform, "transform");
        return map(
                geometry, transform, new TransformWork(Objects.requireNonNull(limits, "limits")));
    }

    private static Geometry map(Geometry geometry, XyTransform transform, TransformWork work) {
        if (geometry.isEmpty()) {
            return geometry;
        }
        if (geometry instanceof GeometryCollection collection) {
            List<Geometry> children = new ArrayList<>(collection.geometries().size());
            for (Geometry child : collection.geometries()) {
                children.add(map(child, transform, work));
            }
            return GeometryCollection.of(children);
        }
        if (geometry instanceof PointGeometry point) {
            work.positions(1);
            return new PointGeometry(apply(transform, point.coordinate()));
        }
        if (geometry instanceof MultiPointGeometry points) {
            return new MultiPointGeometry(map(points.coordinates(), transform, work));
        }
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(map(line.coordinates(), transform, work));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    map(lines.coordinates(), transform, work), lines.partOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            CoordinateSequence exterior = map(polygon.exterior(), transform, work);
            List<CoordinateSequence> holes = new ArrayList<>(polygon.holes().size());
            for (CoordinateSequence hole : polygon.holes()) {
                holes.add(map(hole, transform, work));
            }
            return new PolygonGeometry(exterior, holes);
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return MultiPolygonGeometry.of(
                    map(polygons.coordinates(), transform, work),
                    polygons.ringOffsets(),
                    polygons.polygonRingOffsets());
        }
        DimensionalGeometry dimensional = (DimensionalGeometry) geometry;
        CoordinateSequence coordinates = map(dimensional.coordinates(), transform, work);
        return switch (dimensional.kind()) {
            case POINT -> DimensionalGeometry.point(coordinates);
            case LINE_STRING -> DimensionalGeometry.lineString(coordinates);
            case POLYGON -> DimensionalGeometry.polygon(coordinates, dimensional.partOffsets());
            case MULTI_POINT -> DimensionalGeometry.multiPoint(coordinates);
            case MULTI_LINE_STRING ->
                    DimensionalGeometry.multiLineString(coordinates, dimensional.partOffsets());
            case MULTI_POLYGON ->
                    DimensionalGeometry.multiPolygon(
                            coordinates,
                            dimensional.partOffsets(),
                            dimensional.polygonPartOffsets(),
                            GeometryLimits.DEFAULT);
            case GEOMETRY_COLLECTION ->
                    throw new IllegalArgumentException(
                            "A dimensional primitive cannot be a geometry collection");
        };
    }

    private static CoordinateSequence map(
            CoordinateSequence source, XyTransform transform, TransformWork work) {
        work.positions(source.size());
        GeometryDimension dimension = source.dimension();
        int stride = dimension.stride();
        double[] output = new double[Math.multiplyExact(source.size(), stride)];
        for (int index = 0; index < source.size(); index++) {
            Coordinate transformed = apply(transform, source.coordinate(index));
            int offset = index * stride;
            output[offset] = transformed.x();
            output[offset + 1] = transformed.y();
            if (dimension.hasZ()) {
                output[offset + dimension.zOffset()] = source.z(index);
            }
            if (dimension.hasM()) {
                output[offset + dimension.mOffset()] = source.m(index);
            }
        }
        return CoordinateSequence.of(dimension, output);
    }

    private static Coordinate apply(XyTransform transform, Coordinate coordinate) {
        return Objects.requireNonNull(transform.transform(coordinate), "transformed coordinate");
    }

    private static final class TransformWork {
        private final GeometryTopologyLimits limits;
        private int positions;

        private TransformWork(GeometryTopologyLimits limits) {
            this.limits = limits;
        }

        private void positions(int count) {
            positions = Math.addExact(positions, count);
            if (positions > limits.maxCoordinates()) {
                throw limit(
                        GeometryTopologyException.COORDINATE_LIMIT,
                        "maxCoordinates",
                        positions,
                        limits.maxCoordinates());
            }
            if (positions > limits.maxOutputCoordinates()) {
                throw limit(
                        GeometryTopologyException.OUTPUT_LIMIT,
                        "maxOutputCoordinates",
                        positions,
                        limits.maxOutputCoordinates());
            }
        }

        private static GeometryTopologyException limit(
                String code, String name, int actual, int maximum) {
            return new GeometryTopologyException(
                    code,
                    "Geometry topology " + name + " limit exceeded",
                    Map.of(
                            "actual",
                            Integer.toString(actual),
                            "limit",
                            Integer.toString(maximum),
                            "name",
                            name));
        }
    }
}
