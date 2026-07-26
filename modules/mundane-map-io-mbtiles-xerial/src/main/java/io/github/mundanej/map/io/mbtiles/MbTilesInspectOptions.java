package io.github.mundanej.map.io.mbtiles;

import java.util.Objects;

/**
 * Immutable options for bounded MBTiles metadata inspection.
 *
 * @param limits container and metadata ceilings
 */
public record MbTilesInspectOptions(MbTilesLimits limits) {
    /** Validates the option graph. */
    public MbTilesInspectOptions {
        Objects.requireNonNull(limits, "limits");
    }

    /**
     * Returns conservative inspection defaults.
     *
     * @return default inspection options
     */
    public static MbTilesInspectOptions defaults() {
        return new MbTilesInspectOptions(MbTilesLimits.DEFAULTS);
    }
}
