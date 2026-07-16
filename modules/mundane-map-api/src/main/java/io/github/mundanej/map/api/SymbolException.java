package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A symbol validation or rendering failure with a stable code and immutable string context. */
@SuppressWarnings("serial")
public final class SymbolException extends RuntimeException {
    /** A feature geometry and symbol role are incompatible. */
    public static final String ROLE_MISMATCH = "SYMBOL_ROLE_MISMATCH";

    /** No renderer was explicitly registered for a symbol role and key. */
    public static final String RENDERER_NOT_REGISTERED = "SYMBOL_RENDERER_NOT_REGISTERED";

    /** A renderer key was paired with an unsupported symbol value shape. */
    public static final String RENDERER_VALUE_MISMATCH = "SYMBOL_RENDERER_VALUE_MISMATCH";

    /** A finite symbol input overflowed while deriving a screen transform. */
    public static final String TRANSFORM_NON_FINITE = "SYMBOL_TRANSFORM_NON_FINITE";

    /** A bounded hatch layout requires more candidate segments than permitted. */
    public static final String HATCH_SEGMENT_LIMIT_EXCEEDED = "SYMBOL_HATCH_SEGMENT_LIMIT_EXCEEDED";

    /** A named symbol catalog contains an exact duplicate name. */
    public static final String CATALOG_DUPLICATE = "SYMBOL_CATALOG_DUPLICATE";

    /** A required exact symbol catalog name is absent. */
    public static final String CATALOG_MISSING = "SYMBOL_CATALOG_MISSING";

    /** Application code attempted to register a reserved built-in renderer key. */
    public static final String RENDERER_RESERVED_KEY = "SYMBOL_RENDERER_RESERVED_KEY";

    /** A renderer registry already contains the same role and key. */
    public static final String RENDERER_DUPLICATE = "SYMBOL_RENDERER_DUPLICATE";

    /** A renderer returned a result incompatible with its registered role. */
    public static final String RENDERER_INVALID_RESULT = "SYMBOL_RENDERER_INVALID_RESULT";

    /** Stable failure code retained for serialization. */
    private final String code;

    /** Immutable failure context retained for serialization. */
    private final Map<String, String> context;

    /**
     * Creates a failure from a stable code, message, and ordered string context.
     *
     * @param code stable non-blank machine-readable code
     * @param message non-blank human-readable explanation
     * @param context insertion-ordered diagnostic context
     */
    public SymbolException(String code, String message, Map<String, String> context) {
        super(Objects.requireNonNull(message, "message"));
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
     * Returns the stable machine-readable failure code.
     *
     * @return stable code
     */
    public String code() {
        return code;
    }

    /**
     * Returns immutable, insertion-ordered diagnostic context.
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
