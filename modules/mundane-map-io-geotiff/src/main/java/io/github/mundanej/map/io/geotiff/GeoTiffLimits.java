package io.github.mundanej.map.io.geotiff;

/**
 * Immutable resource ceilings for one GeoTIFF open or read transaction.
 *
 * @param maximumInputBytes input snapshot byte ceiling
 * @param maximumDimension width-or-height ceiling
 * @param maximumPixels declared pixel ceiling
 * @param maximumIfdEntries IFD entry ceiling
 * @param maximumGeoKeys GeoKey ceiling
 * @param maximumSegments image segment ceiling
 * @param maximumEncodedSegmentBytes encoded-segment byte ceiling
 * @param maximumDecodedSegmentBytes decoded-segment byte ceiling
 * @param maximumTagPayloadBytes out-of-line tag-payload byte ceiling
 * @param maximumGeoAsciiBytes GeoASCII byte ceiling
 * @param maximumNoDataBytes no-data field byte ceiling
 * @param maximumWorkingBytes format-working byte ceiling
 */
public record GeoTiffLimits(
        long maximumInputBytes,
        int maximumDimension,
        long maximumPixels,
        int maximumIfdEntries,
        int maximumGeoKeys,
        int maximumSegments,
        long maximumEncodedSegmentBytes,
        long maximumDecodedSegmentBytes,
        long maximumTagPayloadBytes,
        int maximumGeoAsciiBytes,
        int maximumNoDataBytes,
        long maximumWorkingBytes) {
    private static final GeoTiffLimits DEFAULTS =
            new GeoTiffLimits(
                    268_435_456L,
                    65_536,
                    268_435_456L,
                    512,
                    128,
                    262_144,
                    67_108_864L,
                    67_108_864L,
                    67_108_864L,
                    65_536,
                    128,
                    268_435_456L);

    /** Validates positive reachable limits. */
    public GeoTiffLimits {
        positive(maximumInputBytes, "maximumInputBytes");
        positive(maximumDimension, "maximumDimension");
        positive(maximumPixels, "maximumPixels");
        positive(maximumIfdEntries, "maximumIfdEntries");
        positive(maximumGeoKeys, "maximumGeoKeys");
        positive(maximumSegments, "maximumSegments");
        positive(maximumEncodedSegmentBytes, "maximumEncodedSegmentBytes");
        positive(maximumDecodedSegmentBytes, "maximumDecodedSegmentBytes");
        positive(maximumTagPayloadBytes, "maximumTagPayloadBytes");
        if (maximumGeoAsciiBytes < 2 || maximumNoDataBytes < 2) {
            throw new IllegalArgumentException("GeoASCII and no-data limits must be at least two");
        }
        positive(maximumWorkingBytes, "maximumWorkingBytes");
        maximum(maximumInputBytes, 1_073_741_824L, "maximumInputBytes");
        maximum(maximumDimension, 131_072, "maximumDimension");
        maximum(maximumPixels, Integer.MAX_VALUE, "maximumPixels");
        maximum(maximumIfdEntries, 4_096, "maximumIfdEntries");
        maximum(maximumGeoKeys, 1_024, "maximumGeoKeys");
        maximum(maximumSegments, 1_048_576, "maximumSegments");
        maximum(maximumEncodedSegmentBytes, 268_435_456L, "maximumEncodedSegmentBytes");
        maximum(maximumDecodedSegmentBytes, 268_435_456L, "maximumDecodedSegmentBytes");
        maximum(maximumTagPayloadBytes, 268_435_456L, "maximumTagPayloadBytes");
        maximum(maximumGeoAsciiBytes, 1_048_576, "maximumGeoAsciiBytes");
        maximum(maximumNoDataBytes, 1_024, "maximumNoDataBytes");
        maximum(maximumWorkingBytes, 1_073_741_824L, "maximumWorkingBytes");
        if (maximumEncodedSegmentBytes > maximumInputBytes
                || maximumTagPayloadBytes > maximumInputBytes
                || maximumDecodedSegmentBytes > maximumWorkingBytes
                || maximumGeoAsciiBytes > maximumTagPayloadBytes
                || maximumNoDataBytes > maximumTagPayloadBytes) {
            throw new IllegalArgumentException("GeoTIFF limit relationships are unreachable");
        }
    }

    /**
     * Returns the shared conservative defaults.
     *
     * @return immutable defaults
     */
    public static GeoTiffLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a copy with the requested input snapshot byte ceiling.
     *
     * @param value positive byte ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumInputBytes(long value) {
        return copy(
                value,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested width-or-height ceiling.
     *
     * @param value positive dimension ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumDimension(int value) {
        return copy(
                maximumInputBytes,
                value,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested declared pixel ceiling.
     *
     * @param value positive pixel ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumPixels(long value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                value,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested IFD entry ceiling.
     *
     * @param value positive entry ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumIfdEntries(int value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                value,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested GeoKey ceiling.
     *
     * @param value positive key ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumGeoKeys(int value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                value,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested image-segment ceiling.
     *
     * @param value positive segment ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumSegments(int value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                value,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested encoded-segment byte ceiling.
     *
     * @param value positive byte ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumEncodedSegmentBytes(long value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                value,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested decoded-segment byte ceiling.
     *
     * @param value positive byte ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumDecodedSegmentBytes(long value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                value,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested out-of-line tag payload ceiling.
     *
     * @param value positive byte ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumTagPayloadBytes(long value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                value,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested GeoASCII byte ceiling.
     *
     * @param value byte ceiling of at least two
     * @return updated limits
     */
    public GeoTiffLimits withMaximumGeoAsciiBytes(int value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                value,
                maximumNoDataBytes,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested no-data field byte ceiling.
     *
     * @param value byte ceiling of at least two
     * @return updated limits
     */
    public GeoTiffLimits withMaximumNoDataBytes(int value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                value,
                maximumWorkingBytes);
    }

    /**
     * Returns a copy with the requested format-working byte ceiling.
     *
     * @param value positive byte ceiling
     * @return updated limits
     */
    public GeoTiffLimits withMaximumWorkingBytes(long value) {
        return copy(
                maximumInputBytes,
                maximumDimension,
                maximumPixels,
                maximumIfdEntries,
                maximumGeoKeys,
                maximumSegments,
                maximumEncodedSegmentBytes,
                maximumDecodedSegmentBytes,
                maximumTagPayloadBytes,
                maximumGeoAsciiBytes,
                maximumNoDataBytes,
                value);
    }

    private static GeoTiffLimits copy(
            long input,
            int dimension,
            long pixels,
            int entries,
            int keys,
            int segments,
            long encoded,
            long decoded,
            long payload,
            int ascii,
            int noData,
            long working) {
        return new GeoTiffLimits(
                input, dimension, pixels, entries, keys, segments, encoded, decoded, payload, ascii,
                noData, working);
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void maximum(long value, long maximum, String name) {
        if (value > maximum) {
            throw new IllegalArgumentException(name + " exceeds the supported hard maximum");
        }
    }
}
