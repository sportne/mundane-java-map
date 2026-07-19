package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable notification for one committed edit revision.
 *
 * @param cause closed transition cause
 * @param previous immutable snapshot before the transition
 * @param current immutable snapshot after the transition
 * @param description bounded transaction description
 */
public record FeatureEditEvent(
        FeatureEditCause cause,
        FeatureEditSnapshot previous,
        FeatureEditSnapshot current,
        String description) {
    /** Validates revision, CRS, and description consistency. */
    public FeatureEditEvent {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(description, "description");
        if (!previous.crs().equals(current.crs())
                || previous.equals(current)
                || previous.revision() == Long.MAX_VALUE
                || current.revision() != previous.revision() + 1) {
            throw new IllegalArgumentException("event snapshots are inconsistent");
        }
        if (description.isBlank()
                || description.length() > FeatureEditTransaction.MAXIMUM_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description is blank or exceeds its bound");
        }
    }
}
