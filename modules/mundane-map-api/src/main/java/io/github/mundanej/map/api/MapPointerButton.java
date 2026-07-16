package io.github.mundanej.map.api;

/**
 * Toolkit-neutral pointer-button identity.
 *
 * @param number non-negative toolkit-neutral button number; zero means no changed button
 */
public record MapPointerButton(int number) {
    /** No changed button. */
    public static final MapPointerButton NONE = new MapPointerButton(0);

    /** Primary pointer button. */
    public static final MapPointerButton PRIMARY = new MapPointerButton(1);

    /** Middle pointer button. */
    public static final MapPointerButton MIDDLE = new MapPointerButton(2);

    /** Secondary pointer button. */
    public static final MapPointerButton SECONDARY = new MapPointerButton(3);

    /** Validates the non-negative button number. */
    public MapPointerButton {
        if (number < 0) {
            throw new IllegalArgumentException("Pointer button number must not be negative");
        }
    }
}
