package io.github.mundanej.map.io.maplibre.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable bounded MapLibre read failure.
 *
 * @param code machine-readable code
 * @param phase fixed processing phase
 * @param location non-sensitive specification location
 * @param context immutable bounded context
 */
public record MapLibreProblem(
        String code, String phase, String location, Map<String, String> context) {
    /** Validates and defensively copies a problem. */
    public MapLibreProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(context, "context");
        if (!code.matches("[A-Z][A-Z0-9_]{0,63}")
                || !phase.matches("[a-z]{1,16}")
                || location.isBlank()
                || location.length() > 256
                || context.size() > 16) {
            throw new IllegalArgumentException("MapLibre problem is outside its bounded profile");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        context.forEach(
                (key, value) -> {
                    Objects.requireNonNull(key, "context key");
                    Objects.requireNonNull(value, "context value");
                    if (key.isBlank() || key.length() > 64 || value.length() > 256) {
                        throw new IllegalArgumentException("MapLibre problem context is invalid");
                    }
                    copy.put(key, value);
                });
        context = Collections.unmodifiableMap(copy);
    }
}
