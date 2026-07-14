package io.github.mundanej.map.api;

import java.util.Objects;

/** Sequenced semantic command routed to a map tool. */
public record MapToolCommandEvent(long sequence, MapToolCommand command) {
    /** Validates the sequence and command. */
    public MapToolCommandEvent {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(command, "command");
    }
}
