package io.github.mundanej.map.api;

/**
 * Immutable deterministic first-order hillshade settings.
 *
 * @param azimuthDegrees light direction clockwise from north in {@code [0,360)}
 * @param altitudeDegrees light altitude in {@code (0,90]}
 * @param verticalExaggeration vertical exaggeration in {@code (0,100]}
 */
public record ElevationHillshade(
        double azimuthDegrees, double altitudeDegrees, double verticalExaggeration) {
    private static final ElevationHillshade DEFAULTS = new ElevationHillshade(315.0, 45.0, 1.0);

    /** Validates and canonicalizes the bounded finite settings. */
    public ElevationHillshade {
        if (!Double.isFinite(azimuthDegrees) || azimuthDegrees < 0.0 || azimuthDegrees >= 360.0) {
            throw new IllegalArgumentException("azimuthDegrees must be finite and in [0,360)");
        }
        if (!Double.isFinite(altitudeDegrees) || altitudeDegrees <= 0.0 || altitudeDegrees > 90.0) {
            throw new IllegalArgumentException("altitudeDegrees must be finite and in (0,90]");
        }
        if (!Double.isFinite(verticalExaggeration)
                || verticalExaggeration <= 0.0
                || verticalExaggeration > 100.0) {
            throw new IllegalArgumentException(
                    "verticalExaggeration must be finite and in (0,100]");
        }
        if (azimuthDegrees == 0.0) {
            azimuthDegrees = 0.0;
        }
    }

    /**
     * Returns the 315-degree azimuth, 45-degree altitude, unit-exaggeration defaults.
     *
     * @return shared immutable default settings
     */
    public static ElevationHillshade defaults() {
        return DEFAULTS;
    }
}
