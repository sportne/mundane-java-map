package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** An immutable, standards-neutral geometry value. */
public sealed interface Geometry
        permits PointGeometry,
                LineStringGeometry,
                PolygonGeometry,
                MultiPointGeometry,
                MultiLineStringGeometry,
                MultiPolygonGeometry,
                DimensionalGeometry,
                EmptyGeometry,
                GeometryCollection {
    /**
     * Returns the standards-neutral value family.
     *
     * @return geometry kind
     * @throws IllegalStateException when an implementation does not expose its kind
     */
    default GeometryKind kind() {
        if (this instanceof PointGeometry) {
            return GeometryKind.POINT;
        }
        if (this instanceof LineStringGeometry) {
            return GeometryKind.LINE_STRING;
        }
        if (this instanceof PolygonGeometry) {
            return GeometryKind.POLYGON;
        }
        if (this instanceof MultiPointGeometry) {
            return GeometryKind.MULTI_POINT;
        }
        if (this instanceof MultiLineStringGeometry) {
            return GeometryKind.MULTI_LINE_STRING;
        }
        if (this instanceof MultiPolygonGeometry) {
            return GeometryKind.MULTI_POLYGON;
        }
        throw new IllegalStateException("Geometry implementation must expose its kind");
    }

    /**
     * Returns the coordinate dimensional model.
     *
     * @return geometry dimension
     */
    default GeometryDimension dimension() {
        return GeometryDimension.XY;
    }

    /**
     * Returns whether this value contains no positions, recursively for collections.
     *
     * @return whether the value is empty
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * Returns the finite x/y bounds when at least one position exists.
     *
     * @return optional immutable bounds
     */
    default Optional<Envelope> bounds() {
        return Optional.of(envelope());
    }

    /**
     * Returns the geometry envelope in source-coordinate units.
     *
     * @return immutable source-coordinate envelope
     * @throws GeometryException when the geometry is empty
     */
    Envelope envelope();

    /**
     * Visits this value and every nested collection member in encounter order.
     *
     * @param visitor visitor called once for every node
     */
    default void visit(GeometryVisitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        GeometryTraversal.visit(this, visitor, GeometryLimits.DEFAULT);
    }
}
