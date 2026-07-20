package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Closed result of one bounded snap query.
 *
 * @param status snapped, unsnapped, or rejected
 * @param result present exactly for {@link SnapQueryStatus#SNAPPED}
 * @param problem present exactly for {@link SnapQueryStatus#REJECTED}
 */
public record SnapQueryResult(
        SnapQueryStatus status, Optional<SnapResult> result, Optional<FeatureEditProblem> problem) {
    /** Validates the closed result variants. */
    public SnapQueryResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(problem, "problem");
        boolean valid =
                switch (status) {
                    case SNAPPED -> result.isPresent() && problem.isEmpty();
                    case UNSNAPPED -> result.isEmpty() && problem.isEmpty();
                    case REJECTED -> result.isEmpty() && problem.isPresent();
                };
        if (!valid) {
            throw new IllegalArgumentException("snap query result has an invalid variant");
        }
    }

    /**
     * Returns a snapped result.
     *
     * @param result non-null deterministic winner
     * @return snapped variant
     */
    public static SnapQueryResult snapped(SnapResult result) {
        return new SnapQueryResult(
                SnapQueryStatus.SNAPPED,
                Optional.of(Objects.requireNonNull(result, "result")),
                Optional.empty());
    }

    /**
     * Returns ordinary absence.
     *
     * @return unsnapped variant
     */
    public static SnapQueryResult unsnapped() {
        return new SnapQueryResult(SnapQueryStatus.UNSNAPPED, Optional.empty(), Optional.empty());
    }

    /**
     * Returns a stable rejected result.
     *
     * @param problem non-null stable edit problem
     * @return rejected variant
     */
    public static SnapQueryResult rejected(FeatureEditProblem problem) {
        return new SnapQueryResult(
                SnapQueryStatus.REJECTED,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(problem, "problem")));
    }
}
