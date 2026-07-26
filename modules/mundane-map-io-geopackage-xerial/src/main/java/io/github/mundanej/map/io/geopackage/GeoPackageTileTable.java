package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import java.util.List;
import java.util.Objects;

/**
 * Detached catalog descriptor for a bounded tile source.
 *
 * @param tableName exact bounded table name
 * @param bounds declared tile matrix-set bounds
 * @param crs retained and possibly recognized CRS metadata
 * @param zoomLevels immutable ascending zoom levels
 */
public record GeoPackageTileTable(
        String tableName, Envelope bounds, CrsMetadata crs, List<Integer> zoomLevels) {
    /** Validates and copies catalog values. */
    public GeoPackageTileTable {
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(crs, "crs");
        zoomLevels = List.copyOf(Objects.requireNonNull(zoomLevels, "zoomLevels"));
        if (tableName.isBlank()
                || tableName.length() > 256
                || tableName.indexOf('\0') >= 0
                || zoomLevels.isEmpty()
                || !zoomLevels.equals(zoomLevels.stream().sorted().distinct().toList())) {
            throw new IllegalArgumentException("Invalid GeoPackage tile-table descriptor");
        }
    }
}
