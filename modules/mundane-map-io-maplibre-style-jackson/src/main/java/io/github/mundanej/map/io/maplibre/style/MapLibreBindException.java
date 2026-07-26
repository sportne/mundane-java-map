package io.github.mundanej.map.io.maplibre.style;

import java.util.Objects;

/** Unchecked terminal failure from explicit MapLibre source binding. */
@SuppressWarnings("serial")
public final class MapLibreBindException extends RuntimeException {
    /** Stable structured failure detail. */
    private final MapLibreProblem problem;

    /**
     * Creates a stable bind failure.
     *
     * @param problem immutable bind-phase detail
     * @throws IllegalArgumentException if an argument violates the documented constraints
     */
    public MapLibreBindException(MapLibreProblem problem) {
        super("MapLibre style bind failed: " + Objects.requireNonNull(problem, "problem").code());
        if (!"bind".equals(problem.phase())) {
            throw new IllegalArgumentException("problem must use the bind phase");
        }
        this.problem = problem;
    }

    /**
     * Returns immutable structured failure detail.
     *
     * @return problem
     */
    public MapLibreProblem problem() {
        return problem;
    }
}
