package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryException;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.OrdinateLossPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Deterministic conversion of ordinate-aware geometry trees for x/y-only consumers. */
public final class GeometryDownProjection {
    private GeometryDownProjection() {}

    /**
     * Converts a geometry tree to x/y under a named loss policy.
     *
     * <p>Existing x/y geometry objects are returned unchanged. Dimensional values retain their
     * family and fenceposts. Collection order, nesting, and typed emptiness are preserved.
     *
     * @param geometry immutable input geometry
     * @param policy explicit ordinate-loss policy
     * @return x/y-only geometry
     * @throws GeometryException when loss is rejected
     */
    public static Geometry toXy(Geometry geometry, OrdinateLossPolicy policy) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(policy, "policy");
        if (geometry.dimension() == GeometryDimension.XY) {
            return geometry;
        }
        if (policy == OrdinateLossPolicy.REJECT) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("dimension", geometry.dimension().name());
            context.put("kind", geometry.kind().name());
            context.put("policy", policy.name());
            throw new GeometryException(
                    GeometryException.ORDINATE_LOSS_REJECTED,
                    "X/y conversion would discard unsupported ordinates",
                    context);
        }
        if (geometry instanceof EmptyGeometry empty) {
            return new EmptyGeometry(empty.kind(), GeometryDimension.XY);
        }
        if (geometry instanceof GeometryCollection collection) {
            List<Geometry> converted = new ArrayList<>(collection.geometries().size());
            for (Geometry child : collection.geometries()) {
                converted.add(toXy(child, policy));
            }
            return converted.isEmpty()
                    ? GeometryCollection.empty(GeometryDimension.XY)
                    : GeometryCollection.of(converted);
        }
        if (geometry instanceof DimensionalGeometry dimensional) {
            return convertDimensional(dimensional);
        }
        return geometry;
    }

    private static Geometry convertDimensional(DimensionalGeometry geometry) {
        CoordinateSequence input = geometry.coordinates();
        double[] xy = new double[Math.multiplyExact(input.size(), 2)];
        for (int index = 0; index < input.size(); index++) {
            xy[index * 2] = input.x(index);
            xy[index * 2 + 1] = input.y(index);
        }
        CoordinateSequence coordinates = CoordinateSequence.of(xy);
        GeometryKind kind = geometry.kind();
        return switch (kind) {
            case POINT -> DimensionalGeometry.point(coordinates);
            case LINE_STRING -> DimensionalGeometry.lineString(coordinates);
            case POLYGON -> DimensionalGeometry.polygon(coordinates, geometry.partOffsets());
            case MULTI_POINT -> DimensionalGeometry.multiPoint(coordinates);
            case MULTI_LINE_STRING ->
                    DimensionalGeometry.multiLineString(coordinates, geometry.partOffsets());
            case MULTI_POLYGON ->
                    DimensionalGeometry.multiPolygon(
                            coordinates,
                            geometry.partOffsets(),
                            geometry.polygonPartOffsets(),
                            io.github.mundanej.map.api.GeometryLimits.DEFAULT);
            case GEOMETRY_COLLECTION ->
                    throw new IllegalArgumentException(
                            "A dimensional primitive cannot be a geometry collection");
        };
    }
}
