package io.github.mundanej.map.io.dted;

import io.github.mundanej.map.api.ElevationSourceLimits;
import java.util.Objects;

/** Immutable options captured for one synchronous DTED open transaction. */
public final class DtedOpenOptions {
    private static final DtedOpenOptions DEFAULTS =
            new DtedOpenOptions(ElevationSourceLimits.DEFAULTS);

    private final ElevationSourceLimits elevationSourceLimits;

    private DtedOpenOptions(ElevationSourceLimits elevationSourceLimits) {
        this.elevationSourceLimits =
                Objects.requireNonNull(elevationSourceLimits, "elevationSourceLimits");
    }

    /**
     * Returns the shared immutable defaults.
     *
     * @return default open options
     */
    public static DtedOpenOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the effective eager elevation-source limits.
     *
     * @return immutable source limits
     */
    public ElevationSourceLimits elevationSourceLimits() {
        return elevationSourceLimits;
    }

    /**
     * Returns a copy using the requested eager elevation-source limits.
     *
     * @param limits effective source limits
     * @return immutable updated options
     */
    public DtedOpenOptions withElevationSourceLimits(ElevationSourceLimits limits) {
        return new DtedOpenOptions(Objects.requireNonNull(limits, "limits"));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof DtedOpenOptions options
                        && elevationSourceLimits.equals(options.elevationSourceLimits));
    }

    @Override
    public int hashCode() {
        return elevationSourceLimits.hashCode();
    }

    @Override
    public String toString() {
        return "DtedOpenOptions[elevationSourceLimits=" + elevationSourceLimits + "]";
    }
}
