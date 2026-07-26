package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;
import java.util.Objects;

/** Explicit entry points for the bounded read-only GeoPackage profile. */
public final class GeoPackages {
    private GeoPackages() {}

    /**
     * Inspects supported feature content into a detached immutable catalog.
     *
     * @param path authorized local GeoPackage path
     * @param identity stable diagnostic identity
     * @param options bounded inspection options
     * @param cancellation operation cancellation token
     * @return detached catalog
     */
    public static GeoPackageCatalog inspect(
            Path path,
            SourceIdentity identity,
            GeoPackageInspectOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        GeoPackageFailures.checkpoint(identity.id(), cancellation::isCancellationRequested, "open");
        GeoPackageFile.Fingerprint fingerprint =
                GeoPackageFile.preflight(identity.id(), path, options.limits(), cancellation);
        try (GeoPackageSession session =
                GeoPackageSession.open(
                        identity.id(), fingerprint, options.limits(), cancellation)) {
            return GeoPackageCatalogReader.read(identity.id(), session, cancellation).catalog();
        }
    }

    /**
     * Opens one exact supported feature table.
     *
     * @param path authorized local GeoPackage path
     * @param identity stable diagnostic identity
     * @param tableName exact catalog table name
     * @param options bounded source options
     * @param cancellation opening cancellation token
     * @return caller-owned feature source
     */
    public static FeatureSource openFeatures(
            Path path,
            SourceIdentity identity,
            String tableName,
            GeoPackageFeatureOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        if (tableName.isBlank()
                || tableName.indexOf('\0') >= 0
                || tableName.length() > options.limits().maximumIdentifierCharacters()) {
            throw new IllegalArgumentException("tableName must be a bounded non-blank identifier");
        }
        GeoPackageFailures.checkpoint(identity.id(), cancellation::isCancellationRequested, "open");
        GeoPackageFile.Fingerprint fingerprint =
                GeoPackageFile.preflight(identity.id(), path, options.limits(), cancellation);
        GeoPackageSession session =
                GeoPackageSession.open(identity.id(), fingerprint, options.limits(), cancellation);
        try {
            GeoPackageCatalogSnapshot snapshot =
                    GeoPackageCatalogReader.read(identity.id(), session, cancellation);
            GeoPackageTableProfile selected = snapshot.requireFeature(tableName, identity.id());
            return new GeoPackageFeatureSource(identity, session, selected, options);
        } catch (RuntimeException | Error failure) {
            try {
                session.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Opens one exact tile table at one explicit catalogued zoom.
     *
     * @param path authorized local GeoPackage path
     * @param identity stable diagnostic identity
     * @param tableName exact catalog table name
     * @param zoom explicit catalogued zoom
     * @param options bounded source and cache options
     * @param decoders explicit PNG/JPEG decoder registry
     * @param cancellation opening cancellation token
     * @return caller-owned raster source
     */
    public static RasterSource openTiles(
            Path path,
            SourceIdentity identity,
            String tableName,
            int zoom,
            GeoPackageTileOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(decoders, "decoders");
        Objects.requireNonNull(cancellation, "cancellation");
        if (tableName.isBlank()
                || tableName.indexOf('\0') >= 0
                || tableName.length() > options.limits().maximumIdentifierCharacters()) {
            throw new IllegalArgumentException("tableName must be a bounded non-blank identifier");
        }
        if (zoom < 0 || zoom > options.limits().maximumZoom()) {
            throw new IllegalArgumentException("zoom is outside configured limits");
        }
        GeoPackageFailures.checkpoint(identity.id(), cancellation::isCancellationRequested, "open");
        GeoPackageFile.Fingerprint fingerprint =
                GeoPackageFile.preflight(identity.id(), path, options.limits(), cancellation);
        GeoPackageSession session =
                GeoPackageSession.open(identity.id(), fingerprint, options.limits(), cancellation);
        try {
            GeoPackageCatalogSnapshot snapshot =
                    GeoPackageCatalogReader.read(identity.id(), session, cancellation);
            GeoPackageTileProfile selected = snapshot.requireTile(tableName, identity.id());
            return new GeoPackageTileSource(
                    identity, session, selected, zoom, options, decoders, cancellation);
        } catch (RuntimeException | Error failure) {
            try {
                session.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
