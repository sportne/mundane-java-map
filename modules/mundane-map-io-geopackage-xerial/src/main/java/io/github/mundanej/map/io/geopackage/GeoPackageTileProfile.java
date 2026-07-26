package io.github.mundanej.map.io.geopackage;

import java.util.List;

record GeoPackageTileProfile(GeoPackageTileTable table, List<GeoPackageTileMatrix> matrices) {
    GeoPackageTileProfile {
        matrices = List.copyOf(matrices);
    }

    GeoPackageTileMatrix matrix(String sourceId, int zoom) {
        return matrices.stream()
                .filter(candidate -> candidate.zoom() == zoom)
                .findFirst()
                .orElseThrow(
                        () ->
                                GeoPackageFailures.failure(
                                        sourceId,
                                        "GEOPACKAGE_PROFILE_UNSUPPORTED",
                                        "GeoPackage tile zoom is outside the supported profile",
                                        java.util.Map.of("construct", "zoom")));
    }
}
