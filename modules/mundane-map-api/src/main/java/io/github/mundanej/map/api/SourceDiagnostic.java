package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Immutable stable bounded source diagnostic.
 *
 * @param code stable uppercase machine-readable code
 * @param severity warning or terminal error severity
 * @param sourceId bounded logical source identity, never a path
 * @param location optional structural source location
 * @param message bounded human-readable explanation
 * @param context immutable bounded diagnostic context, ordered by key
 */
public record SourceDiagnostic(
        String code,
        DiagnosticSeverity severity,
        String sourceId,
        Optional<DiagnosticLocation> location,
        String message,
        Map<String, String> context) {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** Validates and canonicalizes a diagnostic. */
    public SourceDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(context, "context");
        if (!CODE.matcher(code).matches()
                || code.length() > 64
                || sourceId.isBlank()
                || sourceId.length() > 256
                || message.length() > 1024
                || context.size() > 16) {
            throw new IllegalArgumentException("Diagnostic value is outside its bounded profile");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "context key");
            String value = Objects.requireNonNull(entry.getValue(), "context value");
            if (key.isEmpty() || key.length() > 64 || value.length() > 256) {
                throw new IllegalArgumentException(
                        "Diagnostic context is outside its bounded profile");
            }
            sorted.put(key, value);
        }
        context = Collections.unmodifiableMap(sorted);
    }
}
