package io.github.mundanej.map.io.mbtiles;

/**
 * Immutable ceilings for the approved MbTiles profile.
 *
 * @param maximumInputBytes largest accepted container
 * @param maximumSchemaObjects largest inspected schema inventory
 * @param maximumColumns largest selected-table column count
 * @param maximumIdentifierCharacters largest identifier length
 * @param maximumMetadataRows largest core/catalog row count
 * @param maximumTextValueCharacters largest decoded text value
 * @param maximumTextCharacters largest aggregate decoded text
 * @param maximumBlobBytes largest geometry, tile, or attribute BLOB
 * @param maximumRows largest number of rows examined by one operation
 * @param maximumVmOpcodes largest approximate SQLite virtual-machine work budget
 * @param maximumOwnedBytes largest project-owned payload per operation
 * @param maximumZoomLevels largest tile-matrix level count
 * @param maximumZoom largest tile zoom
 * @param maximumMatrixAxis largest matrix or populated-tile axis
 * @param maximumCoordinates largest decoded geometry coordinate count
 * @param maximumParts largest decoded geometry part/ring count
 * @param maximumCacheEntries largest decoded tile-cache entry count
 * @param maximumCacheBytes largest decoded tile-cache RGBA payload
 */
public record MbTilesLimits(
        long maximumInputBytes,
        int maximumSchemaObjects,
        int maximumColumns,
        int maximumIdentifierCharacters,
        long maximumMetadataRows,
        int maximumTextValueCharacters,
        long maximumTextCharacters,
        int maximumBlobBytes,
        long maximumRows,
        long maximumVmOpcodes,
        long maximumOwnedBytes,
        int maximumZoomLevels,
        int maximumZoom,
        int maximumMatrixAxis,
        int maximumCoordinates,
        int maximumParts,
        int maximumCacheEntries,
        long maximumCacheBytes) {
    private static final long TILE_BYTES = 256L * 256L * Integer.BYTES;

    /** Conservative defaults for local read-only containers. */
    public static final MbTilesLimits DEFAULTS =
            new MbTilesLimits(
                    1_073_741_824L,
                    512,
                    128,
                    256,
                    4_096,
                    1_048_576,
                    4_194_304,
                    33_554_432,
                    2_000_000L,
                    50_000_000L,
                    536_870_912L,
                    23,
                    22,
                    4_194_304,
                    1_000_000,
                    100_000,
                    256,
                    67_108_864L);

    /** Validates positive, bounded, mutually reachable ceilings. */
    public MbTilesLimits {
        if (maximumInputBytes <= 0
                || maximumInputBytes > 17_179_869_184L
                || maximumSchemaObjects < 3
                || maximumSchemaObjects > 4_096
                || maximumColumns < 2
                || maximumColumns > 512
                || maximumIdentifierCharacters <= 0
                || maximumIdentifierCharacters > 256
                || maximumMetadataRows < 3
                || maximumMetadataRows > 65_536
                || maximumTextValueCharacters <= 0
                || maximumTextValueCharacters > 16_777_216
                || maximumTextCharacters < maximumTextValueCharacters
                || maximumTextCharacters > 67_108_864L
                || maximumBlobBytes <= 0
                || maximumBlobBytes > 268_435_456
                || maximumRows <= 0
                || maximumRows > 100_000_000L
                || maximumVmOpcodes < 1_000
                || maximumVmOpcodes > 500_000_000L
                || maximumOwnedBytes < 2L * maximumBlobBytes + TILE_BYTES
                || maximumOwnedBytes > Integer.MAX_VALUE
                || maximumZoomLevels <= 0
                || maximumZoomLevels > 23
                || maximumZoom < 0
                || maximumZoom > 22
                || maximumMatrixAxis <= 0
                || maximumMatrixAxis > 8_388_607
                || maximumCoordinates <= 0
                || maximumCoordinates > 10_000_000
                || maximumParts <= 0
                || maximumParts > 1_000_000
                || maximumCacheEntries <= 0
                || maximumCacheEntries > 4_096
                || maximumCacheBytes < TILE_BYTES
                || maximumCacheBytes > 536_870_912L) {
            throw new IllegalArgumentException("MbTiles limits are outside the approved range");
        }
    }

    /**
     * Replaces the schema-object ceiling.
     *
     * @param value replacement ceiling
     * @return copied limits
     */
    public MbTilesLimits withMaximumSchemaObjects(int value) {
        return copy(value, maximumVmOpcodes);
    }

    /**
     * Replaces the virtual-machine opcode ceiling.
     *
     * @param value replacement ceiling
     * @return copied limits
     */
    public MbTilesLimits withMaximumVmOpcodes(long value) {
        return copy(maximumSchemaObjects, value);
    }

    private MbTilesLimits copy(int schemaObjects, long vmOpcodes) {
        return new MbTilesLimits(
                maximumInputBytes,
                schemaObjects,
                maximumColumns,
                maximumIdentifierCharacters,
                maximumMetadataRows,
                maximumTextValueCharacters,
                maximumTextCharacters,
                maximumBlobBytes,
                maximumRows,
                vmOpcodes,
                maximumOwnedBytes,
                maximumZoomLevels,
                maximumZoom,
                maximumMatrixAxis,
                maximumCoordinates,
                maximumParts,
                maximumCacheEntries,
                maximumCacheBytes);
    }
}
