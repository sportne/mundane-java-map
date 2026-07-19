package io.github.mundanej.map.api;

import java.util.Objects;

/** Listener failure raised only after the carried edit result has committed. */
@SuppressWarnings("serial")
public final class FeatureEditNotificationException extends RuntimeException {
    /** Applied result retained for serialization. */
    private final FeatureEditResult committedResult;

    /**
     * Creates a post-commit listener failure.
     *
     * @param committedResult applied authoritative result
     * @param cause first listener failure
     */
    public FeatureEditNotificationException(
            FeatureEditResult committedResult, RuntimeException cause) {
        super(
                "Feature edit committed but listener notification failed",
                Objects.requireNonNull(cause));
        this.committedResult = Objects.requireNonNull(committedResult, "committedResult");
        if (committedResult.status() != FeatureEditStatus.APPLIED) {
            throw new IllegalArgumentException("committedResult must be applied");
        }
    }

    /**
     * Returns the authoritative already-committed result.
     *
     * @return applied result
     */
    public FeatureEditResult committedResult() {
        return committedResult;
    }
}
