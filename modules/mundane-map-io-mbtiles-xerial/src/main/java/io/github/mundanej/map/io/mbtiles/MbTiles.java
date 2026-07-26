package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Static entry points for the strict read-only MBTiles 1.3 raster profile. */
public final class MbTiles {
    private MbTiles() {}

    /**
     * Inspects one local MBTiles container and returns detached metadata.
     *
     * @param path caller-authorized local {@code .mbtiles} file
     * @param identity logical non-locator identity
     * @param options bounded inspection policy
     * @param cancellation synchronous cancellation signal
     * @return detached immutable metadata
     */
    public static MbTilesMetadata inspect(
            Path path,
            SourceIdentity identity,
            MbTilesInspectOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        validateIdentity(identity);
        MbTilesFailures.checkpoint(identity.id(), cancellation::isCancellationRequested, "open");
        MbTilesFile.Fingerprint fingerprint =
                MbTilesFile.preflight(identity.id(), path, options.limits(), cancellation);
        try (MbTilesSession session =
                MbTilesSession.open(identity.id(), fingerprint, options.limits(), cancellation)) {
            return MbTilesCatalogReader.read(identity.id(), session, cancellation).metadata();
        }
    }

    /**
     * Opens one populated zoom as an owned placed raster source.
     *
     * @param path caller-authorized local {@code .mbtiles} file
     * @param identity logical non-locator identity
     * @param zoom explicit populated zoom in {@code [0,22]}
     * @param options bounded source and cache policy
     * @param decoders explicit PNG/JPEG decoder registry
     * @param cancellation synchronous opening cancellation signal
     * @return owned raster source
     */
    public static RasterSource open(
            Path path,
            SourceIdentity identity,
            int zoom,
            MbTilesOpenOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cancellation, "cancellation");
        validateIdentity(identity);
        if (zoom < 0 || zoom > options.limits().maximumZoom()) {
            throw unsupported(identity.id(), "zoom");
        }
        MbTilesFailures.checkpoint(identity.id(), cancellation::isCancellationRequested, "open");
        MbTilesFile.Fingerprint fingerprint =
                MbTilesFile.preflight(identity.id(), path, options.limits(), cancellation);
        MbTilesSession session =
                MbTilesSession.open(identity.id(), fingerprint, options.limits(), cancellation);
        try {
            MbTilesCatalogReader.Snapshot snapshot =
                    MbTilesCatalogReader.read(identity.id(), session, cancellation);
            Map<Integer, MbTilesTileProfile> profiles = snapshot.profiles();
            MbTilesTileProfile profile = profiles.get(zoom);
            if (profile == null) {
                throw unsupported(identity.id(), "zoom");
            }
            return new MbTilesRasterSource(
                    identity,
                    session,
                    profile,
                    snapshot.metadata().openingDiagnostics(),
                    options,
                    decoders,
                    cancellation);
        } catch (RuntimeException | Error failure) {
            try {
                session.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static void validateIdentity(SourceIdentity identity) {
        if (identity.id().length() > 256 || identity.id().indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Source identity exceeds the MBTiles profile");
        }
    }

    private static io.github.mundanej.map.api.SourceException unsupported(
            String sourceId, String construct) {
        return MbTilesFailures.failure(
                sourceId,
                "MBTILES_PROFILE_UNSUPPORTED",
                "MBTiles construct is outside the supported profile",
                Map.of("construct", construct));
    }
}
