package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable ellipsoid metadata retained from WKT2.
 *
 * @param name bounded ellipsoid name
 * @param semiMajorAxis positive semi-major axis in metres
 * @param inverseFlattening positive inverse flattening, or zero for a sphere
 */
public record CrsEllipsoid(String name, double semiMajorAxis, double inverseFlattening) {
    /** Validates finite-size ellipsoid metadata. */
    public CrsEllipsoid {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > 256) {
            throw new IllegalArgumentException("Ellipsoid name must be non-blank and bounded");
        }
        if (!Double.isFinite(semiMajorAxis) || semiMajorAxis <= 0.0) {
            throw new IllegalArgumentException(
                    "Ellipsoid semi-major axis must be positive and finite");
        }
        if (!Double.isFinite(inverseFlattening) || inverseFlattening < 0.0) {
            throw new IllegalArgumentException(
                    "Ellipsoid inverse flattening must be non-negative and finite");
        }
    }

    /**
     * Returns the ellipsoid flattening, with zero for a sphere.
     *
     * @return non-negative flattening
     */
    public double flattening() {
        return inverseFlattening == 0.0 ? 0.0 : 1.0 / inverseFlattening;
    }
}
