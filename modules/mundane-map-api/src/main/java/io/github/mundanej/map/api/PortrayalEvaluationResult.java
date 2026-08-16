package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable expression evaluation outcome.
 *
 * @param value optional canonical result
 * @param code empty on success or stable non-blank failure code
 * @param message empty on success or human-readable failure message
 */
public record PortrayalEvaluationResult(Optional<Object> value, String code, String message) {
    /** Validates success/failure exclusivity. */
    public PortrayalEvaluationResult {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (value.isPresent() == !code.isEmpty() || code.isEmpty() != message.isEmpty()) {
            throw new IllegalArgumentException(
                    "Evaluation result must be exactly success or failure");
        }
        value = value.map(AttributeValues::canonicalizeValue);
    }

    /**
     * Creates a successful canonical result.
     *
     * @param value supported result value
     * @return successful outcome
     */
    public static PortrayalEvaluationResult success(Object value) {
        return new PortrayalEvaluationResult(Optional.of(value), "", "");
    }

    /**
     * Creates a stable failure.
     *
     * @param code non-blank stable code
     * @param message non-blank human-readable message
     * @return failed outcome
     */
    public static PortrayalEvaluationResult failure(String code, String message) {
        if (Objects.requireNonNull(code, "code").isBlank()
                || Objects.requireNonNull(message, "message").isBlank()) {
            throw new IllegalArgumentException("Failure code and message must not be blank");
        }
        return new PortrayalEvaluationResult(Optional.empty(), code, message);
    }

    /**
     * Returns whether evaluation succeeded.
     *
     * @return success state
     */
    public boolean succeeded() {
        return value.isPresent();
    }
}
