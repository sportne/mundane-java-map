package io.github.mundanej.map.io.se;

import io.github.mundanej.map.api.CancellationToken;
import java.util.Objects;

/**
 * Immutable options for one Symbology Encoding read.
 *
 * @param limits bounded resource limits
 * @param cancellation cancellation signal
 */
public record SeReadOptions(SeReadLimits limits, CancellationToken cancellation) {
    /** Validates the options. */
    public SeReadOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
    }

    /**
     * Returns the bounded defaults with no cancellation request.
     *
     * @return immutable default options
     */
    public static SeReadOptions defaults() {
        return new SeReadOptions(SeReadLimits.defaults(), CancellationToken.none());
    }
}
