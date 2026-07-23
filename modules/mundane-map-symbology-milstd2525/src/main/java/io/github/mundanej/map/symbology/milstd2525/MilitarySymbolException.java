package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;

/** Failure to parse or strictly resolve a military symbol identifier. */
@SuppressWarnings("serial")
public final class MilitarySymbolException extends IllegalArgumentException {
    /** Stable structured problem. */
    private final MilitarySymbolProblem problem;

    /**
     * Creates a failure with stable structured detail.
     *
     * @param message non-contractual human-readable message
     * @param problem stable problem
     */
    public MilitarySymbolException(String message, MilitarySymbolProblem problem) {
        super(message);
        this.problem = Objects.requireNonNull(problem, "problem");
    }

    /**
     * Returns stable structured detail.
     *
     * @return immutable problem
     */
    public MilitarySymbolProblem problem() {
        return problem;
    }
}
