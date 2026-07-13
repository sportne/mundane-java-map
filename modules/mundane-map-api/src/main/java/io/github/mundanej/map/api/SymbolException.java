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

    private final String code;
    private final Map<String, String> context;

    /** Creates a failure from a stable code, message, and ordered string context. */
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

    /** Returns the stable machine-readable failure code. */
    public String code() {
        return code;
    }

    /** Returns immutable, insertion-ordered diagnostic context. */
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
