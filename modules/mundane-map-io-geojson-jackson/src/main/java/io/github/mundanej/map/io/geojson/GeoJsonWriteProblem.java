package io.github.mundanej.map.io.geojson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable structured GeoJSON write failure.
 *
 * @param code stable machine-readable code
 * @param message fixed human-readable meaning
 * @param context ordered bounded machine-readable context
 */
public record GeoJsonWriteProblem(String code, String message, Map<String, String> context) {
    /** Defensively copies the structured problem. */
    public GeoJsonWriteProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(context, "context");
        if (code.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("GeoJSON write problem text must not be blank");
        }
        context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }
}
