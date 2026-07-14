package io.github.mundanej.map.api;

/** A two-dimensional geometry with a finite envelope. */
public sealed interface Geometry
        permits PointGeometry,
                LineStringGeometry,
                PolygonGeometry,
                MultiPointGeometry,
                MultiLineStringGeometry,
                MultiPolygonGeometry {
    /** Returns the geometry envelope in source-coordinate units. */
    Envelope envelope();
}
