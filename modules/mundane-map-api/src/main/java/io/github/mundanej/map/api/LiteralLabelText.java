package io.github.mundanej.map.api;

/**
 * Exact fixed text for every eligible point feature.
 *
 * @param text bounded non-blank single-line text
 */
public record LiteralLabelText(String text) implements LabelTextSource {
    /** Validates the fixed text. */
    public LiteralLabelText {
        PointLabelTexts.requireSupported(text);
    }
}
