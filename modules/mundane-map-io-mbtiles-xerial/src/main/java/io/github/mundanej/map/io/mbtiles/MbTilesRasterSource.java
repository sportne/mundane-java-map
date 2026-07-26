package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.RasterRequestAccounting;
import io.github.mundanej.map.core.RasterResampling;
import io.github.mundanej.map.io.image.EncodedRasterDecodeOptions;
import io.github.mundanej.map.io.image.ImageSourceLimits;
import io.github.mundanej.map.io.image.RasterImages;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class MbTilesRasterSource implements RasterSource {
    private static final int TILE_SIZE = 256;
    private static final long TILE_BYTES = (long) TILE_SIZE * TILE_SIZE * Integer.BYTES;

    private final MbTilesSession session;
    private final MbTilesTileProfile profile;
    private final MbTilesOpenOptions options;
    private final EncodedRasterDecoderRegistry decoders;
    private final RasterSourceMetadata metadata;
    private final DiagnosticReport openingDiagnostics;
    private final LinkedHashMap<TileKey, RgbaPixelBuffer> cache = new LinkedHashMap<>();
    private long retainedCacheBytes;
    private boolean closed;

    MbTilesRasterSource(
            SourceIdentity identity,
            MbTilesSession session,
            MbTilesTileProfile profile,
            DiagnosticReport openingDiagnostics,
            MbTilesOpenOptions options,
            EncodedRasterDecoderRegistry decoders,
            CancellationToken cancellation) {
        this.session = Objects.requireNonNull(session, "session");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.openingDiagnostics = Objects.requireNonNull(openingDiagnostics, "openingDiagnostics");
        this.options = Objects.requireNonNull(options, "options");
        this.decoders = Objects.requireNonNull(decoders, "decoders");
        int width = Math.multiplyExact(profile.matrixWidth(), TILE_SIZE);
        int height = Math.multiplyExact(profile.matrixHeight(), TILE_SIZE);
        metadata =
                new RasterSourceMetadata(
                        identity,
                        width,
                        height,
                        Optional.of(profile.bounds()),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())));
        session.verifyBeforePublication(cancellation, "publish");
    }

    @Override
    public RasterSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public RasterSourceLimits limits() {
        return options.rasterSourceLimits();
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return openingDiagnostics;
    }

    @Override
    public synchronized RasterRead read(RasterRequest request, CancellationToken cancellation) {
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        RasterRequestLimits effective =
                request.tighterLimits().orElse(options.rasterSourceLimits().requestLimits());
        if (!effective.tightens(options.rasterSourceLimits().requestLimits())) {
            throw new IllegalArgumentException("Request limits may only tighten source limits");
        }
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(metadata.identity().id(), effective, cancellation);
        accounting.validateWindow(metadata, request.sourceWindow());
        long sourcePixels =
                Math.multiplyExact(
                        (long) request.sourceWindow().width(), request.sourceWindow().height());
        accounting.chargeSourcePixels(sourcePixels);
        long outputPixels =
                accounting.validateOutput(request.outputWidth(), request.outputHeight());
        RasterResampling.validatePlan(
                request.sourceWindow().width(),
                request.sourceWindow().height(),
                request.outputWidth(),
                request.outputHeight(),
                request.interpolation());
        long sourceBytes = Math.multiplyExact(sourcePixels, Integer.BYTES);
        long outputBytes = Math.multiplyExact(outputPixels, Integer.BYTES);
        accounting.chargeIntermediateBytes(sourceBytes);
        accounting.chargePublishedBytes(outputBytes);
        accounting.checkpoint();
        OwnedBudget owned = new OwnedBudget();
        owned.retain(Math.addExact(sourceBytes, outputBytes));

        int firstColumn = request.sourceWindow().column() / TILE_SIZE;
        int lastColumn = Math.toIntExact((request.sourceWindow().endColumn() - 1) / TILE_SIZE);
        int firstRow = request.sourceWindow().row() / TILE_SIZE;
        int lastRow = Math.toIntExact((request.sourceWindow().endRow() - 1) / TILE_SIZE);
        long expectedTiles =
                Math.multiplyExact(
                        (long) lastColumn - firstColumn + 1, (long) lastRow - firstRow + 1);
        int[] source = new int[Math.toIntExact(sourcePixels)];
        List<TileKey> successfulAccesses = new ArrayList<>();
        LinkedHashMap<TileKey, RgbaPixelBuffer> admissions = new LinkedHashMap<>();
        long presentTiles =
                loadTiles(
                        firstColumn,
                        lastColumn,
                        firstRow,
                        lastRow,
                        request,
                        source,
                        accounting,
                        owned,
                        cancellation,
                        successfulAccesses,
                        admissions);
        RgbaPixelBuffer pixels =
                resample(
                        source,
                        request.sourceWindow().width(),
                        request.sourceWindow().height(),
                        request.outputWidth(),
                        request.outputHeight(),
                        request.interpolation(),
                        accounting);
        long missing = expectedTiles - presentTiles;
        DiagnosticReport diagnostics =
                missing == 0
                        ? DiagnosticReport.empty()
                        : new DiagnosticReport(
                                List.of(
                                        new SourceDiagnostic(
                                                "MBTILES_TILE_MISSING",
                                                DiagnosticSeverity.WARNING,
                                                metadata.identity().id(),
                                                Optional.of(DiagnosticLocation.empty()),
                                                "MBTiles raster read contained missing tiles",
                                                Map.of(
                                                        "zoom",
                                                        Integer.toString(profile.zoom()),
                                                        "count",
                                                        Long.toString(missing)))),
                                0);
        session.verifyBeforePublication(cancellation, "publish");
        accounting.checkpoint();
        commitCache(successfulAccesses, admissions);
        return new RasterRead(request.sourceWindow(), pixels, diagnostics);
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cache.clear();
        retainedCacheBytes = 0;
        session.close();
    }

    private long loadTiles(
            int firstColumn,
            int lastColumn,
            int firstRow,
            int lastRow,
            RasterRequest request,
            int[] source,
            RasterRequestAccounting accounting,
            OwnedBudget owned,
            CancellationToken cancellation,
            List<TileKey> successfulAccesses,
            Map<TileKey, RgbaPixelBuffer> admissions) {
        session.beforeOperation(cancellation, "read");
        long count = 0;
        int globalFirstColumn = Math.addExact(profile.minimumX(), firstColumn);
        int globalLastColumn = Math.addExact(profile.minimumX(), lastColumn);
        int globalFirstRow = Math.addExact(profile.minimumY(), firstRow);
        int globalLastRow = Math.addExact(profile.minimumY(), lastRow);
        int minimumTmsRow = profile.tmsRowForXyz(globalLastRow);
        int maximumTmsRow = profile.tmsRowForXyz(globalFirstRow);
        try (PreparedStatement statement =
                session.connection()
                        .prepareStatement(
                                "SELECT tile_column,tile_row,"
                                        + "typeof(tile_column),typeof(tile_row),"
                                        + "typeof(tile_data),length(tile_data),tile_data FROM tiles"
                                        + " WHERE zoom_level=?"
                                        + " AND tile_column BETWEEN ? AND ?"
                                        + " AND tile_row BETWEEN ? AND ?"
                                        + " ORDER BY tile_row DESC,tile_column"); ) {
            statement.setInt(1, profile.zoom());
            statement.setInt(2, globalFirstColumn);
            statement.setInt(3, globalLastColumn);
            statement.setInt(4, minimumTmsRow);
            statement.setInt(5, maximumTmsRow);
            TileKey previous = null;
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    count++;
                    checkpoint(cancellation, count);
                    if (count > session.limits().maximumRows()) {
                        throw limit("rows", count, session.limits().maximumRows());
                    }
                    TileKey key = validateTileRow(rows);
                    if (key.equals(previous)) {
                        throw tileInvalid("x", "duplicate");
                    }
                    previous = key;
                    RgbaPixelBuffer tile = cache.get(key);
                    if (tile == null) {
                        long encodedLength = rows.getLong(6);
                        owned.ensureTransient(encodedLength);
                        byte[] encoded = rows.getBytes(7);
                        if (encoded == null || encoded.length != encodedLength) {
                            throw tileInvalid("data", "null");
                        }
                        owned.ensureTransient(
                                Math.addExact(
                                        Math.multiplyExact(encodedLength, 3),
                                        Math.multiplyExact(TILE_BYTES, 4)));
                        accounting.chargeIntermediateBytes(TILE_BYTES);
                        tile = decodeTile(encoded, cancellation);
                        owned.retain(TILE_BYTES);
                        admissions.put(key, tile);
                    }
                    successfulAccesses.add(key);
                    copyTile(key, tile, request, source, accounting);
                }
            }
            session.afterOperation(cancellation, "read");
            return count;
        } catch (SQLException exception) {
            SourceException primary = session.queryFailure(exception, "tile");
            session.suppressOperationCleanup(primary, cancellation, "read");
            throw primary;
        } catch (RuntimeException | Error failure) {
            session.suppressOperationCleanup(failure, cancellation, "read");
            throw failure;
        }
    }

    private TileKey validateTileRow(ResultSet rows) throws SQLException {
        if (!"integer".equals(rows.getString(3))) {
            throw tileInvalid("x", "range");
        }
        if (!"integer".equals(rows.getString(4))) {
            throw tileInvalid("y", "range");
        }
        int column = exactInteger(rows, 1, "x");
        if (column < profile.minimumX() || column > profile.maximumX()) {
            throw tileInvalid("x", "range");
        }
        int tmsRow = exactInteger(rows, 2, "y");
        int xyzRow = profile.xyzRowForTms(tmsRow);
        if (xyzRow < profile.minimumY() || xyzRow > profile.maximumY()) {
            throw tileInvalid("y", "range");
        }
        String storage = rows.getString(5);
        long bytes = rows.getLong(6);
        validateTileData(storage, bytes, rows.wasNull());
        return new TileKey(column - profile.minimumX(), xyzRow - profile.minimumY());
    }

    private int exactInteger(ResultSet rows, int index, String field) throws SQLException {
        long value = rows.getLong(index);
        if (rows.wasNull() || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw tileInvalid(field, "range");
        }
        return Math.toIntExact(value);
    }

    private void validateTileData(String storage, long bytes, boolean lengthNull) {
        if (!"blob".equals(storage) || lengthNull || bytes <= 0) {
            throw tileInvalid("data", "null");
        }
        if (bytes > session.limits().maximumBlobBytes()) {
            throw limit("blobBytes", bytes, session.limits().maximumBlobBytes());
        }
    }

    private RgbaPixelBuffer decodeTile(byte[] encoded, CancellationToken cancellation) {
        ImageSourceLimits imageLimits =
                ImageSourceLimits.defaults()
                        .withMaximumEncodedBytes(session.limits().maximumBlobBytes());
        EncodedRasterDecodeOptions decodeOptions =
                EncodedRasterDecodeOptions.defaults()
                        .expecting(profile.format())
                        .expectingDimensions(TILE_SIZE, TILE_SIZE)
                        .withImageLimits(imageLimits);
        try {
            return RasterImages.decode(
                    encoded, metadata.identity(), decodeOptions, decoders, cancellation);
        } catch (SourceException failure) {
            SourceDiagnostic terminal = failure.terminal();
            if ("SOURCE_CANCELLED".equals(terminal.code())) {
                throw failure;
            }
            if ("SOURCE_LIMIT_EXCEEDED".equals(terminal.code())) {
                throw MbTilesFailures.failure(
                        metadata.identity().id(),
                        "SOURCE_LIMIT_EXCEEDED",
                        "MBTiles tile image decode exceeded its limit",
                        Map.of(
                                "scope",
                                "mbtilesRaster",
                                "limit",
                                "imageDecode",
                                "requested",
                                terminal.context().getOrDefault("requested", "0"),
                                "maximum",
                                terminal.context().getOrDefault("maximum", "0")));
            }
            if ("IMAGE_DIMENSIONS_MISMATCH".equals(terminal.code())) {
                throw MbTilesFailures.failure(
                        metadata.identity().id(),
                        "MBTILES_TILE_INVALID",
                        "MBTiles tile is invalid",
                        Map.of(
                                "field",
                                "data",
                                "reason",
                                "size",
                                "expectedWidth",
                                terminal.context().get("expectedWidth"),
                                "expectedHeight",
                                terminal.context().get("expectedHeight"),
                                "width",
                                terminal.context().get("width"),
                                "height",
                                terminal.context().get("height")));
            }
            if (terminal.code().startsWith("IMAGE_")) {
                throw MbTilesFailures.failure(
                        metadata.identity().id(),
                        "MBTILES_TILE_INVALID",
                        "MBTiles tile is invalid",
                        Map.of("field", "data", "reason", "decode", "imageCode", terminal.code()));
            }
            throw new IllegalStateException("Unexpected encoded-image diagnostic contract");
        }
    }

    private void copyTile(
            TileKey key,
            RgbaPixelBuffer tile,
            RasterRequest request,
            int[] destination,
            RasterRequestAccounting accounting) {
        int tileLeft = key.column() * TILE_SIZE;
        int tileTop = key.row() * TILE_SIZE;
        int left = Math.max(tileLeft, request.sourceWindow().column());
        int top = Math.max(tileTop, request.sourceWindow().row());
        int right =
                Math.min(tileLeft + TILE_SIZE, Math.toIntExact(request.sourceWindow().endColumn()));
        int bottom =
                Math.min(tileTop + TILE_SIZE, Math.toIntExact(request.sourceWindow().endRow()));
        int width = request.sourceWindow().width();
        for (int row = top; row < bottom; row++) {
            if ((row & 63) == 0) {
                accounting.checkpoint();
            }
            for (int column = left; column < right; column++) {
                destination[
                                (row - request.sourceWindow().row()) * width
                                        + column
                                        - request.sourceWindow().column()] =
                        tile.rgbaAt(column - tileLeft, row - tileTop);
            }
        }
    }

    private static RgbaPixelBuffer resample(
            int[] source,
            int sourceWidth,
            int sourceHeight,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation,
            RasterRequestAccounting accounting) {
        RgbaPixelBuffer.Builder output = RgbaPixelBuffer.builder(outputWidth, outputHeight);
        for (int y = 0; y < outputHeight; y++) {
            if ((y & 63) == 0) {
                accounting.checkpoint();
            }
            if (interpolation == RasterInterpolation.NEAREST) {
                int sourceY = RasterResampling.nearestIndex(y, sourceHeight, outputHeight);
                for (int x = 0; x < outputWidth; x++) {
                    int sourceX = RasterResampling.nearestIndex(x, sourceWidth, outputWidth);
                    output.setRgba(x, y, source[sourceY * sourceWidth + sourceX]);
                }
            } else {
                RasterResampling.AxisWeights yWeights =
                        RasterResampling.bilinearAxis(y, sourceHeight, outputHeight);
                for (int x = 0; x < outputWidth; x++) {
                    RasterResampling.AxisWeights xWeights =
                            RasterResampling.bilinearAxis(x, sourceWidth, outputWidth);
                    int northWest =
                            source[yWeights.lowerIndex() * sourceWidth + xWeights.lowerIndex()];
                    int northEast =
                            source[yWeights.lowerIndex() * sourceWidth + xWeights.upperIndex()];
                    int southWest =
                            source[yWeights.upperIndex() * sourceWidth + xWeights.lowerIndex()];
                    int southEast =
                            source[yWeights.upperIndex() * sourceWidth + xWeights.upperIndex()];
                    output.setRgba(
                            x,
                            y,
                            RasterResampling.bilinearRgba(
                                    northWest, northEast, southWest, southEast, xWeights,
                                    yWeights));
                }
            }
        }
        return output.build();
    }

    private void commitCache(
            List<TileKey> successfulAccesses, Map<TileKey, RgbaPixelBuffer> admissions) {
        MbTilesTileCachePolicy policy = options.cachePolicy();
        if (!policy.enabled()) {
            return;
        }
        for (TileKey key : successfulAccesses) {
            RgbaPixelBuffer tile = cache.remove(key);
            if (tile == null) {
                tile = admissions.get(key);
                if (tile == null || TILE_BYTES > policy.maximumPixelBytes().orElseThrow()) {
                    continue;
                }
                retainedCacheBytes = Math.addExact(retainedCacheBytes, TILE_BYTES);
            }
            cache.put(key, tile);
        }
        while (cache.size() > policy.maximumEntries().orElseThrow()
                || retainedCacheBytes > policy.maximumPixelBytes().orElseThrow()) {
            TileKey eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
            retainedCacheBytes -= TILE_BYTES;
        }
    }

    private void checkpoint(CancellationToken cancellation, long row) {
        if ((row & 4_095) == 1) {
            MbTilesFailures.checkpoint(
                    metadata.identity().id(), cancellation::isCancellationRequested, "tile");
        }
    }

    private SourceException tileInvalid(String field, String reason) {
        return MbTilesFailures.failure(
                metadata.identity().id(),
                "MBTILES_TILE_INVALID",
                "MBTiles tile is invalid",
                Map.of("field", field, "reason", reason));
    }

    private SourceException limit(String name, long requested, long maximum) {
        return MbTilesFailures.failure(
                metadata.identity().id(),
                "SOURCE_LIMIT_EXCEEDED",
                "MBTiles raster operation limit exceeded",
                Map.of(
                        "scope",
                        "mbtilesRaster",
                        "limit",
                        name,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("MBTiles tile source is closed");
        }
    }

    private final class OwnedBudget {
        private long retained;

        private void retain(long bytes) {
            long requested = Math.addExact(retained, bytes);
            ensure(requested);
            retained = requested;
        }

        private void ensureTransient(long bytes) {
            ensure(Math.addExact(retained, bytes));
        }

        private void ensure(long requested) {
            if (requested > session.limits().maximumOwnedBytes()) {
                throw limit("ownedBytes", requested, session.limits().maximumOwnedBytes());
            }
        }
    }

    private record TileKey(int column, int row) {}
}
