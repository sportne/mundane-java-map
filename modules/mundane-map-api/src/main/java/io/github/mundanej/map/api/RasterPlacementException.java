package io.github.mundanej.map.api;

import java.io.Serial;
import java.util.Objects;

/** Structured failure to construct an invertible, representable raster grid placement. */
public final class RasterPlacementException extends IllegalArgumentException {
    @Serial private static final long serialVersionUID = 1L;

    /** Stable placement-failure categories for format-neutral diagnostic mapping. */
    public enum Reason {
        /** The affine linear transform has no inverse. */
        SINGULAR,
        /** The affine inverse or inverse translation cannot be represented. */
        INVERSE_NON_FINITE,
        /** At least one represented raster corner cannot be computed finitely. */
        CORNER_NON_FINITE,
        /** A represented edge, determinant, or envelope span cannot be computed finitely. */
        ENVELOPE_NON_FINITE,
        /** The represented footprint or its envelope collapses to zero area. */
        ENVELOPE_NON_POSITIVE
    }

    /** Stable placement category retained for serialization. */
    private final Reason reason;

    /**
     * Creates a failure with a stable reason and no implementation-detail context.
     *
     * @param reason stable placement-failure category
     */
    public RasterPlacementException(Reason reason) {
        super(message(reason));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Creates a failure with a stable reason and retained arithmetic cause.
     *
     * @param reason stable placement-failure category
     * @param cause arithmetic failure that prevented placement construction
     */
    public RasterPlacementException(Reason reason, Throwable cause) {
        super(message(reason), Objects.requireNonNull(cause, "cause"));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the stable failure category.
     *
     * @return stable placement-failure category
     */
    public Reason reason() {
        return reason;
    }

    private static String message(Reason reason) {
        return "Raster placement failed: " + Objects.requireNonNull(reason, "reason");
    }
}
