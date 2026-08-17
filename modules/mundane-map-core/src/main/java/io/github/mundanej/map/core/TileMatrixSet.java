package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.Envelope;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable JSON/XML-independent OGC TileMatrixSet 2.0 definition.
 *
 * @param identifier bounded set identifier
 * @param crs exact coordinate reference system
 * @param orderedAxes ordering of encoded matrix origin ordinates
 * @param boundingBox supported domain in library x/y presentation
 * @param tileMatrices declaration-ordered matrices
 */
public record TileMatrixSet(
        String identifier,
        CrsDefinition crs,
        TileMatrixAxisOrder orderedAxes,
        Envelope boundingBox,
        List<TileMatrix> tileMatrices) {
    /** Maximum matrices retained in one set. */
    public static final int MAXIMUM_MATRICES = 64;

    /** Validates and defensively copies the complete set. */
    public TileMatrixSet {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(crs, "crs");
        Objects.requireNonNull(orderedAxes, "orderedAxes");
        Objects.requireNonNull(boundingBox, "boundingBox");
        tileMatrices = List.copyOf(Objects.requireNonNull(tileMatrices, "tileMatrices"));
        if (identifier.isBlank()
                || identifier.length() > 128
                || tileMatrices.isEmpty()
                || tileMatrices.size() > MAXIMUM_MATRICES) {
            throw new IllegalArgumentException("Tile matrix set is outside its bounded profile");
        }
        Set<String> identifiers = new HashSet<>();
        double previousScale = Double.POSITIVE_INFINITY;
        for (TileMatrix matrix : tileMatrices) {
            if (!identifiers.add(matrix.identifier())
                    || matrix.scaleDenominator() >= previousScale
                    || !TileMatrixAlgorithms.matrixEnvelope(orderedAxes, matrix)
                            .contains(
                                    new io.github.mundanej.map.api.Coordinate(
                                            boundingBox.minX(), boundingBox.minY()))
                    || !TileMatrixAlgorithms.matrixEnvelope(orderedAxes, matrix)
                            .contains(
                                    new io.github.mundanej.map.api.Coordinate(
                                            boundingBox.maxX(), boundingBox.maxY()))) {
                throw new IllegalArgumentException(
                        "Tile matrices must be unique, scale-ordered, and cover set bounds");
            }
            previousScale = matrix.scaleDenominator();
        }
    }

    /**
     * Resolves one exact matrix identifier.
     *
     * @param exactIdentifier case-sensitive matrix identifier
     * @return exact immutable matrix
     * @throws TileMatrixException when the matrix is not declared
     */
    public TileMatrix matrix(String exactIdentifier) {
        Objects.requireNonNull(exactIdentifier, "exactIdentifier");
        for (TileMatrix matrix : tileMatrices) {
            if (matrix.identifier().equals(exactIdentifier)) {
                return matrix;
            }
        }
        throw new TileMatrixException(
                new TileMatrixProblem(
                        "TILE_MATRIX_UNKNOWN", Map.of("matrix", bounded(exactIdentifier))));
    }

    /**
     * Selects a matrix for a positive scale denominator under an explicit policy.
     *
     * @param scaleDenominator positive requested scale denominator
     * @param policy deterministic selection relationship
     * @return selected declared matrix
     * @throws TileMatrixException when the requested relationship has no matrix
     */
    public TileMatrix select(double scaleDenominator, TileMatrixSelectionPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (!Double.isFinite(scaleDenominator) || scaleDenominator <= 0) {
            throw failure("TILE_MATRIX_SCALE_INVALID");
        }
        TileMatrix selected = null;
        double selectedDistance = Double.POSITIVE_INFINITY;
        for (TileMatrix matrix : tileMatrices) {
            double candidate = matrix.scaleDenominator();
            boolean eligible =
                    switch (policy) {
                        case NEAREST -> true;
                        case COARSER_OR_EQUAL -> candidate >= scaleDenominator;
                        case FINER_OR_EQUAL -> candidate <= scaleDenominator;
                    };
            if (!eligible) {
                continue;
            }
            double distance = Math.abs(StrictMath.log(candidate / scaleDenominator));
            if (Double.compare(distance, selectedDistance) < 0
                    || (Double.compare(distance, selectedDistance) == 0
                            && selected != null
                            && candidate < selected.scaleDenominator())) {
                selected = matrix;
                selectedDistance = distance;
            }
        }
        if (selected == null) {
            throw failure("TILE_MATRIX_SCALE_UNAVAILABLE");
        }
        return selected;
    }

    private static TileMatrixException failure(String code) {
        return new TileMatrixException(new TileMatrixProblem(code, Map.of()));
    }

    private static String bounded(String value) {
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
}
