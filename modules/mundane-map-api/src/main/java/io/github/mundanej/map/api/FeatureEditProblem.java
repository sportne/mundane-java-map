package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stable bounded application-edit failure detail.
 *
 * @param code uppercase machine-readable edit code
 * @param message bounded non-blank explanation
 * @param context immutable bounded context ordered by key
 */
public record FeatureEditProblem(String code, String message, Map<String, String> context) {
    private static final Set<String> CONTEXT_KEYS =
            Set.of(
                    "actual",
                    "actualCrs",
                    "actualRevision",
                    "commandIndex",
                    "componentIndex",
                    "elementIndex",
                    "expectedCrs",
                    "expectedRevision",
                    "featureIndex",
                    "layerIndex",
                    "maximum",
                    "partIndex",
                    "reason");

    /** Validates and canonicalizes the problem. */
    public FeatureEditProblem {
        code = boundedNonBlank(code, 64, "code");
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            if ((index == 0 && (character < 'A' || character > 'Z'))
                    || (index > 0
                            && (character < 'A' || character > 'Z')
                            && (character < '0' || character > '9')
                            && character != '_')) {
                throw new IllegalArgumentException("problem code has an invalid grammar");
            }
        }
        message = boundedNonBlank(message, 1_024, "message");
        Objects.requireNonNull(context, "context");
        if (context.size() > 16) {
            throw new IllegalArgumentException("problem context has too many entries");
        }
        TreeMap<String, String> ordered = new TreeMap<>();
        context.forEach(
                (key, value) -> {
                    String checkedKey = boundedNonBlank(key, 64, "context key");
                    if (!CONTEXT_KEYS.contains(checkedKey)) {
                        throw new IllegalArgumentException("problem context key is not supported");
                    }
                    ordered.put(checkedKey, boundedNonBlank(value, 256, "context value"));
                });
        context = Collections.unmodifiableMap(ordered);
    }

    private static String boundedNonBlank(String value, int maximum, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is blank or exceeds its bound");
        }
        return value;
    }
}
