package io.github.mundanej.map.io.maplibre.style;

import java.util.OptionalDouble;

/**
 * Retained, unapplied MapLibre camera metadata.
 *
 * @param longitude optional center longitude
 * @param latitude optional center latitude; present exactly when longitude is present
 * @param zoom optional initial zoom
 * @param bearing optional bearing in degrees
 * @param pitch optional pitch in degrees
 */
public record MapLibreCamera(
        OptionalDouble longitude,
        OptionalDouble latitude,
        OptionalDouble zoom,
        OptionalDouble bearing,
        OptionalDouble pitch) {
    /** Empty camera metadata. */
    public static final MapLibreCamera EMPTY =
            new MapLibreCamera(
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty(),
                    OptionalDouble.empty());

    /** Validates optional finite camera values. */
    public MapLibreCamera {
        requireOptional(longitude, -180.0, 180.0, "longitude");
        requireOptional(latitude, -90.0, 90.0, "latitude");
        requireOptional(zoom, 0.0, 24.0, "zoom");
        requireFinite(bearing, "bearing");
        requireOptional(pitch, 0.0, 180.0, "pitch");
        if (longitude.isPresent() != latitude.isPresent()) {
            throw new IllegalArgumentException("longitude and latitude must be present together");
        }
    }

    private static void requireOptional(
            OptionalDouble value, double minimum, double maximum, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        value.ifPresent(
                number -> {
                    if (!Double.isFinite(number) || number < minimum || number > maximum) {
                        throw new IllegalArgumentException(
                                name + " is outside its supported range");
                    }
                });
    }

    private static void requireFinite(OptionalDouble value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        value.ifPresent(
                number -> {
                    if (!Double.isFinite(number)) {
                        throw new IllegalArgumentException(name + " must be finite");
                    }
                });
    }
}
