package io.github.mundanej.map.io.geopackage;

import java.util.List;
import java.util.Objects;

record GeoPackageCatalogSnapshot(
        GeoPackageCatalog catalog,
        List<GeoPackageTableProfile> featureProfiles,
        List<GeoPackageTileProfile> tileProfiles) {
    GeoPackageCatalogSnapshot {
        Objects.requireNonNull(catalog, "catalog");
        featureProfiles = List.copyOf(featureProfiles);
        tileProfiles = List.copyOf(tileProfiles);
    }

    GeoPackageTableProfile requireFeature(String tableName, String sourceId) {
        return featureProfiles.stream()
                .filter(profile -> profile.table().tableName().equals(tableName))
                .findFirst()
                .orElseThrow(
                        () ->
                                GeoPackageFailures.failure(
                                        sourceId,
                                        "GEOPACKAGE_SCHEMA_INVALID",
                                        "Selected GeoPackage table is unavailable",
                                        java.util.Map.of(
                                                "object",
                                                "selectedTable",
                                                "field",
                                                "kind",
                                                "reason",
                                                "missing")));
    }

    GeoPackageTileProfile requireTile(String tableName, String sourceId) {
        return tileProfiles.stream()
                .filter(profile -> profile.table().tableName().equals(tableName))
                .findFirst()
                .orElseThrow(
                        () ->
                                GeoPackageFailures.failure(
                                        sourceId,
                                        "GEOPACKAGE_SCHEMA_INVALID",
                                        "Selected GeoPackage tile table is unavailable",
                                        java.util.Map.of(
                                                "object",
                                                "selectedTable",
                                                "field",
                                                "kind",
                                                "reason",
                                                "missing")));
    }
}
