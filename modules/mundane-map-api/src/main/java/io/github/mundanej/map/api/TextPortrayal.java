package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable neutral text portrayal with expression label, placement, halo, and fallback fonts.
 *
 * @param label label expression
 * @param fontFamilies ordered non-empty fallback font families
 * @param weight font weight from 1 through 1000
 * @param size positive text size
 * @param color text color
 * @param placement point or line placement
 * @param halo optional text halo
 * @param opacity finite opacity from zero through one
 */
public record TextPortrayal(
        PortrayalExpression label,
        List<String> fontFamilies,
        int weight,
        SymbolLength size,
        Rgba color,
        Placement placement,
        Optional<Halo> halo,
        double opacity) {
    /** Text placement modes. */
    public enum Mode {
        /** Anchor and displacement relative to a point. */
        POINT,
        /** Repeated or singular placement following a line. */
        LINE
    }

    /**
     * Neutral point/line text placement.
     *
     * @param mode placement mode
     * @param anchorX normalized horizontal anchor from zero through one
     * @param anchorY normalized vertical anchor from zero through one
     * @param displacementX finite horizontal displacement in text-size units
     * @param displacementY finite vertical displacement in text-size units
     * @param rotationDegrees finite clockwise rotation
     * @param repeatGap nonnegative repeat gap in text-size units
     * @param maximumAngleDelta finite line-following angle tolerance from zero through 180
     */
    public record Placement(
            Mode mode,
            double anchorX,
            double anchorY,
            double displacementX,
            double displacementY,
            double rotationDegrees,
            double repeatGap,
            double maximumAngleDelta) {
        /** Creates and validates text placement. */
        public Placement {
            Objects.requireNonNull(mode, "mode");
            if (!unitInterval(anchorX) || !unitInterval(anchorY)) {
                throw new IllegalArgumentException("anchors must be between zero and one");
            }
            if (!Double.isFinite(displacementX)
                    || !Double.isFinite(displacementY)
                    || !Double.isFinite(rotationDegrees)
                    || !Double.isFinite(repeatGap)
                    || repeatGap < 0
                    || !Double.isFinite(maximumAngleDelta)
                    || maximumAngleDelta < 0
                    || maximumAngleDelta > 180) {
                throw new IllegalArgumentException(
                        "text placement values are outside their bounds");
            }
        }

        private static boolean unitInterval(double value) {
            return Double.isFinite(value) && value >= 0 && value <= 1;
        }
    }

    /**
     * Text halo.
     *
     * @param color halo color
     * @param radius positive halo radius
     */
    public record Halo(Rgba color, SymbolLength radius) {
        /** Creates a halo. */
        public Halo {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(radius, "radius");
        }
    }

    /** Creates and validates a text portrayal. */
    public TextPortrayal {
        Objects.requireNonNull(label, "label");
        fontFamilies = List.copyOf(Objects.requireNonNull(fontFamilies, "fontFamilies"));
        if (fontFamilies.isEmpty() || fontFamilies.size() > 32) {
            throw new IllegalArgumentException(
                    "fontFamilies must contain from one through 32 names");
        }
        for (String family : fontFamilies) {
            if (family == null || family.isBlank() || family.length() > 256) {
                throw new IllegalArgumentException(
                        "font family names must be bounded and non-blank");
            }
        }
        if (weight < 1 || weight > 1000) {
            throw new IllegalArgumentException("weight must be from 1 through 1000");
        }
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(placement, "placement");
        halo = Objects.requireNonNull(halo, "halo").map(Objects::requireNonNull);
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between zero and one");
        }
        opacity = opacity == 0.0 ? 0.0 : opacity;
    }
}
