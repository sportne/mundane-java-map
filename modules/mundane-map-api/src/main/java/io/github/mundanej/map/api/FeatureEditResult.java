package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable exact outcome of an edit attempt.
 *
 * @param status closed outcome status
 * @param snapshot authoritative current snapshot after the attempt
 * @param problem present exactly for a rejected result
 */
public record FeatureEditResult(
        FeatureEditStatus status,
        FeatureEditSnapshot snapshot,
        Optional<FeatureEditProblem> problem) {
    /** Enforces exact status variants. */
    public FeatureEditResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(problem, "problem");
        if ((status == FeatureEditStatus.REJECTED) != problem.isPresent()) {
            throw new IllegalArgumentException("only rejected results carry a problem");
        }
    }

    /**
     * Returns an applied result.
     *
     * @param snapshot authoritative committed snapshot
     * @return applied result without a problem
     */
    public static FeatureEditResult applied(FeatureEditSnapshot snapshot) {
        return new FeatureEditResult(FeatureEditStatus.APPLIED, snapshot, Optional.empty());
    }

    /**
     * Returns an unchanged result.
     *
     * @param snapshot authoritative unchanged snapshot
     * @return unchanged result without a problem
     */
    public static FeatureEditResult unchanged(FeatureEditSnapshot snapshot) {
        return new FeatureEditResult(FeatureEditStatus.UNCHANGED, snapshot, Optional.empty());
    }

    /**
     * Returns a rejected result.
     *
     * @param snapshot authoritative unchanged snapshot
     * @param problem stable rejection detail
     * @return rejected result carrying the problem
     */
    public static FeatureEditResult rejected(
            FeatureEditSnapshot snapshot, FeatureEditProblem problem) {
        return new FeatureEditResult(
                FeatureEditStatus.REJECTED, snapshot, Optional.of(Objects.requireNonNull(problem)));
    }
}
