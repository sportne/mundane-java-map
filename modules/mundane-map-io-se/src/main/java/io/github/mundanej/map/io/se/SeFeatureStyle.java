package io.github.mundanej.map.io.se;

import io.github.mundanej.map.api.FeaturePortrayal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable retained metadata and ordinary portrayal from one supported SE feature style.
 *
 * @param name optional bounded style name
 * @param description retained style description
 * @param featureTypeName optional bounded feature-type name
 * @param semanticTypeIdentifiers retained declaration-ordered semantic identifiers
 * @param rules retained declaration-ordered rule metadata
 * @param portrayal ordinary toolkit-neutral feature portrayal
 */
public record SeFeatureStyle(
        Optional<String> name,
        SeDescription description,
        Optional<String> featureTypeName,
        List<String> semanticTypeIdentifiers,
        List<SeRuleMetadata> rules,
        FeaturePortrayal portrayal) {
    /** Validates and defensively copies the style. */
    public SeFeatureStyle {
        name = text(name, "name");
        Objects.requireNonNull(description, "description");
        featureTypeName = text(featureTypeName, "featureTypeName");
        semanticTypeIdentifiers = texts(semanticTypeIdentifiers, "semanticTypeIdentifiers");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        rules.forEach(rule -> Objects.requireNonNull(rule, "rule"));
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        Objects.requireNonNull(portrayal, "portrayal");
    }

    /**
     * Returns the immutable declaration-ordered semantic identifiers.
     *
     * @return immutable identifiers
     */
    @Override
    public List<String> semanticTypeIdentifiers() {
        return List.copyOf(semanticTypeIdentifiers);
    }

    private static Optional<String> text(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(
                text -> {
                    Objects.requireNonNull(text, name + " value");
                    validate(text, name);
                    return text;
                });
    }

    private static List<String> texts(List<String> values, String name) {
        List<String> copy = List.copyOf(Objects.requireNonNull(values, name));
        copy.forEach(value -> validate(Objects.requireNonNull(value, name + " value"), name));
        return copy;
    }

    private static void validate(String value, String name) {
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > 4_096) {
            throw new IllegalArgumentException(name + " is outside the bounded profile");
        }
    }
}
