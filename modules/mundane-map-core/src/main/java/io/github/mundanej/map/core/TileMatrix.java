package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable encoding-independent OGC TileMatrixSet 2.0 tile-matrix definition.
 *
 * @param identifier bounded identifier unique within its set
 * @param scaleDenominator positive scale denominator
 * @param cellSize positive cell size in CRS units
 * @param pointOfOrigin origin ordinates in the owning set's declared axis order
 * @param cornerOfOrigin corner containing row zero
 * @param tileWidth positive tile width in cells
 * @param tileHeight positive tile height in cells
 * @param matrixWidth positive nominal matrix width in tiles
 * @param matrixHeight positive matrix height in tiles
 * @param variableMatrixWidths non-overlapping coalesced row bands
 */
public record TileMatrix(
        String identifier,
        double scaleDenominator,
        double cellSize,
        Coordinate pointOfOrigin,
        TileMatrixCorner cornerOfOrigin,
        int tileWidth,
        int tileHeight,
        long matrixWidth,
        long matrixHeight,
        List<VariableMatrixWidth> variableMatrixWidths) {
    /** Maximum accepted tile width or height in cells. */
    public static final int MAXIMUM_TILE_SIZE = 65_536;

    /** Maximum accepted nominal row or column count. */
    public static final long MAXIMUM_MATRIX_DIMENSION = 4_294_967_296L;

    /** Maximum coalesced row-band declarations in one matrix. */
    public static final int MAXIMUM_VARIABLE_WIDTHS = 1_024;

    /** Validates and defensively copies the complete matrix definition. */
    public TileMatrix {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(pointOfOrigin, "pointOfOrigin");
        Objects.requireNonNull(cornerOfOrigin, "cornerOfOrigin");
        if (identifier.isBlank() || identifier.length() > 128) {
            throw new IllegalArgumentException(
                    "Tile-matrix identifier must be non-blank and bounded");
        }
        if (!Double.isFinite(scaleDenominator)
                || scaleDenominator <= 0
                || !Double.isFinite(cellSize)
                || cellSize <= 0) {
            throw new IllegalArgumentException(
                    "Tile-matrix scale values must be positive and finite");
        }
        if (tileWidth <= 0
                || tileWidth > MAXIMUM_TILE_SIZE
                || tileHeight <= 0
                || tileHeight > MAXIMUM_TILE_SIZE
                || matrixWidth <= 0
                || matrixWidth > MAXIMUM_MATRIX_DIMENSION
                || matrixHeight <= 0
                || matrixHeight > MAXIMUM_MATRIX_DIMENSION) {
            throw new IllegalArgumentException("Tile-matrix dimensions are outside their profile");
        }
        if (!Double.isFinite(cellSize * tileWidth * matrixWidth)
                || !Double.isFinite(cellSize * tileHeight * matrixHeight)) {
            throw new IllegalArgumentException("Tile-matrix world extent is not finite");
        }
        variableMatrixWidths =
                List.copyOf(Objects.requireNonNull(variableMatrixWidths, "variableMatrixWidths"));
        if (variableMatrixWidths.size() > MAXIMUM_VARIABLE_WIDTHS
                || !variableMatrixWidths.equals(
                        variableMatrixWidths.stream()
                                .sorted(
                                        Comparator.comparingLong(
                                                VariableMatrixWidth::minimumTileRow))
                                .toList())) {
            throw new IllegalArgumentException(
                    "Variable matrix widths are not bounded and ordered");
        }
        long previousMaximum = -1;
        for (VariableMatrixWidth width : variableMatrixWidths) {
            if (width.minimumTileRow() <= previousMaximum
                    || width.maximumTileRow() >= matrixHeight
                    || matrixWidth % width.coalesce() != 0) {
                throw new IllegalArgumentException(
                        "Variable matrix widths must be disjoint and divide the matrix");
            }
            previousMaximum = width.maximumTileRow();
        }
    }

    /**
     * Returns the coalescence factor for one valid row.
     *
     * @param row zero-based matrix row
     * @return one for a normal row or the declared factor
     */
    public int coalesce(long row) {
        requireRow(row);
        for (VariableMatrixWidth width : variableMatrixWidths) {
            if (row < width.minimumTileRow()) {
                break;
            }
            if (row <= width.maximumTileRow()) {
                return width.coalesce();
            }
        }
        return 1;
    }

    /**
     * Returns the physical tile-column count for one valid row.
     *
     * @param row zero-based matrix row
     * @return nominal width divided by the row coalescence factor
     */
    public long columnCount(long row) {
        return matrixWidth / coalesce(row);
    }

    private void requireRow(long row) {
        if (row < 0 || row >= matrixHeight) {
            throw new IllegalArgumentException("Tile row is outside the matrix");
        }
    }
}
