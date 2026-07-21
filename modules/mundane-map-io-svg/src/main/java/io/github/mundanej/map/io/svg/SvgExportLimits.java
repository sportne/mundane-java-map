package io.github.mundanej.map.io.svg;

/**
 * Immutable hard-bounded limits for canonical SVG map serialization.
 *
 * @param maximumElements maximum emitted SVG elements
 * @param maximumPathCommands maximum emitted path commands
 * @param maximumHatchSegments maximum aggregate hatch candidates
 * @param maximumOutputBytes maximum encoded UTF-8 bytes
 * @param maximumOwnedBytes maximum deterministic writer-owned-byte inventory
 */
public record SvgExportLimits(
        int maximumElements,
        int maximumPathCommands,
        int maximumHatchSegments,
        int maximumOutputBytes,
        long maximumOwnedBytes) {
    /** Hard maximum for emitted elements. */
    public static final int ELEMENTS_HARD_MAXIMUM = 1_000_000;

    /** Hard maximum for emitted path commands. */
    public static final int PATH_COMMANDS_HARD_MAXIMUM = 10_000_000;

    /** Hard maximum for hatch candidates. */
    public static final int HATCH_SEGMENTS_HARD_MAXIMUM = 1_000_000;

    /** Hard maximum for encoded output bytes. */
    public static final int OUTPUT_BYTES_HARD_MAXIMUM = 67_108_864;

    /** Hard maximum for deterministic writer-owned bytes. */
    public static final long OWNED_BYTES_HARD_MAXIMUM = 268_435_456L;

    /** Validates every positive limit against its hard maximum. */
    public SvgExportLimits {
        require(maximumElements, ELEMENTS_HARD_MAXIMUM, "maximumElements");
        require(maximumPathCommands, PATH_COMMANDS_HARD_MAXIMUM, "maximumPathCommands");
        require(maximumHatchSegments, HATCH_SEGMENTS_HARD_MAXIMUM, "maximumHatchSegments");
        require(maximumOutputBytes, OUTPUT_BYTES_HARD_MAXIMUM, "maximumOutputBytes");
        if (maximumOwnedBytes <= 0 || maximumOwnedBytes > OWNED_BYTES_HARD_MAXIMUM) {
            throw new IllegalArgumentException(
                    "maximumOwnedBytes must be positive and at most " + OWNED_BYTES_HARD_MAXIMUM);
        }
    }

    /**
     * Returns the Level 1 defaults, which are also the hard maxima.
     *
     * @return immutable default limits
     */
    public static SvgExportLimits defaults() {
        return new SvgExportLimits(
                ELEMENTS_HARD_MAXIMUM,
                PATH_COMMANDS_HARD_MAXIMUM,
                HATCH_SEGMENTS_HARD_MAXIMUM,
                OUTPUT_BYTES_HARD_MAXIMUM,
                OWNED_BYTES_HARD_MAXIMUM);
    }

    /**
     * Returns a copy with a different element maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public SvgExportLimits withMaximumElements(int value) {
        return new SvgExportLimits(
                value,
                maximumPathCommands,
                maximumHatchSegments,
                maximumOutputBytes,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different path-command maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public SvgExportLimits withMaximumPathCommands(int value) {
        return new SvgExportLimits(
                maximumElements,
                value,
                maximumHatchSegments,
                maximumOutputBytes,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different hatch-candidate maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public SvgExportLimits withMaximumHatchSegments(int value) {
        return new SvgExportLimits(
                maximumElements, maximumPathCommands, value, maximumOutputBytes, maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different output-byte maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public SvgExportLimits withMaximumOutputBytes(int value) {
        return new SvgExportLimits(
                maximumElements,
                maximumPathCommands,
                maximumHatchSegments,
                value,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different deterministic owned-byte maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public SvgExportLimits withMaximumOwnedBytes(long value) {
        return new SvgExportLimits(
                maximumElements,
                maximumPathCommands,
                maximumHatchSegments,
                maximumOutputBytes,
                value);
    }

    private static void require(int value, int hardMaximum, String name) {
        if (value <= 0 || value > hardMaximum) {
            throw new IllegalArgumentException(
                    name + " must be positive and at most " + hardMaximum);
        }
    }
}
