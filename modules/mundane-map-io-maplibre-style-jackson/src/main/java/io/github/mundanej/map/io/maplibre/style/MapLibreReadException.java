package io.github.mundanej.map.io.maplibre.style;

import java.util.Objects;

/** Unchecked terminal failure from the bounded MapLibre style reader. */
@SuppressWarnings("serial")
public final class MapLibreReadException extends RuntimeException {
    /** Stable structured failure detail. */
    private final MapLibreProblem problem;

    /**
     * Creates a stable failure.
     *
     * @param problem immutable failure detail
     */
    public MapLibreReadException(MapLibreProblem problem) {
        this(problem, null);
    }

    /**
     * Creates a stable failure with a non-contractual debugging cause.
     *
     * @param problem immutable failure detail
     * @param cause optional cause
     */
    public MapLibreReadException(MapLibreProblem problem, Throwable cause) {
        super(
                "MapLibre style read failed: " + Objects.requireNonNull(problem, "problem").code(),
                cause);
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
