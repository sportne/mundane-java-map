package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stable bounded point-label collection or layout failure.
 *
 * @param code supported stable machine-readable failure code
 * @param message bounded non-blank explanation without label text
 * @param context immutable insertion-ordered bounded context
 */
public record LabelPlacementProblem(String code, String message, Map<String, String> context) {
    private static final Set<String> CODES =
            Set.of(
                    "LABEL_REQUEST_LIMIT_EXCEEDED",
                    "LABEL_CANDIDATE_LIMIT_EXCEEDED",
                    "LABEL_COLLISION_WORK_LIMIT_EXCEEDED",
                    "LABEL_TEXT_LIMIT_EXCEEDED",
                    "LABEL_TEXT_BUDGET_EXCEEDED",
                    "LABEL_TEXT_MULTILINE_UNSUPPORTED",
                    "LABEL_METRICS_NON_FINITE",
                    "LABEL_LAYOUT_NON_FINITE");
    private static final Set<String> KEYS =
            Set.of(
                    "layerIndex",
                    "featureIndex",
                    "limit",
                    "attempted",
                    "attemptedAtLeast",
                    "codePoint",
                    "quantity",
                    "position");

    /** Validates and retains context in insertion order. */
    public LabelPlacementProblem {
        Objects.requireNonNull(code, "code");
        if (!CODES.contains(code)) {
            throw new IllegalArgumentException("unsupported label problem code");
        }
        message = bounded(message, 1_024, "message");
        Objects.requireNonNull(context, "context");
        if (context.size() > 8) {
            throw new IllegalArgumentException("label problem context has too many entries");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        context.forEach(
                (key, value) -> {
                    if (!KEYS.contains(key)) {
                        throw new IllegalArgumentException("unsupported label problem context key");
                    }
                    copy.put(key, bounded(value, 256, "context value"));
                });
        context = Collections.unmodifiableMap(copy);
    }

    private static String bounded(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is blank or exceeds its bound");
        }
        return value;
    }
}
