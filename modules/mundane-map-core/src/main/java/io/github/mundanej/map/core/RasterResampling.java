package io.github.mundanej.map.core;

import io.github.mundanej.map.api.RasterInterpolation;
import java.util.Objects;

/** Exact toolkit-neutral raster sampling math. */
public final class RasterResampling {
    private RasterResampling() {}

    /**
     * Validates all axis, tap-count, and worst-case accumulation arithmetic before allocation.
     *
     * @param sourceWidth positive strict-window width
     * @param sourceHeight positive strict-window height
     * @param outputWidth positive output width
     * @param outputHeight positive output height
     * @param interpolation requested sampling mode
     * @throws ArithmeticException if exact work or accumulation arithmetic would overflow
     */
    public static void validatePlan(
            int sourceWidth,
            int sourceHeight,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation) {
        Objects.requireNonNull(interpolation, "interpolation");
        requirePositive(sourceWidth, "sourceWidth");
        requirePositive(sourceHeight, "sourceHeight");
        requirePositive(outputWidth, "outputWidth");
        requirePositive(outputHeight, "outputHeight");
        long outputPixels = Math.multiplyExact((long) outputWidth, outputHeight);
        nearestIndex(outputWidth - 1, sourceWidth, outputWidth);
        nearestIndex(outputHeight - 1, sourceHeight, outputHeight);
        if (interpolation == RasterInterpolation.NEAREST) {
            return;
        }
        long tapCount = Math.multiplyExact(outputPixels, 4L);
        AxisWeights x = bilinearAxis(outputWidth - 1, sourceWidth, outputWidth);
        AxisWeights y = bilinearAxis(outputHeight - 1, sourceHeight, outputHeight);
        long totalWeight = Math.multiplyExact(x.denominator(), y.denominator());
        long alphaNumerator = Math.multiplyExact(totalWeight, 255L);
        long roundedAlphaNumerator = Math.addExact(alphaNumerator, totalWeight / 2L);
        long channelNumerator = Math.multiplyExact(alphaNumerator, 255L);
        long roundedChannelNumerator = Math.addExact(channelNumerator, alphaNumerator / 2L);
        if (tapCount < outputPixels
                || roundedAlphaNumerator < alphaNumerator
                || roundedChannelNumerator < channelNumerator) {
            throw new ArithmeticException("Raster resampling work overflow");
        }
    }

    /**
     * Returns the source-cell index containing an output pixel center.
     *
     * @param outputIndex zero-based output index
     * @param sourceSize positive source-axis size
     * @param outputSize positive output-axis size
     * @return zero-based nearest source index, with exact half-cell ties rounded upward
     */
    public static int nearestIndex(int outputIndex, int sourceSize, int outputSize) {
        requireAxis(outputIndex, sourceSize, outputSize);
        long doubled = Math.addExact(Math.multiplyExact(2L, outputIndex), 1L);
        long numerator = Math.multiplyExact(doubled, sourceSize);
        long denominator = Math.multiplyExact(2L, outputSize);
        return Math.toIntExact(numerator / denominator);
    }

    /**
     * Returns exact window-local bilinear indexes and weights for one output pixel center.
     *
     * @param outputIndex zero-based output index
     * @param sourceSize positive source-axis size
     * @param outputSize positive output-axis size
     * @return immutable exact axis weights
     */
    public static AxisWeights bilinearAxis(int outputIndex, int sourceSize, int outputSize) {
        requireAxis(outputIndex, sourceSize, outputSize);
        long denominator = Math.multiplyExact(2L, outputSize);
        long numerator =
                Math.subtractExact(
                        Math.multiplyExact(
                                Math.addExact(Math.multiplyExact(2L, outputIndex), 1L), sourceSize),
                        outputSize);
        if (sourceSize == 1 || numerator <= 0) {
            return new AxisWeights(0, 0, denominator, 0, denominator);
        }
        long finalCenter = Math.multiplyExact((long) sourceSize - 1L, denominator);
        if (numerator >= finalCenter) {
            int finalIndex = sourceSize - 1;
            return new AxisWeights(finalIndex, finalIndex, denominator, 0, denominator);
        }
        int lower = Math.toIntExact(Math.floorDiv(numerator, denominator));
        long upperWeight = Math.floorMod(numerator, denominator);
        return new AxisWeights(
                lower,
                Math.addExact(lower, 1),
                Math.subtractExact(denominator, upperWeight),
                upperWeight,
                denominator);
    }

