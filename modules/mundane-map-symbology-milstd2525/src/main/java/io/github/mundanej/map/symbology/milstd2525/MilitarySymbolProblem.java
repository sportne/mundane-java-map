package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;

/**
 * Stable bounded detail for a malformed or unsupported military symbol identifier.
 *
 * @param code stable uppercase diagnostic code
 * @param field stable field name
 * @param startPosition one-based inclusive SIDC position, or zero for whole-input failures
 * @param endPosition one-based inclusive SIDC position, or zero for whole-input failures
 * @param value bounded offending value
 */
public record MilitarySymbolProblem(
        String code, String field, int startPosition, int endPosition, String value) {
    /** Validates and creates immutable problem detail. */
    public MilitarySymbolProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
        if (code.isBlank() || !code.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("code must be stable uppercase ASCII");
        }
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        boolean wholeInput = startPosition == 0 && endPosition == 0;
        boolean sidcRange =
                startPosition >= 1
                        && startPosition <= endPosition
                        && endPosition <= MilitarySymbolId.LENGTH;
        if (!wholeInput && !sidcRange) {
            throw new IllegalArgumentException("positions must be 0..0 or within the SIDC");
        }
        if (value.length() > MilitarySymbolId.LENGTH) {
            throw new IllegalArgumentException("value exceeds the bounded SIDC length");
        }
    }
}
