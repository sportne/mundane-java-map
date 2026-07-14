package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable recognized coordinate-reference-system definition. */
public record CrsDefinition(
        String canonicalIdentifier,
        CrsKind kind,
        CrsAxis xAxis,
        CrsAxis yAxis,
        Envelope coordinateDomain) {
    private static final int IDENTIFIER_LIMIT = 256;

    /** Creates and validates a recognized definition. */
    public CrsDefinition {
        canonicalIdentifier = boundedNonBlank(canonicalIdentifier, "canonicalIdentifier");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(xAxis, "xAxis");
        Objects.requireNonNull(yAxis, "yAxis");
        Objects.requireNonNull(coordinateDomain, "coordinateDomain");
        if (kind == CrsKind.UNKNOWN) {
            throw new IllegalArgumentException("A recognized CRS definition cannot be UNKNOWN");
        }
        if (coordinateDomain.width() <= 0.0 || coordinateDomain.height() <= 0.0) {
            throw new IllegalArgumentException("A CRS coordinate domain must have positive spans");
        }
        if (kind == CrsKind.GEOGRAPHIC
                && (xAxis.meaning() != CrsAxisMeaning.LONGITUDE
                        || xAxis.unit() != CrsUnit.DEGREE
                        || yAxis.meaning() != CrsAxisMeaning.LATITUDE
                        || yAxis.unit() != CrsUnit.DEGREE)) {
            throw new IllegalArgumentException(
                    "A geographic CRS must use longitude/latitude degree axes");
        }
        if (kind == CrsKind.PROJECTED
                && (xAxis.meaning() != CrsAxisMeaning.EASTING
                        || xAxis.unit() != CrsUnit.METRE
                        || yAxis.meaning() != CrsAxisMeaning.NORTHING
                        || yAxis.unit() != CrsUnit.METRE)) {
            throw new IllegalArgumentException(
                    "A projected CRS must use easting/northing metre axes");
        }
    }

    private static String boundedNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > IDENTIFIER_LIMIT) {
            throw new IllegalArgumentException(
                    name + " must be non-blank and at most 256 characters");
        }
        return value;
    }
}
