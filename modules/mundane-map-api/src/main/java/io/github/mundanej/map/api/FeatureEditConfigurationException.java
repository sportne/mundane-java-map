package io.github.mundanej.map.api;

import java.util.Objects;

/** Unchecked problem-bearing failure before an edit session or binding becomes usable. */
@SuppressWarnings("serial")
public final class FeatureEditConfigurationException extends IllegalArgumentException {
    /** Stable problem retained for serialization. */
    private final FeatureEditProblem problem;

    /**
     * Creates a configuration failure.
     *
     * @param problem stable bounded problem
     */
    public FeatureEditConfigurationException(FeatureEditProblem problem) {
        super(Objects.requireNonNull(problem, "problem").message());
        this.problem = problem;
    }

    /**
     * Returns the stable configuration problem.
     *
     * @return immutable problem
     */
    public FeatureEditProblem problem() {
        return problem;
    }
}
