package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Objects;

/** Static explicit-construction facade for bounded JDK HTTP XYZ acquisition. */
public final class HttpXyzTiles {
    private HttpXyzTiles() {}

    /**
     * Opens one owned client without making a network request.
     *
     * @param identity logical non-locator source identity
     * @param template strict fixed-host XYZ template
     * @param options immutable client policy and limits
     * @param decoders explicitly registered PNG/JPEG decoders
     * @return caller-owned unopened-network client
     */
    public static HttpXyzTileClient open(
            SourceIdentity identity,
            HttpXyzTemplate template,
            HttpXyzClientOptions options,
            EncodedRasterDecoderRegistry decoders) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        if (template.length() > options.limits().templateCharacters()) {
            throw new IllegalArgumentException("XYZ template exceeds its configured limit");
        }
        if (template.scheme().equals("http")
                && options.schemePolicy() != HttpSchemePolicy.HTTPS_OR_HTTP) {
            throw new IllegalArgumentException("Cleartext HTTP requires explicit opt-in");
        }
        if (options.cachePolicy() != HttpTileCachePolicy.DISABLED) {
            throw new IllegalArgumentException("Memory caching is not available in this slice");
        }
        if (options.decodeOptions().expectedFormat().isPresent()
                || options.decodeOptions().expectedWidth().isPresent()) {
            throw new IllegalArgumentException(
                    "HTTP tile decoding derives format and dimensions from the response");
        }
        validateCompatibility(options);
        return DefaultHttpXyzTileClient.open(identity, template, options, decoders);
    }

    private static void validateCompatibility(HttpXyzClientOptions options) {
        final long pixels = 256L * 256L;
        long body = options.limits().responseBodyBytes();
        var image = options.decodeOptions().imageLimits();
        var decode = options.decodeOptions().decodeLimits();
        var snapshot = options.snapshotLimits().requestLimits();
        long decodeIntermediate;
        long requestReservation;
        long ownedFloor;
        try {
            decodeIntermediate = Math.addExact(Math.multiplyExact(2L, body), 12L * pixels);
            long segmentSlots = Math.multiplyExact(8L, (body + 65_535L) / 65_536L);
            requestReservation =
                    Math.addExact(
                            Math.addExact(Math.multiplyExact(4L, body), 16L * pixels),
                            segmentSlots);
            ownedFloor = Math.addExact(Math.addExact(4L * pixels, 16L), requestReservation);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "HTTP tile limits overflow compatibility accounting");
        }
        if (body > image.maximumEncodedBytes()
                || image.maximumWidth() < 256
                || image.maximumHeight() < 256
                || image.maximumPixels() < pixels
                || image.maximumInflatedRasterBytes() < 4L * pixels + 256L
                || decode.sourceWindowPixels() < pixels
                || decode.outputDimension() < 256
                || decode.outputPixels() < pixels
                || decode.decodedIntermediateBytes() < decodeIntermediate
                || decode.ownedPayloadBytes() < 4L * pixels
                || snapshot.sourceWindowPixels() < pixels
                || snapshot.outputDimension() < 256
                || snapshot.outputPixels() < pixels
                || snapshot.decodedIntermediateBytes() < 4L * pixels
                || snapshot.ownedPayloadBytes() < 4L * pixels
                || options.limits().ownedBytes() < ownedFloor) {
            throw new IllegalArgumentException(
                    "HTTP tile limits do not admit one complete 256 by 256 acquisition");
        }
    }
}
