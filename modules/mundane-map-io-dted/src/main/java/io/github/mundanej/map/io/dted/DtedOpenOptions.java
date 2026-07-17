package io.github.mundanej.map.io.dted;

import io.github.mundanej.map.api.ElevationSourceLimits;
import java.util.Objects;

/** Immutable options captured for one synchronous DTED open transaction. */
public final class DtedOpenOptions {
    private static final DtedOpenOptions DEFAULTS =
            new DtedOpenOptions(ElevationSourceLimits.DEFAULTS, DtedLimits.defaults());

    private final ElevationSourceLimits elevationSourceLimits;
    private final DtedLimits dtedLimits;

    private DtedOpenOptions(ElevationSourceLimits elevationSourceLimits, DtedLimits dtedLimits) {
        this.elevationSourceLimits =
                Objects.requireNonNull(elevationSourceLimits, "elevationSourceLimits");
        this.dtedLimits = Objects.requireNonNull(dtedLimits, "dtedLimits");
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
        return new DtedOpenOptions(Objects.requireNonNull(limits, "limits"), dtedLimits);
    }

    /**
     * Returns the effective DTED parser limits.
     *
     * @return immutable DTED limits
     */
    public DtedLimits dtedLimits() {
        return dtedLimits;
    }

    /**
     * Returns a copy using the requested DTED parser limits.
     *
     * @param limits effective DTED limits
     * @return immutable updated options
     */
    public DtedOpenOptions withDtedLimits(DtedLimits limits) {
        return new DtedOpenOptions(elevationSourceLimits, Objects.requireNonNull(limits, "limits"));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof DtedOpenOptions options
                        && elevationSourceLimits.equals(options.elevationSourceLimits)
                        && dtedLimits.equals(options.dtedLimits));
    }

    @Override
    public int hashCode() {
        return 31 * elevationSourceLimits.hashCode() + dtedLimits.hashCode();
    }

    @Override
    public String toString() {
        return "DtedOpenOptions[elevationSourceLimits="
                + elevationSourceLimits
                + ", dtedLimits="
                + dtedLimits
                + "]";
    }
}
