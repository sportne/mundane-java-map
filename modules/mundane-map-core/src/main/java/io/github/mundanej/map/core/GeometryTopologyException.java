package io.github.mundanej.map.core;

import java.util.Map;
import java.util.Objects;

/** Stable atomic failure from a bounded geometry topology operation. */
@SuppressWarnings("serial")
public final class GeometryTopologyException extends RuntimeException {
    /** Input coordinate limit was exceeded before an operation published a result. */
    public static final String COORDINATE_LIMIT = "geometry.topology.coordinateLimit";

    /** Segment-comparison work limit was exceeded before an operation published a result. */
    public static final String COMPARISON_LIMIT = "geometry.topology.comparisonLimit";

    /** Output coordinate limit was exceeded before an operation published a result. */
    public static final String OUTPUT_LIMIT = "geometry.topology.outputLimit";

    /** Stable machine-readable failure code. */
    private final String code;

    /** Immutable bounded diagnostic context. */
    private final Map<String, String> context;

    GeometryTopologyException(String code, String message, Map<String, String> context) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.context = Map.copyOf(Objects.requireNonNull(context, "context"));
    }

    /**
     * Returns the stable machine-readable failure code.
     *
     * @return stable failure code
     */
    public String code() {
        return code;
    }

    /**
     * Returns immutable bounded diagnostic context.
     *
     * @return immutable diagnostic context
     */
    public Map<String, String> context() {
        return context;
    }
}
