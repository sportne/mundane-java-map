package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;
import java.util.Objects;

/** Entry points for the strict dependency-free GeoTIFF profile. */
public final class GeoTiffFiles {
    private GeoTiffFiles() {}

    /**
     * Opens a local GeoTIFF with cancellation disabled.
     *
     * @param identity logical non-sensitive source identity
     * @param path local file
     * @param options immutable limits
     * @return open raster source
     */
    public static RasterSource openRaster(
            SourceIdentity identity, Path path, GeoTiffRasterOptions options) {
        return openRaster(identity, path, options, CancellationToken.none());
    }

    /**
     * Opens a local GeoTIFF through one bounded snapshot transaction.
     *
     * @param identity logical non-sensitive source identity
     * @param path local file
     * @param options immutable limits
     * @param cancellation operation-local cancellation token
     * @return open raster source
     */
    public static RasterSource openRaster(
            SourceIdentity identity,
            Path path,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        return GeoTiffParser.open(
                identity,
                GeoTiffSnapshots.read(identity, path, options.formatLimits(), cancellation),
                options,
                cancellation);
    }

    /**
     * Opens an owned defensive copy of encoded GeoTIFF bytes with cancellation disabled.
     *
     * @param identity logical non-sensitive source identity
     * @param encoded encoded GeoTIFF bytes
     * @param options immutable limits
     * @return open raster source
     */
    public static RasterSource openRaster(
            SourceIdentity identity, byte[] encoded, GeoTiffRasterOptions options) {
        return openRaster(identity, encoded, options, CancellationToken.none());
    }

    /**
     * Opens an owned defensive copy of encoded GeoTIFF bytes.
     *
     * @param identity logical non-sensitive source identity
     * @param encoded encoded GeoTIFF bytes
     * @param options immutable limits
     * @param cancellation operation-local cancellation token
     * @return open raster source
     */
    public static RasterSource openRaster(
            SourceIdentity identity,
            byte[] encoded,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        GeoTiffFailures.limit(
                identity.id(),
                "geoTiffOpen",
                "inputBytes",
                encoded.length,
                options.formatLimits().maximumInputBytes());
        byte[] snapshot = encoded.clone();
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        return GeoTiffParser.open(identity, snapshot, options, cancellation);
    }

    /**
     * Opens a local signed-integer PixelIsPoint GeoTIFF elevation grid.
     *
     * @param identity logical non-sensitive source identity
     * @param path local file snapshotted before parsing
     * @param options explicit elevation unit and immutable resource ceilings
     * @return caller-owned eager elevation source retaining no encoded TIFF state
     */
    public static ElevationSource openElevation(
            SourceIdentity identity, Path path, GeoTiffElevationOptions options) {
        return openElevation(identity, path, options, CancellationToken.none());
    }

    /**
     * Opens a local signed-integer PixelIsPoint GeoTIFF elevation grid with cancellation.
     *
     * @param identity logical non-sensitive source identity
     * @param path local file snapshotted before parsing
     * @param options explicit elevation unit and immutable resource ceilings
     * @param cancellation operation-local cooperative cancellation token
     * @return caller-owned eager elevation source retaining no encoded TIFF state
     */
    public static ElevationSource openElevation(
            SourceIdentity identity,
            Path path,
            GeoTiffElevationOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        return GeoTiffParser.openElevation(
                identity,
                GeoTiffSnapshots.read(identity, path, options.formatLimits(), cancellation),
                options,
                cancellation);
    }

    /**
     * Opens a defensive copy of signed-integer PixelIsPoint GeoTIFF elevation bytes.
     *
     * @param identity logical non-sensitive source identity
     * @param encoded encoded GeoTIFF bytes copied before parsing
     * @param options explicit elevation unit and immutable resource ceilings
     * @return caller-owned eager elevation source retaining no encoded TIFF state
     */
    public static ElevationSource openElevation(
            SourceIdentity identity, byte[] encoded, GeoTiffElevationOptions options) {
        return openElevation(identity, encoded, options, CancellationToken.none());
    }

    /**
     * Opens a defensive copy of signed-integer PixelIsPoint GeoTIFF elevation bytes with
     * cancellation.
     *
     * @param identity logical non-sensitive source identity
     * @param encoded encoded GeoTIFF bytes copied before parsing
     * @param options explicit elevation unit and immutable resource ceilings
     * @param cancellation operation-local cooperative cancellation token
     * @return caller-owned eager elevation source retaining no encoded TIFF state
     */
    public static ElevationSource openElevation(
            SourceIdentity identity,
            byte[] encoded,
            GeoTiffElevationOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        GeoTiffFailures.limit(
                identity.id(),
                "geoTiffOpen",
                "inputBytes",
                encoded.length,
                options.formatLimits().maximumInputBytes());
        byte[] snapshot = encoded.clone();
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        return GeoTiffParser.openElevation(identity, snapshot, options, cancellation);
    }
}
