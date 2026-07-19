package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterPlacementException;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Static bounded PNG/JPEG raster-source facade.
 *
 * <p>Opening owns one exact local-file snapshot and publishes a source only after a second SHA-256
 * observation matches. A published source never adopts changed bytes: ordinary length, header, or
 * body mutation invalidates retained results; exact restoration may be read again, while accepting
 * a new version requires reopen. The observational guarantee assumes non-adversarial local files
 * and excludes same-length ABA rewrites deliberately racing one pass.
 *
 * <p>The returned source owns its channel. Its complete reads and close operation are serialized;
 * whichever acquires the source first completes first. Close clears retained decoded results and is
 * not a cancellation request for an in-progress opaque codec call.
 */
public final class RasterImages {
    private RasterImages() {}

    /**
     * Decodes one bounded PNG or JPEG byte array at its native dimensions.
     *
     * <p>The operation snapshots the caller array, uses only the explicitly supplied registry, and
     * returns toolkit-neutral independently owned pixels. It performs no file, suffix, placement,
     * cache, or source-lifecycle work.
     *
     * @param encodedBytes caller-owned encoded PNG or JPEG bytes
     * @param identity logical non-locator identity used in diagnostics
     * @param options immutable signature, dimension, and resource policy
     * @param decoders explicit decoder registry
     * @param cancellation synchronous cancellation signal
     * @return independently owned native-size RGBA pixels
     */
    public static RgbaPixelBuffer decode(
            byte[] encodedBytes,
            SourceIdentity identity,
            EncodedRasterDecodeOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        Objects.requireNonNull(encodedBytes, "encodedBytes");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cancellation, "cancellation");
        return EncodedRasterByteDecoder.decode(
                encodedBytes, identity, options, decoders, cancellation);
    }

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
        return open(
                path,
                identity,
                options,
                decoders,
                cancellation,
                opener,
                WorldFileSupport.fileSystemAccess(opener));
    }

    static RasterSource open(
            Path path,
            SourceIdentity identity,
            ImageOpenOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation,
            ImageChannel.Opener opener,
            WorldFileSupport.Access worldFiles) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(worldFiles, "worldFiles");
        String extension = extension(path);
        ImageChannel channel = null;
        Throwable primary = null;
        try {
            checkpoint(identity.id(), cancellation, "image-open");
            channel = opener.open(path);
            ImageHeader header =
                    ImageHeaderProbe.probe(
                            channel, extension, identity.id(), options.imageLimits(), cancellation);
            byte[] openSnapshot =
                    ImageSnapshots.exact(
                            channel,
                            header.encodedLength(),
                            identity.id(),
                            "openSnapshot",
                            cancellation);
            ImageSnapshots.requireHeader(openSnapshot, header, identity.id(), "openSnapshot");
            ImageContainerValidator.validate(
                    openSnapshot, header, options.imageLimits(), cancellation, identity.id());
            ImageContentVersion version = ImageSnapshots.version(openSnapshot);
            ImageContentVersion observed =
                    ImageSnapshots.fingerprint(
                            channel,
                            header.encodedLength(),
                            header,
                            identity.id(),
                            "openSnapshot",
                            cancellation);
            ImageSnapshots.requireVersion(version, observed, identity.id(), "openSnapshot");
            RasterSourceMetadata metadata =
                    resolveMetadata(
                            path, extension, identity, header, options, cancellation, worldFiles);
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
            long finalLength = channel.size();
            if (finalLength != header.encodedLength()) {
                throw ImageDiagnostics.failure(
                        identity.id(),
                        "IMAGE_FILE_LENGTH_MISMATCH",
                        "image",
                        "Encoded image length changed during open",
                        Map.of(
                                "capturedBytes", Long.toString(header.encodedLength()),
                                "actualBytes", Long.toString(finalLength),
                                "reason", "openSnapshot"));
            }
            checkpoint(identity.id(), cancellation, "image-open");
            ImageRasterSource source =
                    new ImageRasterSource(channel, header, metadata, options, decoder, version);
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

    private static RasterSourceMetadata resolveMetadata(
            Path path,
            String extension,
            SourceIdentity identity,
            ImageHeader header,
            ImageOpenOptions options,
            CancellationToken cancellation,
            WorldFileSupport.Access worldFiles) {
        ImagePlacement instruction = options.placement();
        if (instruction.kind() == ImagePlacement.Kind.UNPLACED) {
            return new RasterSourceMetadata(
                    identity,
                    header.width(),
                    header.height(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty());
        }
        RasterGridPlacement placement;
        if (instruction.kind() == ImagePlacement.Kind.AXIS_ALIGNED) {
            placement = RasterGridPlacement.axisAligned(instruction.mapBounds().orElseThrow());
        } else {
            placement =
                    WorldFileSupport.read(
                            path,
                            extension,
                            identity.id(),
                            options.imageLimits(),
                            cancellation,
                            worldFiles);
        }
        try {
            checkpoint(identity.id(), cancellation, "image-open");
            RasterSourceMetadata metadata =
                    RasterSourceMetadata.withPlacement(
                            identity,
                            header.width(),
                            header.height(),
                            placement,
                            instruction.crs());
            checkpoint(identity.id(), cancellation, "image-open");
            return metadata;
        } catch (RasterPlacementException failure) {
            if (instruction.kind() != ImagePlacement.Kind.WORLD_FILE) {
                throw failure;
            }
            throw ImageDiagnostics.worldFileTransform(identity.id(), failure);
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
