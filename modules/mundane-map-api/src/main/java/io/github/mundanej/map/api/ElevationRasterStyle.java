package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable elevation colorization and optional hillshade style.
 *
 * @param colorRamp caller-selected numeric color ramp
 * @param noDataColor explicit color for structurally absent samples
 * @param hillshade optional bounded first-order hillshade settings
 */
public record ElevationRasterStyle(
        ElevationColorRamp colorRamp, Rgba noDataColor, Optional<ElevationHillshade> hillshade) {
    /** Validates non-null immutable style components. */
    public ElevationRasterStyle {
        Objects.requireNonNull(colorRamp, "colorRamp");
        Objects.requireNonNull(noDataColor, "noDataColor");
        Objects.requireNonNull(hillshade, "hillshade");
    }

    /**
     * Creates a style with transparent no-data and hillshade disabled.
     *
     * @param ramp caller-selected numeric color ramp
     * @return immutable base style
     */
    public static ElevationRasterStyle of(ElevationColorRamp ramp) {
        return new ElevationRasterStyle(
                Objects.requireNonNull(ramp, "ramp"), Rgba.TRANSPARENT, Optional.empty());
    }

    /**
     * Returns a copy using the requested no-data color.
     *
     * @param color explicit no-data color
     * @return immutable updated style
     */
    public ElevationRasterStyle withNoDataColor(Rgba color) {
        return new ElevationRasterStyle(
                colorRamp, Objects.requireNonNull(color, "color"), hillshade);
    }

    /**
     * Returns a copy with hillshading enabled using the requested settings.
     *
     * @param value bounded hillshade settings
     * @return immutable updated style
     */
    public ElevationRasterStyle withHillshade(ElevationHillshade value) {
        return new ElevationRasterStyle(
                colorRamp, noDataColor, Optional.of(Objects.requireNonNull(value, "value")));
    }

    /**
     * Returns a copy with hillshading disabled.
     *
     * @return immutable updated style
     */
    public ElevationRasterStyle withoutHillshade() {
        return new ElevationRasterStyle(colorRamp, noDataColor, Optional.empty());
    }
}
