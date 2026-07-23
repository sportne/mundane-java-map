package io.github.mundanej.map.io.se;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structured problem from the bounded Symbology Encoding reader.
 *
 * @param code stable uppercase machine-readable code
 * @param sourceName bounded logical source name
 * @param context insertion-ordered immutable bounded context
 */
public record SeReadProblem(String code, String sourceName, Map<String, String> context) {
    /** Validates and defensively copies a problem. */
    public SeReadProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(context, "context");
        if (!code.matches("[A-Z][A-Z0-9_]{0,63}")
                || sourceName.isBlank()
                || sourceName.length() > 256
                || context.size() > 16) {
            throw new IllegalArgumentException("SE problem is outside its bounded profile");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        context.forEach(
                (key, value) -> {
                    Objects.requireNonNull(key, "context key");
                    Objects.requireNonNull(value, "context value");
                    if (key.isBlank() || key.length() > 64 || value.length() > 256) {
                        throw new IllegalArgumentException(
                                "SE problem context is outside its bounded profile");
                    }
                    copy.put(key, value);
                });
        context = Collections.unmodifiableMap(copy);
    }
}
