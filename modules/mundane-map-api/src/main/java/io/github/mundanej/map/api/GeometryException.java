package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Geometry validation or conversion failure with a stable code and immutable context. */
@SuppressWarnings("serial")
public final class GeometryException extends IllegalArgumentException {
    /** An envelope was requested from an empty geometry. */
    public static final String EMPTY_ENVELOPE = "GEOMETRY_EMPTY_ENVELOPE";

    /** A requested ordinate is absent from the dimensional model. */
    public static final String ORDINATE_ABSENT = "GEOMETRY_ORDINATE_ABSENT";

    /** A configured coordinate, part, collection-size, or depth limit was exceeded. */
    public static final String LIMIT_EXCEEDED = "GEOMETRY_LIMIT_EXCEEDED";

    /** A conversion would discard an ordinate without an explicit loss policy. */
    public static final String ORDINATE_LOSS_REJECTED = "GEOMETRY_ORDINATE_LOSS_REJECTED";

    /** A consumer cannot process a geometry family. */
    public static final String KIND_UNSUPPORTED = "GEOMETRY_KIND_UNSUPPORTED";

    /** Stable failure code retained for serialization. */
    private final String code;

    /** Immutable failure context retained for serialization. */
    private final Map<String, String> context;

    /**
     * Creates a stable geometry failure.
     *
     * @param code non-blank machine-readable code
     * @param message non-blank human-readable message
     * @param context ordered string context
     */
    public GeometryException(String code, String message, Map<String, String> context) {
        super(requireText(message, "message"));
        this.code = requireText(code, "code");
        Objects.requireNonNull(context, "context");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        context.forEach(
                (key, value) ->
                        copy.put(
                                requireText(key, "context key"),
                                Objects.requireNonNull(value, "context value")));
        this.context = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the stable failure code.
     *
     * @return machine-readable code
     */
    public String code() {
        return code;
    }

    /**
     * Returns immutable ordered diagnostic context.
     *
     * @return diagnostic context
     */
    public Map<String, String> context() {
        return context;
    }

    static GeometryException emptyEnvelope(GeometryKind kind) {
        return new GeometryException(
                EMPTY_ENVELOPE,
                "An empty geometry has no finite envelope",
                Map.of("kind", kind.name()));
    }

    static GeometryException missingOrdinate(String ordinate, GeometryDimension dimension) {
        return new GeometryException(
                ORDINATE_ABSENT,
                "The coordinate dimensional model does not contain the requested ordinate",
                Map.of("dimension", dimension.name(), "ordinate", ordinate));
    }

    static GeometryException limit(String limit, long actual, long maximum) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("limit", limit);
        context.put("actual", Long.toString(actual));
        context.put("maximum", Long.toString(maximum));
        return new GeometryException(
                LIMIT_EXCEEDED, "A geometry safety limit was exceeded", context);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
