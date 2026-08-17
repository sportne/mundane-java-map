package io.github.mundanej.map.core;

/**
 * OGC TileMatrixSet 2.0 row band whose tiles coalesce nominal columns.
 *
 * @param coalesce number of nominal matrix columns represented by one tile
 * @param minimumTileRow inclusive first affected row
 * @param maximumTileRow inclusive last affected row
 */
public record VariableMatrixWidth(int coalesce, long minimumTileRow, long maximumTileRow) {
    /** Maximum accepted coalescence factor. */
    public static final int MAXIMUM_COALESCE = 1_048_576;

    /** Validates the immutable row-band declaration. */
    public VariableMatrixWidth {
        if (coalesce < 2
                || coalesce > MAXIMUM_COALESCE
                || minimumTileRow < 0
                || maximumTileRow < minimumTileRow) {
            throw new IllegalArgumentException("Variable matrix width is outside its profile");
        }
    }
}
