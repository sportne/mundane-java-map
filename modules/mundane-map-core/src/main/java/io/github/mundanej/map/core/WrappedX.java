package io.github.mundanej.map.core;

/**
 * A canonical horizontal ordinate and its visual world-copy index.
 *
 * @param canonicalX finite ordinate in the owning wrap's half-open canonical interval
 * @param copyIndex signed visual world-copy index
 */
public record WrappedX(double canonicalX, long copyIndex) {
    /** Requires a finite canonical ordinate. */
    public WrappedX {
        if (!Double.isFinite(canonicalX)) {
            throw new IllegalArgumentException("Canonical horizontal ordinate must be finite");
        }
    }
}
