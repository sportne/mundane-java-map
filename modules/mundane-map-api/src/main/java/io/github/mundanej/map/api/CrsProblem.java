package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stable bounded detail for a CRS registry, domain, or numeric failure.
 *
 * @param code stable uppercase machine-readable code
 * @param message bounded human-readable explanation
 * @param context immutable bounded diagnostic context, ordered by key
 */
public record CrsProblem(String code, String message, Map<String, String> context) {
    private static final int CODE_LIMIT = 64;
    private static final int MESSAGE_LIMIT = 1_024;
    private static final int CONTEXT_LIMIT = 16;
    private static final int CONTEXT_KEY_LIMIT = 64;
    private static final int CONTEXT_VALUE_LIMIT = 256;

    /** Creates and validates a problem. */
    public CrsProblem {
        code = validateCode(code);
        message = boundedNonBlank(message, MESSAGE_LIMIT, "message");
        Objects.requireNonNull(context, "context");
        if (context.size() > CONTEXT_LIMIT) {
            throw new IllegalArgumentException("CRS problem context has too many entries");
        }
        TreeMap<String, String> ordered = new TreeMap<>();
        context.forEach(
                (key, value) ->
                        ordered.put(
                                boundedNonBlank(key, CONTEXT_KEY_LIMIT, "context key"),
                                boundedNonBlank(value, CONTEXT_VALUE_LIMIT, "context value")));
        context = Collections.unmodifiableMap(ordered);
    }

    private static String boundedNonBlank(String value, int limit, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > limit) {
            throw new IllegalArgumentException(name + " is blank or exceeds its bound");
        }
        return value;
    }

    private static String validateCode(String value) {
        String code = boundedNonBlank(value, CODE_LIMIT, "code");
        if (code.charAt(0) < 'A' || code.charAt(0) > 'Z') {
            throw new IllegalArgumentException("CRS problem code has an invalid grammar");
        }
        for (int index = 1; index < code.length(); index++) {
            char character = code.charAt(index);
            if ((character < 'A' || character > 'Z')
                    && (character < '0' || character > '9')
                    && character != '_') {
                throw new IllegalArgumentException("CRS problem code has an invalid grammar");
            }
        }
        return code;
    }
}
