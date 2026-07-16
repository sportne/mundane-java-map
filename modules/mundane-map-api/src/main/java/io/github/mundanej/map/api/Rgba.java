package io.github.mundanej.map.api;

/**
 * An immutable red, green, blue, and alpha color using 8-bit channels.
 *
 * @param red red channel from 0 through 255
 * @param green green channel from 0 through 255
 * @param blue blue channel from 0 through 255
 * @param alpha alpha channel from 0 (transparent) through 255 (opaque)
 */
public record Rgba(int red, int green, int blue, int alpha) {
    /** Fully transparent black. */
    public static final Rgba TRANSPARENT = new Rgba(0, 0, 0, 0);

    /** Creates a color after validating channel ranges. */
    public Rgba {
        checkChannel(red, "red");
        checkChannel(green, "green");
        checkChannel(blue, "blue");
        checkChannel(alpha, "alpha");
    }

    /**
     * Creates an opaque RGB color.
     *
     * @param red red channel from 0 through 255
     * @param green green channel from 0 through 255
     * @param blue blue channel from 0 through 255
     * @return opaque color
     */
    public static Rgba rgb(int red, int green, int blue) {
        return new Rgba(red, green, blue, 255);
    }

    private static void checkChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " channel must be between 0 and 255");
        }
    }
}
