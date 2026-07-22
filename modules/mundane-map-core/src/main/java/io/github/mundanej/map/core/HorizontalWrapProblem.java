package io.github.mundanej.map.core;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Stable bounded description of a horizontal-wrap planning failure.
 *
 * @param code stable uppercase machine-readable code
 * @param context immutable bounded context ordered by key
 */
public record HorizontalWrapProblem(String code, Map<String, String> context) {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** Validates and defensively copies the problem. */
    public HorizontalWrapProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(context, "context");
        if (!CODE.matcher(code).matches() || code.length() > 64 || context.size() > 8) {
            throw new IllegalArgumentException("Horizontal-wrap problem is outside its profile");
        }
        TreeMap<String, String> ordered = new TreeMap<>();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "context key");
            String value = Objects.requireNonNull(entry.getValue(), "context value");
            if (key.isEmpty() || key.length() > 64 || value.length() > 64) {
                throw new IllegalArgumentException(
                        "Horizontal-wrap context is outside its profile");
            }
            ordered.put(key, value);
        }
        context = Collections.unmodifiableMap(ordered);
    }
}
