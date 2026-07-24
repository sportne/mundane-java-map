package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** Closed immutable attribute-input conversion used by bounded selectors. */
public final class AttributeValueConversion {
    /** Identity conversion of the selector's primary attribute. */
    public static final AttributeValueConversion IDENTITY =
            new AttributeValueConversion(Operation.IDENTITY, List.of());

    /** Numeric conversion of the selector's primary attribute only. */
    public static final AttributeValueConversion TO_NUMBER =
            new AttributeValueConversion(Operation.TO_NUMBER, List.of());

    /** String conversion of the selector's primary attribute only. */
    public static final AttributeValueConversion TO_STRING =
            new AttributeValueConversion(Operation.TO_STRING, List.of());

    /** Closed conversion operation. */
    public enum Operation {
        /** Preserve the canonical input type. */
        IDENTITY,
        /** Select the first candidate convertible to a finite decimal. */
        TO_NUMBER,
        /** Convert canonical null, boolean, number, or string to exact text. */
        TO_STRING
    }

    private final Operation operation;
    private final List<AttributeValueCandidate> candidates;

    private AttributeValueConversion(
            Operation operation, List<AttributeValueCandidate> candidates) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        this.candidates.forEach(candidate -> Objects.requireNonNull(candidate, "candidate"));
        if (operation == Operation.IDENTITY && !this.candidates.isEmpty()) {
            throw new IllegalArgumentException("identity conversion has no candidate list");
        }
        if (this.candidates.size() > 8) {
            throw new IllegalArgumentException(
                    "conversion candidates must contain at most 8 entries");
        }
    }

    /**
     * Creates ordered first-success numeric conversion candidates.
     *
     * @param candidates one through eight attribute or literal candidates
     * @return immutable numeric conversion
     */
    public static AttributeValueConversion toNumber(
            List<? extends AttributeValueCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("conversion candidates must be non-empty");
        }
        return new AttributeValueConversion(Operation.TO_NUMBER, List.copyOf(candidates));
    }

    /**
     * Returns the conversion operation.
     *
     * @return closed operation
     */
    public Operation operation() {
        return operation;
    }

    /**
     * Returns ordered explicit candidates, or empty to use the selector's primary attribute.
     *
     * @return immutable candidate list
     */
    public List<AttributeValueCandidate> candidates() {
        return candidates;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AttributeValueConversion conversion
                && operation == conversion.operation
                && candidates.equals(conversion.candidates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, candidates);
    }

    @Override
    public String toString() {
        return "AttributeValueConversion[operation="
                + operation
                + ", candidates="
                + candidates
                + ']';
    }
}
