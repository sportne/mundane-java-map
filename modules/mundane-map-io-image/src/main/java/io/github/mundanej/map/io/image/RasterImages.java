package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Static bounded PNG/JPEG raster-source facade. */
public final class RasterImages {
    private RasterImages() {}

    /**
     * Opens an image with a non-cancelling token.
     *
     * @param path PNG/JPEG file
     * @param identity logical non-locator identity
     * @param options immutable opening options
     * @param decoders explicit decoder registry
     * @return an owned-channel raster source
     */
    public static RasterSource open(
            Path path,
            SourceIdentity identity,
            ImageOpenOptions options,
            EncodedRasterDecoderRegistry decoders) {
        return open(path, identity, options, decoders, CancellationToken.none());
    }

    /**
     * Opens one owned-channel source transactionally.
     *
     * @param path PNG/JPEG file
     * @param identity logical non-locator identity
     * @param options immutable opening options
     * @param decoders explicit decoder registry
     * @param cancellation opening cancellation signal
     * @return an owned-channel raster source
     */
    @SuppressWarnings("Finally")
    public static RasterSource open(
            Path path,
            SourceIdentity identity,
            ImageOpenOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        return open(path, identity, options, decoders, cancellation, ImageChannel::open);
    }

    static RasterSource open(
            Path path,
            SourceIdentity identity,
            ImageOpenOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation,
            ImageChannel.Opener opener) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(opener, "opener");
        String extension = extension(path);
        ImageChannel channel = null;
        Throwable primary = null;
        try {
            checkpoint(identity.id(), cancellation, "image-open");
            channel = opener.open(path);
            ImageHeader header =
                    ImageHeaderProbe.probe(
                            channel, extension, identity.id(), options.imageLimits(), cancellation);
            EncodedRasterDecoder decoder =
                    decoders.find(header.format())
                            .orElseThrow(
                                    () ->
                                            ImageDiagnostics.failure(
                                                    identity.id(),
                                                    "IMAGE_DECODER_NOT_REGISTERED",
                                                    "decoder",
                                                    "No decoder is registered for the image format",
                                                    Map.of("format", header.format().name())));
            if (channel.size() != header.encodedLength()) {
                throw ImageDiagnostics.failure(
                        identity.id(),
                        "IMAGE_FILE_LENGTH_MISMATCH",
                        "image",
                        "Encoded image length changed during open",
                        Map.of(
                                "capturedBytes", Long.toString(header.encodedLength()),
                                "actualBytes", Long.toString(channel.size()),
                                "reason", "sizeChanged"));
            }
            checkpoint(identity.id(), cancellation, "image-open");
            ImageRasterSource source =
                    new ImageRasterSource(channel, header, identity, options, decoder);
            channel = null;
            return source;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } catch (IOException failure) {
            SourceException mapped =
                    ImageDiagnostics.failure(
                            identity.id(),
                            "IMAGE_IO_FAILED",
                            "image",
                            "Image open failed",
                            Map.of("operation", "open", "causeKind", "IOException"));
            primary = mapped;
            throw mapped;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    primary.addSuppressed(closeFailure);
                }
            }
        }
    }

    private static String extension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Image path must have a filename");
        }
        String name = fileName.toString();
        int separator = name.lastIndexOf('.');
        String extension =
                separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new IllegalArgumentException("Image filename must end in .png, .jpg, or .jpeg");
        }
        return extension;
    }

    private static void checkpoint(
            String sourceId, CancellationToken cancellation, String operation) {
        if (cancellation.isCancellationRequested()) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "image",
                    "Image operation was cancelled",
                    Map.of("operation", operation));
        }
    }
}