    /**
     * Blends four unpremultiplied {@code 0xRRGGBBAA} samples with exact axis weights.
     *
     * @param northWest upper-left sample
     * @param northEast upper-right sample
     * @param southWest lower-left sample
     * @param southEast lower-right sample
     * @param xWeights horizontal weights
     * @param yWeights vertical weights
     * @return one unpremultiplied packed RGBA sample
     */
    public static int bilinearRgba(
            int northWest,
            int northEast,
            int southWest,
            int southEast,
            AxisWeights xWeights,
            AxisWeights yWeights) {
        if (xWeights == null) {
            throw new NullPointerException("xWeights");
        }
        if (yWeights == null) {
            throw new NullPointerException("yWeights");
        }
        long northWestWeight = Math.multiplyExact(xWeights.lowerWeight(), yWeights.lowerWeight());
        long northEastWeight = Math.multiplyExact(xWeights.upperWeight(), yWeights.lowerWeight());
        long southWestWeight = Math.multiplyExact(xWeights.lowerWeight(), yWeights.upperWeight());
        long southEastWeight = Math.multiplyExact(xWeights.upperWeight(), yWeights.upperWeight());
        long totalWeight = Math.multiplyExact(xWeights.denominator(), yWeights.denominator());
        long alphaNumerator =
                weightedSum(
                        northWestWeight,
                        alpha(northWest),
                        northEastWeight,
                        alpha(northEast),
                        southWestWeight,
                        alpha(southWest),
                        southEastWeight,
                        alpha(southEast));
        int outputAlpha = roundHalfUp(alphaNumerator, totalWeight);
        if (alphaNumerator == 0 || outputAlpha == 0) {
            return 0;
        }
        int red =
                premultipliedChannel(
                        northWestWeight,
                        northWest,
                        24,
                        northEastWeight,
                        northEast,
                        24,
                        southWestWeight,
                        southWest,
                        24,
                        southEastWeight,
                        southEast,
                        24,
                        alphaNumerator);
        int green =
                premultipliedChannel(
                        northWestWeight,
                        northWest,
                        16,
                        northEastWeight,
                        northEast,
                        16,
                        southWestWeight,
                        southWest,
                        16,
                        southEastWeight,
                        southEast,
                        16,
                        alphaNumerator);
        int blue =
                premultipliedChannel(
                        northWestWeight,
                        northWest,
                        8,
                        northEastWeight,
                        northEast,
                        8,
                        southWestWeight,
                        southWest,
                        8,
                        southEastWeight,
                        southEast,
                        8,
                        alphaNumerator);
        return (red << 24) | (green << 16) | (blue << 8) | outputAlpha;
    }

    private static int premultipliedChannel(
            long weight0,
            int sample0,
            int shift0,
            long weight1,
            int sample1,
            int shift1,
            long weight2,
            int sample2,
            int shift2,
            long weight3,
            int sample3,
            int shift3,
            long alphaNumerator) {
        long numerator =
                weightedPremultipliedSum(
                        weight0,
                        alpha(sample0),
                        channel(sample0, shift0),
                        weight1,
                        alpha(sample1),
                        channel(sample1, shift1),
                        weight2,
                        alpha(sample2),
                        channel(sample2, shift2),
                        weight3,
                        alpha(sample3),
                        channel(sample3, shift3));
        return roundHalfUp(numerator, alphaNumerator);
    }

    private static long weightedSum(
            long weight0,
            int value0,
            long weight1,
            int value1,
            long weight2,
            int value2,
            long weight3,
            int value3) {
        long result = Math.multiplyExact(weight0, value0);
        result = Math.addExact(result, Math.multiplyExact(weight1, value1));
        result = Math.addExact(result, Math.multiplyExact(weight2, value2));
        return Math.addExact(result, Math.multiplyExact(weight3, value3));
    }

    private static long weightedPremultipliedSum(
            long weight0,
            int alpha0,
            int value0,
            long weight1,
            int alpha1,
            int value1,
            long weight2,
            int alpha2,
            int value2,
            long weight3,
            int alpha3,
            int value3) {
        long result = weightedPremultiplied(weight0, alpha0, value0);
        result = Math.addExact(result, weightedPremultiplied(weight1, alpha1, value1));
        result = Math.addExact(result, weightedPremultiplied(weight2, alpha2, value2));
        return Math.addExact(result, weightedPremultiplied(weight3, alpha3, value3));
    }

    private static long weightedPremultiplied(long weight, int alpha, int value) {
        return Math.multiplyExact(Math.multiplyExact(weight, alpha), value);
    }

    private static int roundHalfUp(long numerator, long denominator) {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Rounding values must be non-negative and positive");
        }
        long rounded = Math.addExact(numerator, denominator / 2) / denominator;
        return Math.toIntExact(Math.min(255, rounded));
    }

    private static int alpha(int rgba) {
        return rgba & 0xff;
    }

    private static int channel(int rgba, int shift) {
        return (rgba >>> shift) & 0xff;
    }

    private static void requireAxis(int outputIndex, int sourceSize, int outputSize) {
        if (sourceSize <= 0) {
            throw new IllegalArgumentException("sourceSize must be positive");
        }
        if (outputSize <= 0) {
            throw new IllegalArgumentException("outputSize must be positive");
        }
        if (outputIndex < 0 || outputIndex >= outputSize) {
            throw new IndexOutOfBoundsException("outputIndex is outside the output axis");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /**
     * Exact indexes and weights for one bilinear axis.
     *
     * @param lowerIndex lower source-cell index
     * @param upperIndex upper source-cell index
     * @param lowerWeight non-negative lower-cell weight
     * @param upperWeight non-negative upper-cell weight
     * @param denominator positive common denominator
     */
    public record AxisWeights(
            int lowerIndex, int upperIndex, long lowerWeight, long upperWeight, long denominator) {
        /** Validates indexes and exact non-negative weights. */
        public AxisWeights {
            if (lowerIndex < 0 || upperIndex < lowerIndex) {
                throw new IllegalArgumentException("Axis indexes must be ordered and non-negative");
            }
            if (lowerWeight < 0 || upperWeight < 0 || denominator <= 0) {
                throw new IllegalArgumentException("Axis weights must be non-negative");
            }
            if (Math.addExact(lowerWeight, upperWeight) != denominator) {
                throw new IllegalArgumentException("Axis weights must sum to the denominator");
            }
        }
    }
}
