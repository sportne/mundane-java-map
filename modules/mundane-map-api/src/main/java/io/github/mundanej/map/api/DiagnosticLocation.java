package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Optional bounded structural location within a source.
 *
 * @param component optional bounded source-component name
 * @param recordNumber optional positive one-based record number
 * @param partIndex optional non-negative zero-based part index
 * @param fieldIndex optional non-negative zero-based field index
 * @param fieldName optional bounded field name
 * @param byteOffset optional non-negative byte offset from the component start
 */
public record DiagnosticLocation(
        Optional<String> component,
        OptionalLong recordNumber,
        OptionalInt partIndex,
        OptionalInt fieldIndex,
        Optional<String> fieldName,
        OptionalLong byteOffset) {
    /** Validates a location. */
    public DiagnosticLocation {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(recordNumber, "recordNumber");
        Objects.requireNonNull(partIndex, "partIndex");
        Objects.requireNonNull(fieldIndex, "fieldIndex");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(byteOffset, "byteOffset");
        component.ifPresent(value -> bounded(value, 32, "component"));
        fieldName.ifPresent(value -> bounded(value, 256, "fieldName"));
        if ((recordNumber.isPresent() && recordNumber.getAsLong() <= 0)
                || (partIndex.isPresent() && partIndex.getAsInt() < 0)
                || (fieldIndex.isPresent() && fieldIndex.getAsInt() < 0)
                || (byteOffset.isPresent() && byteOffset.getAsLong() < 0)) {
            throw new IllegalArgumentException("Diagnostic indexes are out of range");
        }
    }

    /**
     * Returns an empty location.
     *
     * @return location with every optional component absent
     */
    public static DiagnosticLocation empty() {
        return new DiagnosticLocation(
                Optional.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    private static void bounded(String value, int maximum, String name) {
        if (value.isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is out of range");
        }
    }
}
