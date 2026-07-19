package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable atomic feature-edit request.
 *
 * @param expectedRevision exact session revision against which commands apply
 * @param description bounded application-facing transaction description
 * @param commands non-empty ordered closed command list
 */
public record FeatureEditTransaction(
        long expectedRevision, String description, List<FeatureEditCommand> commands) {
    /** Maximum UTF-16 description length. */
    public static final int MAXIMUM_DESCRIPTION_LENGTH = 128;

    /** Validates the transaction and defensively copies its commands. */
    public FeatureEditTransaction {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be non-negative");
        }
        Objects.requireNonNull(description, "description");
        if (description.isBlank() || description.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description is blank or exceeds its bound");
        }
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("commands must not be empty");
        }
        Set<String> identities = new HashSet<>();
        for (FeatureEditCommand command : commands) {
            Objects.requireNonNull(command, "command");
            if (!identities.add(command.featureId())) {
                throw new IllegalArgumentException(
                        "a transaction may name each feature id only once");
            }
        }
    }
}
