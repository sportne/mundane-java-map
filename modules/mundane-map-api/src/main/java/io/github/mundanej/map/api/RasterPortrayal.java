package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable neutral raster band selection and color-map portrayal.
 *
 * @param bands ordered zero-based source bands, from one through four entries
 * @param colorMap ordered color stops, empty for direct multiband color
 * @param colorMapMode color interpolation mode
 * @param fallback fallback color for unmapped or invalid samples
 * @param interpolation source sampling mode
 * @param opacity finite opacity from zero through one
 */
public record RasterPortrayal(
        List<Integer> bands,
        List<ColorStop> colorMap,
        ColorMapMode colorMapMode,
        Rgba fallback,
        RasterInterpolation interpolation,
        double opacity) {
    /** Raster color-map interpolation modes. */
    public enum ColorMapMode {
        /** Select the greatest stop not exceeding the sample. */
        INTERVALS,
        /** Select an exactly equal stop. */
        VALUES,
        /** Linearly interpolate neighboring stop colors. */
        RAMP
    }

    /**
     * Ordered raster color-map stop.
     *
     * @param value finite sample value
     * @param color mapped color
     * @param label optional bounded display label
     */
    public record ColorStop(double value, Rgba color, Optional<String> label) {
        /** Creates and validates one stop. */
        public ColorStop {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("color stop value must be finite");
            }
            Objects.requireNonNull(color, "color");
            label = Objects.requireNonNull(label, "label").map(Objects::requireNonNull);
            if (label.isPresent()
                    && (label.orElseThrow().isBlank() || label.orElseThrow().length() > 256)) {
                throw new IllegalArgumentException(
                        "color stop labels must be bounded and non-blank");
            }
        }
    }

    /** Creates and validates a raster portrayal. */
    public RasterPortrayal {
        bands = List.copyOf(Objects.requireNonNull(bands, "bands"));
        if (bands.isEmpty() || bands.size() > 4) {
            throw new IllegalArgumentException("bands must contain from one through four entries");
        }
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (Integer band : bands) {
            if (band == null || band < 0 || band > 255 || !seen.add(band)) {
                throw new IllegalArgumentException(
                        "bands must be distinct values from zero through 255");
            }
        }
        colorMap = List.copyOf(Objects.requireNonNull(colorMap, "colorMap"));
        if (colorMap.size() > 1_024) {
            throw new IllegalArgumentException("colorMap must contain at most 1024 stops");
        }
        double previous = Double.NEGATIVE_INFINITY;
        for (ColorStop stop : colorMap) {
            Objects.requireNonNull(stop, "color stop");
            if (stop.value() <= previous) {
                throw new IllegalArgumentException("colorMap stops must be strictly increasing");
            }
            previous = stop.value();
        }
        if (!colorMap.isEmpty() && bands.size() != 1) {
            throw new IllegalArgumentException("a color map requires exactly one source band");
        }
        Objects.requireNonNull(colorMapMode, "colorMapMode");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(interpolation, "interpolation");
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between zero and one");
        }
        opacity = opacity == 0.0 ? 0.0 : opacity;
    }
}
