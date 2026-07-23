package io.github.mundanej.map.io.se;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable retained SE description.
 *
 * @param title optional bounded title
 * @param abstractText optional bounded abstract
 */
public record SeDescription(Optional<String> title, Optional<String> abstractText) {
    /** Validates the optional values. */
    public SeDescription {
        title = copy(title, "title");
        abstractText = copy(abstractText, "abstractText");
    }

    /**
     * Returns an empty description.
     *
     * @return immutable empty description
     */
    public static SeDescription empty() {
        return new SeDescription(Optional.empty(), Optional.empty());
    }

    private static Optional<String> copy(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(
                text -> {
                    Objects.requireNonNull(text, name + " value");
                    if (text.isBlank() || !text.equals(text.strip()) || text.length() > 4_096) {
                        throw new IllegalArgumentException(
                                name + " is outside the bounded profile");
                    }
                    return text;
                });
    }
}
