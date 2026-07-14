package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;

/** Immutable row-major unpremultiplied {@code 0xRRGGBBAA} pixel buffer. */
public final class RgbaPixelBuffer {
    private final int width;
    private final int height;
    private final int[] rgba;

    private RgbaPixelBuffer(int width, int height, int[] rgba, boolean transfer) {
        int length = requiredLength(width, height);
        if (rgba.length != length) {
            throw new IllegalArgumentException("Pixel array length must equal width times height");
        }
        this.width = width;
        this.height = height;
        this.rgba = transfer ? rgba : rgba.clone();
    }

    /** Creates a buffer by copying caller-owned pixels. */
    public static RgbaPixelBuffer copyOf(int width, int height, int[] rgba) {
        return new RgbaPixelBuffer(width, height, Objects.requireNonNull(rgba, "rgba"), false);
    }

    /** Creates a single-use producer builder. */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /** Returns the positive pixel width. */
    public int width() {
        return width;
    }

    /** Returns the positive pixel height. */
    public int height() {
        return height;
    }

    /** Returns one packed pixel. */
    public int rgbaAt(int column, int row) {
        if (column < 0 || column >= width || row < 0 || row >= height) {
            throw new IndexOutOfBoundsException("Pixel coordinate is outside the buffer");
        }
        return rgba[row * width + column];
    }

    /** Returns a defensive row-major pixel copy. */
    public int[] rgba() {
        return rgba.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RgbaPixelBuffer buffer
                && width == buffer.width
                && height == buffer.height
                && Arrays.equals(rgba, buffer.rgba);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(rgba);
    }

    @Override
    public String toString() {
        return "RgbaPixelBuffer[width=" + width + ", height=" + height + ']';
    }

    private static int requiredLength(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Pixel-buffer dimensions must be positive");
        }
        long pixels = Math.multiplyExact((long) width, height);
        if (pixels > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Pixel buffer exceeds Java array capacity");
        }
        return (int) pixels;
    }

    /** Single-use producer that transfers its array exactly once. */
    public static final class Builder {
        private final int width;
        private final int height;
        private int[] rgba;

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
            rgba = new int[requiredLength(width, height)];
        }

        /** Sets one packed pixel and returns this builder. */
        public Builder setRgba(int column, int row, int value) {
            requireOpen();
            if (column < 0 || column >= width || row < 0 || row >= height) {
                throw new IndexOutOfBoundsException("Pixel coordinate is outside the builder");
            }
            rgba[row * width + column] = value;
            return this;
        }

        /** Transfers the completed array to one immutable buffer. */
        public RgbaPixelBuffer build() {
            requireOpen();
            int[] transferred = rgba;
            rgba = null;
            return new RgbaPixelBuffer(width, height, transferred, true);
        }

        private void requireOpen() {
            if (rgba == null) {
                throw new IllegalStateException("Pixel-buffer builder has already transferred");
            }
        }
    }
}
