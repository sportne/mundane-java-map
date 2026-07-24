package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Toolkit-neutral point-label color, weight, and logical-pixel size.
 *
 * @param color visible RGBA text color
 * @param weight supported logical font weight
 * @param sizePixels logical screen-pixel font size from 1 through 512
 */
public record LabelTextStyle(Rgba color, LabelWeight weight, double sizePixels) {
    /** Validates the bounded visible text style. */
    public LabelTextStyle {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(weight, "weight");
        if (color.alpha() == 0) {
            throw new IllegalArgumentException("color must have positive alpha");
        }
        if (!Double.isFinite(sizePixels) || sizePixels < 1.0 || sizePixels > 512.0) {
            throw new IllegalArgumentException("sizePixels must be finite and between 1 and 512");
        }
    }
}
