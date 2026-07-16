package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;

/** An immutable bounded marker backed by row-major unpremultiplied {@code 0xRRGGBBAA} pixels. */
public final class RasterIconSymbol implements MarkerSymbol {
    /** Maximum icon width or height. */
    public static final int MAX_DIMENSION = 4_096;

    /** Maximum icon pixel count. */
    public static final int MAX_PIXELS = 4_194_304;

    /** The explicit built-in raster-icon renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.raster-icon");

    private final int width;
    private final int height;
    private final int[] rgbaPixels;
    private final MarkerPlacement placement;
    private final RasterInterpolation interpolation;
    private final double opacity;

    private RasterIconSymbol(
            int width,
            int height,
            int[] rgbaPixels,
            MarkerPlacement placement,
            RasterInterpolation interpolation,
            double opacity) {
        if (width <= 0 || width > MAX_DIMENSION) {
            throw new IllegalArgumentException("width must be between 1 and " + MAX_DIMENSION);
        }
        if (height <= 0 || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("height must be between 1 and " + MAX_DIMENSION);
        }
        long pixelCount = (long) width * height;
        if (pixelCount > MAX_PIXELS) {
            throw new IllegalArgumentException("icon pixel count exceeds " + MAX_PIXELS);
        }
        Objects.requireNonNull(rgbaPixels, "rgbaPixels");
        if (rgbaPixels.length != (int) pixelCount) {
            throw new IllegalArgumentException("rgbaPixels length must equal width times height");
        }
        this.placement = Objects.requireNonNull(placement, "placement");
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        this.width = width;
        this.height = height;
        this.rgbaPixels = rgbaPixels.clone();
        this.opacity = opacity == 0.0 ? 0.0 : opacity;
    }

    /**
     * Creates a raster marker with explicit placement and interpolation.
     *
     * @param width positive intrinsic pixel width
     * @param height positive intrinsic pixel height
     * @param rgbaPixels row-major unpremultiplied pixels, defensively copied
     * @param placement marker size, anchor, offset, and rotation
     * @param interpolation raster sampling mode
     * @param opacity finite opacity from zero through one
     * @return immutable raster icon
     */
    public static RasterIconSymbol of(
            int width,
            int height,
            int[] rgbaPixels,
            MarkerPlacement placement,
            RasterInterpolation interpolation,
            double opacity) {
        return new RasterIconSymbol(width, height, rgbaPixels, placement, interpolation, opacity);
    }

    /**
     * Creates a centered screen marker using one logical pixel per source pixel.
     *
     * @param width positive intrinsic pixel width
     * @param height positive intrinsic pixel height
     * @param rgbaPixels row-major unpremultiplied pixels, defensively copied
     * @param interpolation raster sampling mode
     * @param opacity finite opacity from zero through one
     * @return immutable centered raster icon
     */
    public static RasterIconSymbol nativeScreenSize(
            int width,
            int height,
            int[] rgbaPixels,
            RasterInterpolation interpolation,
            double opacity) {
        return of(width, height, rgbaPixels, centered(width, height), interpolation, opacity);
    }

    /**
     * Creates a centered proportional screen marker with the requested width.
     *
     * @param width positive intrinsic pixel width
     * @param height positive intrinsic pixel height
     * @param rgbaPixels row-major unpremultiplied pixels, defensively copied
     * @param widthPixels positive rendered width in logical screen pixels
     * @param interpolation raster sampling mode
     * @param opacity finite opacity from zero through one
     * @return immutable proportional raster icon
     */
    public static RasterIconSymbol screenWidth(
            int width,
            int height,
            int[] rgbaPixels,
            double widthPixels,
            RasterInterpolation interpolation,
            double opacity) {
        if (!Double.isFinite(widthPixels) || widthPixels <= 0.0) {
            throw new IllegalArgumentException("widthPixels must be finite and positive");
        }
        double heightPixels = widthPixels * ((double) height / width);
        if (!Double.isFinite(heightPixels) || heightPixels <= 0.0) {
            throw new IllegalArgumentException("proportional height must be finite and positive");
        }
        return of(
                width,
                height,
                rgbaPixels,
                centered(widthPixels, heightPixels),
                interpolation,
                opacity);
    }

    private static MarkerPlacement centered(double width, double height) {
        return new MarkerPlacement(
                new SymbolSize(width, height, SymbolUnit.SCREEN_PIXEL),
                SymbolAnchor.CENTER,
                0.0,
                0.0,
                0.0,
                SymbolRotationMode.SCREEN_RELATIVE);
    }

    /**
     * Returns the intrinsic pixel width.
     *
     * @return source pixel width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the intrinsic pixel height.
     *
     * @return source pixel height
     */
    public int height() {
        return height;
    }

    /**
     * Returns the packed pixel at an intrinsic coordinate.
     *
     * @param x zero-based intrinsic column
     * @param y zero-based intrinsic row
     * @return unpremultiplied {@code 0xRRGGBBAA} pixel
     */
    public int rgbaAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("pixel (" + x + ", " + y + ")");
        }
        return rgbaPixels[y * width + x];
    }

    /**
     * Returns a defensive row-major packed pixel copy.
     *
     * @return newly allocated unpremultiplied pixels
     */
    public int[] toRgbaArray() {
        return rgbaPixels.clone();
    }

    /**
     * Returns the marker placement.
     *
     * @return immutable placement
     */
    public MarkerPlacement placement() {
        return placement;
    }

    /**
     * Returns the raster interpolation mode.
     *
     * @return sampling mode
     */
    public RasterInterpolation interpolation() {
        return interpolation;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RasterIconSymbol icon
                && width == icon.width
                && height == icon.height
                && Arrays.equals(rgbaPixels, icon.rgbaPixels)
                && placement.equals(icon.placement)
                && interpolation == icon.interpolation
                && Double.compare(opacity, icon.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(width, height, placement, interpolation, opacity)
                + Arrays.hashCode(rgbaPixels);
    }

    @Override
    public String toString() {
        return "RasterIconSymbol{width="
                + width
                + ", height="
                + height
                + ", placement="
                + placement
                + ", interpolation="
                + interpolation
                + ", opacity="
                + opacity
                + '}';
    }
}
