package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Detached descriptor for one supported feature table.
 *
 * @param tableName exact bounded table name
 * @param geometryColumnName exact bounded geometry-column name
 * @param geometryType canonical declared geometry type
 * @param primaryKey exact integer primary-key column name
 * @param attributeSchema attributes supported by this implementation slice
 * @param srsId declared spatial-reference identifier
 * @param crs retained and possibly recognized CRS metadata
 * @param bounds optional declared content bounds
 * @param featureCount exact row count captured during inspection
 */
public record GeoPackageFeatureTable(
        String tableName,
        String geometryColumnName,
        GeoPackageGeometryType geometryType,
        String primaryKey,
        AttributeSchema attributeSchema,
        int srsId,
        CrsMetadata crs,
        Optional<Envelope> bounds,
        OptionalLong featureCount) {
    /** Validates and retains detached catalog values. */
    public GeoPackageFeatureTable {
        tableName = name(tableName, "tableName");
        geometryColumnName = name(geometryColumnName, "geometryColumnName");
        Objects.requireNonNull(geometryType, "geometryType");
        primaryKey = name(primaryKey, "primaryKey");
        Objects.requireNonNull(attributeSchema, "attributeSchema");
        Objects.requireNonNull(crs, "crs");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(featureCount, "featureCount");
        if (featureCount.isPresent() && featureCount.getAsLong() < 0) {
            throw new IllegalArgumentException("featureCount must be non-negative");
        }
    }

    private static String name(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > 256 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must be a bounded non-blank identifier");
        }
        return value;
    }
}
