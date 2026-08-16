package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable native WKT2 axis metadata.
 *
 * @param name bounded axis name
 * @param abbreviation bounded axis abbreviation
 * @param direction native positive direction
 * @param order one-based tuple order
 * @param unitName bounded unit name
 * @param unitToSi positive conversion factor to radians or metres
 */
public record WktCrsAxis(
        String name,
        String abbreviation,
        CrsAxisDirection direction,
        int order,
        String unitName,
        double unitToSi) {
    private static final int TEXT_LIMIT = 128;

    /** Validates bounded axis metadata. */
    public WktCrsAxis {
        name = bounded(name, "name");
        abbreviation = bounded(abbreviation, "abbreviation");
        Objects.requireNonNull(direction, "direction");
        unitName = bounded(unitName, "unitName");
        if (order < 1 || order > 4) {
            throw new IllegalArgumentException("WKT axis order must be between one and four");
        }
        if (!Double.isFinite(unitToSi) || unitToSi <= 0.0) {
            throw new IllegalArgumentException(
                    "WKT axis unit conversion must be positive and finite");
        }
    }

    private static String bounded(String value, String role) {
        Objects.requireNonNull(value, role);
        if (value.isBlank() || value.length() > TEXT_LIMIT) {
            throw new IllegalArgumentException(role + " must be non-blank and bounded");
        }
        return value;
    }
}
