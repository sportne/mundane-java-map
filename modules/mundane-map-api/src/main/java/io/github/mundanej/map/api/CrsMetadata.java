package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable recognized or retained-unknown coordinate-reference metadata. */
public final class CrsMetadata {
    /** Maximum declared-identifier length. */
    public static final int DECLARED_IDENTIFIER_LIMIT = 256;

    /** Maximum retained-definition length. */
    public static final int RETAINED_DEFINITION_LIMIT = 16_384;

    private final Optional<CrsDefinition> definition;
    private final Optional<String> declaredIdentifier;
    private final Optional<String> retainedDefinition;

    private CrsMetadata(
            Optional<CrsDefinition> definition,
            Optional<String> declaredIdentifier,
            Optional<String> retainedDefinition) {
        this.definition = definition;
        this.declaredIdentifier = declaredIdentifier;
        this.retainedDefinition = retainedDefinition;
    }

    /** Creates metadata for an explicitly recognized definition. */
    public static CrsMetadata recognized(
            CrsDefinition definition,
            Optional<String> declaredIdentifier,
            Optional<String> retainedDefinition) {
        Objects.requireNonNull(definition, "definition");
        return new CrsMetadata(
                Optional.of(definition),
                validateDeclared(declaredIdentifier),
                validateRetained(retainedDefinition));
    }

    /** Creates metadata that retains an unrecognized declaration without guessing a transform. */
    public static CrsMetadata unknown(
            Optional<String> declaredIdentifier, Optional<String> retainedDefinition) {
        Optional<String> declared = validateDeclared(declaredIdentifier);
        Optional<String> retained = validateRetained(retainedDefinition);
        if (declared.isEmpty() && retained.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown CRS metadata must retain an identifier or definition");
        }
        return new CrsMetadata(Optional.empty(), declared, retained);
    }

    /** Returns the recognized definition, or empty for retained unknown metadata. */
    public Optional<CrsDefinition> definition() {
        return definition;
    }

    /** Returns the exact declared identifier when one was supplied. */
    public Optional<String> declaredIdentifier() {
        return declaredIdentifier;
    }

    /** Returns the exact bounded retained definition when one was supplied. */
    public Optional<String> retainedDefinition() {
        return retainedDefinition;
    }

    /** Returns the recognized canonical identifier, if any. */
    public Optional<String> canonicalIdentifier() {
        return definition.map(CrsDefinition::canonicalIdentifier);
    }

    /** Returns the recognized kind, or {@link CrsKind#UNKNOWN}. */
    public CrsKind kind() {
        return definition.map(CrsDefinition::kind).orElse(CrsKind.UNKNOWN);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CrsMetadata metadata
                && definition.equals(metadata.definition)
                && declaredIdentifier.equals(metadata.declaredIdentifier)
                && retainedDefinition.equals(metadata.retainedDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, declaredIdentifier, retainedDefinition);
    }

    @Override
    public String toString() {
        return "CrsMetadata[definition="
                + definition
                + ", declaredIdentifier="
                + declaredIdentifier
                + ", retainedDefinition="
                + retainedDefinition
                + ']';
    }

    private static Optional<String> validateDeclared(Optional<String> value) {
        Objects.requireNonNull(value, "declaredIdentifier");
        value.ifPresent(
                identifier -> {
                    if (identifier.isBlank() || identifier.length() > DECLARED_IDENTIFIER_LIMIT) {
                        throw new IllegalArgumentException(
                                "Declared CRS identifier must be non-blank and at most 256 characters");
                    }
                });
        return value;
    }

    private static Optional<String> validateRetained(Optional<String> value) {
        Objects.requireNonNull(value, "retainedDefinition");
        value.ifPresent(
                retained -> {
                    if (retained.isBlank() || retained.length() > RETAINED_DEFINITION_LIMIT) {
                        throw new IllegalArgumentException(
                                "Retained CRS definition must be non-blank and at most 16384 characters");
                    }
                });
        return value;
    }
}
