package io.github.mundanej.map.io.se;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable retained metadata for one SE rule.
 *
 * @param name optional bounded name
 * @param description retained description
 */
public record SeRuleMetadata(Optional<String> name, SeDescription description) {
    /** Validates the metadata. */
    public SeRuleMetadata {
        Objects.requireNonNull(name, "name");
        name =
                name.map(
                        value -> {
                            Objects.requireNonNull(value, "name value");
                            if (value.isBlank()
                                    || !value.equals(value.strip())
                                    || value.length() > 4_096) {
                                throw new IllegalArgumentException(
                                        "name is outside the bounded profile");
                            }
                            return value;
                        });
        Objects.requireNonNull(description, "description");
    }
}
