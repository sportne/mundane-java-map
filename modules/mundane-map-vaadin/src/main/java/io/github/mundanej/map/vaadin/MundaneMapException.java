package io.github.mundanej.map.vaadin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A browser-map validation or lifecycle failure with a stable code and immutable context. */
@SuppressWarnings("serial")
public final class MundaneMapException extends RuntimeException {
    /** The component has been closed. */
    public static final String CLOSED = "CLOSED";

    /** The component is disabled. */
    public static final String DISABLED = "DISABLED";

    /** A protocol version is not supported. */
    public static final String PROTOCOL_VERSION_UNSUPPORTED = "PROTOCOL_VERSION_UNSUPPORTED";

    /** A client or server message belongs to an obsolete generation. */
    public static final String STALE_GENERATION = "STALE_GENERATION";

    /** A client event sequence is duplicate, unsafe, or out of order. */
    public static final String EVENT_SEQUENCE_INVALID = "EVENT_SEQUENCE_INVALID";

    /** A non-coalescible client event exceeded its rate budget. */
    public static final String EVENT_RATE_EXCEEDED = "EVENT_RATE_EXCEEDED";

    /** A value is not finite. */
    public static final String NON_FINITE_VALUE = "NON_FINITE_VALUE";

    /** Two values claim the same identity in one namespace. */
    public static final String DUPLICATE_ID = "DUPLICATE_ID";

    /** A fixed scene or Canvas limit was exceeded. */
    public static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";

    /** A geometry or symbol is outside the accepted browser profile. */
    public static final String UNSUPPORTED_VALUE = "SYMBOL_UNSUPPORTED";

    /** The browser does not provide a required closed-platform capability. */
    public static final String BROWSER_CAPABILITY_UNSUPPORTED = "BROWSER_CAPABILITY_UNSUPPORTED";

    /** The browser rejected or failed to paint an accepted scene. */
    public static final String CLIENT_FAILURE = "CLIENT_FAILURE";

    /** A component/session-owned raster resource is absent, expired, or invalid. */
    public static final String RESOURCE_UNAVAILABLE = "RESOURCE_UNAVAILABLE";

    /** A raster or elevation source is incompatible with horizontal repetition. */
    public static final String WORLD_WRAP_RASTER_INCOMPATIBLE = "WORLD_WRAP_RASTER_INCOMPATIBLE";

    /** Stable failure code retained with the exception. */
    private final String code;

    /** Immutable ordered string context retained with the exception. */
    private final Map<String, String> context;

    /**
     * Creates a stable failure.
     *
     * @param code non-blank machine-readable code
     * @param message non-blank human-readable message
     * @param context insertion-ordered string context
     */
    public MundaneMapException(String code, String message, Map<String, String> context) {
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
     * Returns the stable machine-readable code.
     *
     * @return stable code
     */
    public String code() {
        return code;
    }

    /**
     * Returns immutable insertion-ordered diagnostic context.
     *
     * @return immutable context
     */
    public Map<String, String> context() {
        return context;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
