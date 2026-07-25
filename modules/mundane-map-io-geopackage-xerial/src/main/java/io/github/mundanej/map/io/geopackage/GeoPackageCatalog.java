package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.DiagnosticReport;
import java.util.List;
import java.util.Objects;

/**
 * Detached immutable catalog for the first GeoPackage profile.
 *
 * @param featureTables supported Point and MultiPoint feature tables in catalog order
 * @param tileTables supported tile tables; source opening is introduced by G10-042
 * @param report bounded opening warnings
 */
public record GeoPackageCatalog(
        List<GeoPackageFeatureTable> featureTables,
        List<GeoPackageTileTable> tileTables,
        DiagnosticReport report) {
    /** Defensively copies catalog entries. */
    public GeoPackageCatalog {
        featureTables = List.copyOf(Objects.requireNonNull(featureTables, "featureTables"));
        tileTables = List.copyOf(Objects.requireNonNull(tileTables, "tileTables"));
        Objects.requireNonNull(report, "report");
    }
}
