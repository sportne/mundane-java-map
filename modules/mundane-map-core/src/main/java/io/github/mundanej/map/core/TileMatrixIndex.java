package io.github.mundanej.map.core;

import java.util.Objects;

/**
 * Immutable zero-based tile address.
 *
 * @param matrixIdentifier exact owning matrix identifier
 * @param row non-negative physical tile row
 * @param column non-negative physical tile column for that row
 */
public record TileMatrixIndex(String matrixIdentifier, long row, long column) {
    /** Validates the immutable address shape; matrix bounds are checked by algorithms. */
    public TileMatrixIndex {
        Objects.requireNonNull(matrixIdentifier, "matrixIdentifier");
        if (matrixIdentifier.isBlank()
                || matrixIdentifier.length() > 128
                || row < 0
                || column < 0) {
            throw new IllegalArgumentException("Tile-matrix index is outside its profile");
        }
    }
}
