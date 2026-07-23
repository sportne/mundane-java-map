package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.image.EncodedRasterDecodeOptions;
import io.github.mundanej.map.io.image.RasterImages;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

final class DefaultHttpXyzTileClient implements HttpXyzTileClient {
    private static final Duration WAIT_QUANTUM = Duration.ofMillis(25);
    private static final Set<String> CLOSED_IMAGE_CODES =
            Set.of(
                    "IMAGE_EXPECTED_FORMAT_MISMATCH",
                    "IMAGE_HEADER_INVALID",
                    "IMAGE_CONTAINER_INVALID",
                    "IMAGE_PROFILE_UNSUPPORTED",
                    "IMAGE_DECODER_NOT_REGISTERED",
                    "IMAGE_DECODE_FAILED",
                    "IMAGE_DECODE_MISMATCH",
                    "IMAGE_IO_FAILED");

    private final SourceIdentity identity;
    private final HttpXyzTemplate template;
    private final HttpXyzClientOptions options;
    private final EncodedRasterDecoderRegistry decoders;
    private final ExecutorService executor;
    private final HttpClient client;
    private final Object lifecycle = new Object();
    private Map<TileKey, RgbaPixelBuffer> cache = Map.of();
    private boolean closed;
    private FetchOperation activeOperation;

    private DefaultHttpXyzTileClient(
            SourceIdentity identity,
            HttpXyzTemplate template,
            HttpXyzClientOptions options,
            EncodedRasterDecoderRegistry decoders,
            ExecutorService executor,
            HttpClient client) {
        this.identity = identity;
        this.template = template;
        this.options = options;
        this.decoders = decoders;
        this.executor = executor;
        this.client = client;
    }

    static HttpXyzTileClient open(
            SourceIdentity identity,
            HttpXyzTemplate template,
            HttpXyzClientOptions options,
            EncodedRasterDecoderRegistry decoders) {
        ExecutorService executor =
                Executors.newFixedThreadPool(
                        options.limits().concurrency(),
                        runnable -> {
                            Thread thread = new Thread(runnable, "mundane-map-http-tile");
                            thread.setDaemon(true);
                            return thread;
                        });
        SSLContext tls;
        try {
            TrustManagerFactory trust =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init((KeyStore) null);
            tls = SSLContext.getInstance("TLS");
            tls.init(new KeyManager[0], trust.getTrustManagers(), new SecureRandom());
        } catch (GeneralSecurityException failure) {
            executor.shutdownNow();
            throw initFailure(identity.id(), "tls", "security");
        }
        try {
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(options.connectTimeout())
                            .executor(executor)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .version(HttpClient.Version.HTTP_1_1)
                            .proxy(DirectProxySelector.INSTANCE)
                            .sslContext(tls)
                            .build();
            return new DefaultHttpXyzTileClient(
                    identity, template, options, decoders, executor, client);
        } catch (RuntimeException failure) {
            executor.shutdownNow();
            throw initFailure(identity.id(), "client", "other");
        }
    }

    @Override
    public RasterSource fetch(XyzTileRegion region, CancellationToken cancellation) {
        if (region == null || cancellation == null) {
            throw new NullPointerException("region and cancellation are required");
        }
        validateRegion(region);
        if (cancellation.isCancellationRequested()) {
            throw cancelled();
        }
        FetchOperation operation = beginFetch();
        boolean published = false;
        try {
            long deadline = System.nanoTime() + options.operationTimeout().toNanos();
            List<Tile> tiles = tiles(region);
            RgbaPixelBuffer[] resolved = new RgbaPixelBuffer[tiles.size()];
            boolean[] hits = new boolean[tiles.size()];
            boolean[] missing = new boolean[tiles.size()];
            Map<TileKey, RgbaPixelBuffer> snapshot = snapshotCache();
            List<Tile> misses = new ArrayList<>();
            for (Tile tile : tiles) {
                RgbaPixelBuffer cached = snapshot.get(tile.key());
                if (cached == null) {
                    misses.add(tile);
                } else {
                    resolved[tile.index()] = cached;
                    hits[tile.index()] = true;
                }
            }
            List<SourceDiagnostic> warnings = new ArrayList<>();
            long omittedWarnings = 0;
            long cumulativeBodyBytes = 0;
            int warningLimit =
                    Math.min(
                            options.limits().warnings(),
                            options.snapshotLimits().requestLimits().retainedWarnings());
            int decodedMisses = 0;
            for (int start = 0; start < misses.size(); ) {
                checkpoint(cancellation, operation, "fetch");
                int batchSize =
                        batchSize(
                                region,
                                decodedMisses,
                                cumulativeBodyBytes,
                                misses.get(start).ordinal());
                int end = Math.min(misses.size(), start + batchSize);
                List<PendingTile> pending = new ArrayList<>(end - start);
                for (int index = start; index < end; index++) {
                    pending.add(submit(misses.get(index), operation, deadline));
                }
                for (PendingTile request : pending) {
                    try {
                        HttpResponse<byte[]> response =
                                await(
                                        request.future(),
                                        request.profile(),
                                        cancellation,
                                        operation,
                                        deadline);
                        Tile tile = request.profile().tile;
                        if (request.profile().missingStatus != 0) {
                            missing[tile.index()] = true;
                            if (warnings.size() < warningLimit) {
                                warnings.add(missingWarning(tile, request.profile().missingStatus));
                            } else {
                                omittedWarnings++;
                            }
                        } else {
                            cumulativeBodyBytes =
                                    Math.addExact(cumulativeBodyBytes, response.body().length);
                            if (cumulativeBodyBytes > options.limits().cumulativeResponseBytes()) {
                                throw limit(
                                        tile.ordinal(),
                                        "bodyBytes",
                                        cumulativeBodyBytes,
                                        options.limits().cumulativeResponseBytes());
                            }
                            checkpoint(cancellation, operation, "decode");
                            resolved[tile.index()] =
                                    decode(
                                            response.body(),
                                            request.profile(),
                                            cancellation,
                                            operation);
                            decodedMisses++;
                        }
                    } finally {
                        operation.release(request.future(), request.profile().subscriber);
                    }
                }
                start = end;
            }
            checkpoint(cancellation, operation, "publish");
            RgbaPixelBuffer pixels =
                    mosaic(region, tiles, resolved, missing, cancellation, operation);
            DiagnosticReport report = new DiagnosticReport(warnings, omittedWarnings);
            RasterSource source =
                    new DetachedHttpTileRasterSource(
                            identity, region.bounds(), options.snapshotLimits(), pixels, report);
            checkpoint(cancellation, operation, "publish");
            synchronized (lifecycle) {
                if (closed || operation.isCloseRequested() || System.nanoTime() >= deadline) {
                    source.close();
                    if (closed || operation.isCloseRequested()) {
                        throw clientClosed("publish");
                    }
                    throw requestFailed("wait", "timeout");
                }
                commitCache(tiles, resolved, hits, missing);
                activeOperation = null;
                published = true;
            }
            return source;
        } finally {
            if (!published) {
                settleFailure(operation);
            }
        }
    }

    private int batchSize(
            XyzTileRegion region, int decodedMisses, long cumulativeBodyBytes, long nextOrdinal) {
        long body = options.limits().responseBodyBytes();
        long segmentSlots = Math.multiplyExact(8L, (body + 65_535L) / 65_536L);
        long reservation =
                Math.addExact(
                        Math.addExact(Math.multiplyExact(4L, body), 16L * 256L * 256L),
                        segmentSlots);
        long mosaicAndSlots = Math.multiplyExact(region.tileCount(), 4L * 256L * 256L + 16L);
        long retained = Math.multiplyExact(decodedMisses, 4L * 256L * 256L);
        long base = Math.addExact(mosaicAndSlots, retained);
        long ownedCapacity = (options.limits().ownedBytes() - base) / reservation;
        long bodyCapacity =
                (options.limits().cumulativeResponseBytes() - cumulativeBodyBytes) / body;
        if (ownedCapacity < 1) {
            throw limit(
                    nextOrdinal,
                    "ownedBytes",
                    Math.addExact(base, reservation),
                    options.limits().ownedBytes());
        }
        if (bodyCapacity < 1) {
            throw limit(
                    nextOrdinal,
                    "bodyBytes",
                    Math.addExact(cumulativeBodyBytes, body),
                    options.limits().cumulativeResponseBytes());
        }
        return Math.toIntExact(
                Math.min(options.limits().concurrency(), Math.min(ownedCapacity, bodyCapacity)));
    }

    private Map<TileKey, RgbaPixelBuffer> snapshotCache() {
        synchronized (lifecycle) {
            return new LinkedHashMap<>(cache);
        }
    }

    private List<Tile> tiles(XyzTileRegion region) {
        List<Tile> result = new ArrayList<>(Math.toIntExact(region.tileCount()));
        int index = 0;
        for (int y = region.minimumY(); y <= region.maximumY(); y++) {
            for (int x = region.minimumX(); x <= region.maximumX(); x++) {
                result.add(new Tile(index, index + 1L, region.zoom(), x, y));
                index++;
            }
        }
        return result;
    }

    private PendingTile submit(Tile tile, FetchOperation operation, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw requestFailed(tile.ordinal(), "wait", "timeout");
        }
        XyzTileRegion single = XyzTileRegion.single(tile.zoom(), tile.x(), tile.y());
        URI uri = template.resolve(single, options.limits().templateCharacters());
        Duration timeout =
                Duration.ofNanos(Math.min(options.requestTimeout().toNanos(), remaining));
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("Accept", "image/png, image/jpeg")
                        .header("Accept-Encoding", "identity")
                        .header("User-Agent", "mundane-java-map")
                        .GET()
                        .build();
        ResponseProfile profile = new ResponseProfile(tile);
        CompletableFuture<HttpResponse<byte[]>> future =
                client.sendAsync(request, info -> subscriber(info, profile, operation));
        operation.registerFuture(future);
        return new PendingTile(profile, future);
    }

    private RgbaPixelBuffer mosaic(
            XyzTileRegion region,
            List<Tile> tiles,
            RgbaPixelBuffer[] resolved,
            boolean[] missing,
            CancellationToken cancellation,
            FetchOperation operation) {
        int width = Math.multiplyExact(region.widthInTiles(), 256);
        int height = Math.multiplyExact(region.heightInTiles(), 256);
        RgbaPixelBuffer.Builder builder = RgbaPixelBuffer.builder(width, height);
        long copied = 0;
        for (Tile tile : tiles) {
            if (missing[tile.index()]) {
                continue;
            }
            RgbaPixelBuffer pixels = resolved[tile.index()];
            int offsetX = (tile.x() - region.minimumX()) * 256;
            int offsetY = (tile.y() - region.minimumY()) * 256;
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    if ((copied++ & 4095L) == 0) {
                        checkpoint(cancellation, operation, "publish");
                    }
                    builder.setRgba(offsetX + x, offsetY + y, pixels.rgbaAt(x, y));
                }
            }
        }
        return builder.build();
    }

    private void commitCache(
            List<Tile> tiles, RgbaPixelBuffer[] resolved, boolean[] hits, boolean[] missing) {
        if (options.cachePolicy() != HttpTileCachePolicy.MEMORY) {
            return;
        }
        LinkedHashMap<TileKey, RgbaPixelBuffer> candidate = new LinkedHashMap<>(cache);
        for (Tile tile : tiles) {
            if (missing[tile.index()]) {
                continue;
            }
            if (hits[tile.index()]) {
                candidate.remove(tile.key());
            }
            candidate.put(tile.key(), resolved[tile.index()]);
            while (candidate.size() > options.limits().cacheEntries()
                    || (long) candidate.size() * 256L * 256L * 4L > options.limits().cacheBytes()) {
                candidate.remove(candidate.keySet().iterator().next());
            }
        }
        cache = candidate;
    }

    private FetchOperation beginFetch() {
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("HTTP XYZ client is closed");
            }
            if (activeOperation != null) {
                throw new IllegalStateException("HTTP XYZ client already has an active fetch");
            }
            FetchOperation operation = new FetchOperation();
            activeOperation = operation;
            return operation;
        }
    }

    private HttpResponse.BodySubscriber<byte[]> subscriber(
            HttpResponse.ResponseInfo response, ResponseProfile profile, FetchOperation operation) {
        validateHeaderBounds(response, profile);
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            profile.missingStatus = response.statusCode();
            return new CancellingBodySubscriber();
        }
        if (response.statusCode() != 200) {
            throw invalidStatus(profile.tile.ordinal(), response.statusCode());
        }
        validateSuccessHeaders(response, profile);
        BoundedBodySubscriber subscriber =
                new BoundedBodySubscriber(options.limits().responseBodyBytes(), profile.length);
        profile.subscriber = subscriber;
        operation.registerSubscriber(subscriber);
        return subscriber;
    }

    private void validateHeaderBounds(HttpResponse.ResponseInfo response, ResponseProfile profile) {
        int headerCount = 0;
        long aggregate = 0;
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            headerCount = Math.addExact(headerCount, entry.getValue().size());
            for (String value : entry.getValue()) {
                if (entry.getKey().length() > options.limits().headerNameOrValueCharacters()
                        || value.length() > options.limits().headerNameOrValueCharacters()) {
                    throw limit(
                            profile.tile.ordinal(),
                            "headerCharacters",
                            Math.max(entry.getKey().length(), value.length()),
                            options.limits().headerNameOrValueCharacters());
                }
                aggregate = Math.addExact(aggregate, entry.getKey().length() + value.length());
            }
        }
        if (headerCount > options.limits().responseHeaders()) {
            throw limit(
                    profile.tile.ordinal(),
                    "headers",
                    headerCount,
                    options.limits().responseHeaders());
        }
        if (aggregate > options.limits().aggregateHeaderCharacters()) {
            throw limit(
                    profile.tile.ordinal(),
                    "headerCharacters",
                    aggregate,
                    options.limits().aggregateHeaderCharacters());
        }
    }

    private void validateSuccessHeaders(
            HttpResponse.ResponseInfo response, ResponseProfile profile) {
        List<String> types = response.headers().allValues("Content-Type");
        if (types.isEmpty()) {
            throw invalidResponse(profile.tile.ordinal(), "contentType", "missing");
        }
        if (types.size() != 1) {
            throw invalidResponse(profile.tile.ordinal(), "contentType", "duplicate");
        }
        String media = types.getFirst().strip().toLowerCase(Locale.ROOT);
        if (media.equals("image/png")) {
            profile.format = EncodedRasterFormat.PNG;
        } else if (media.equals("image/jpeg")) {
            profile.format = EncodedRasterFormat.JPEG;
        } else {
            throw invalidResponse(profile.tile.ordinal(), "contentType", "unsupported");
        }
        List<String> encodings = response.headers().allValues("Content-Encoding");
        if (encodings.size() > 1) {
            throw invalidResponse(profile.tile.ordinal(), "contentEncoding", "duplicate");
        }
        if (encodings.size() == 1 && !encodings.getFirst().strip().equalsIgnoreCase("identity")) {
            throw invalidResponse(profile.tile.ordinal(), "contentEncoding", "unsupported");
        }
        List<String> lengths = response.headers().allValues("Content-Length");
        if (lengths.size() > 1) {
            throw invalidResponse(profile.tile.ordinal(), "contentLength", "duplicate");
        }
        if (!lengths.isEmpty()) {
            try {
                String text = lengths.getFirst();
                if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
                    throw new NumberFormatException("non-decimal");
                }
                profile.length = Long.parseLong(text);
            } catch (NumberFormatException failure) {
                throw invalidResponse(profile.tile.ordinal(), "contentLength", "syntax");
            }
            if (profile.length > options.limits().responseBodyBytes()) {
                throw limit(
                        profile.tile.ordinal(),
                        "tileBodyBytes",
                        profile.length,
                        options.limits().responseBodyBytes());
            }
        }
    }

    private HttpResponse<byte[]> await(
            CompletableFuture<HttpResponse<byte[]>> future,
            ResponseProfile profile,
            CancellationToken cancellation,
            FetchOperation operation,
            long deadline) {
        while (true) {
            checkpoint(cancellation, operation, "fetch");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw requestFailed(profile.tile.ordinal(), "wait", "timeout");
            }
            try {
                return future.get(
                        Math.min(remaining, WAIT_QUANTUM.toNanos()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ignored) {
                // Poll cancellation, close, and the operation deadline.
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw requestFailed(profile.tile.ordinal(), "wait", "interrupted");
            } catch (ExecutionException failure) {
                throw mapFailure(failure.getCause(), profile, cancellation, operation);
            } catch (CancellationException failure) {
                checkpoint(cancellation, operation, "fetch");
                throw requestFailed(profile.tile.ordinal(), "wait", "other");
            }
        }
    }

    private RuntimeException mapFailure(
            Throwable failure,
            ResponseProfile profile,
            CancellationToken cancellation,
            FetchOperation operation) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SourceException source) {
            return source;
        }
        if (cause instanceof BoundedBodySubscriber.BodyLimitException) {
            return limit(
                    profile.tile.ordinal(),
                    "tileBodyBytes",
                    (long) options.limits().responseBodyBytes() + 1,
                    options.limits().responseBodyBytes());
        }
        if (cause instanceof BoundedBodySubscriber.BodyLengthException) {
            return invalidResponse(profile.tile.ordinal(), "contentLength", "mismatch");
        }
        checkpoint(cancellation, operation, "fetch");
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return requestFailed(profile.tile.ordinal(), "wait", "timeout");
        }
        if (cause instanceof UnknownHostException) {
            return requestFailed(profile.tile.ordinal(), "dns", "io");
        }
        if (cause instanceof ConnectException) {
            return requestFailed(profile.tile.ordinal(), "connect", "io");
        }
        if (cause instanceof SSLException) {
            return requestFailed(profile.tile.ordinal(), "tls", "protocol");
        }
        if (cause instanceof IOException && profile.length >= 0) {
            return invalidResponse(profile.tile.ordinal(), "contentLength", "mismatch");
        }
        if (cause instanceof ProtocolException) {
            return requestFailed(profile.tile.ordinal(), "body", "protocol");
        }
        return requestFailed(
                profile.tile.ordinal(), "send", cause instanceof IOException ? "io" : "other");
    }

    private RgbaPixelBuffer decode(
            byte[] bytes,
            ResponseProfile profile,
            CancellationToken cancellation,
            FetchOperation operation) {
        EncodedRasterDecodeOptions decode =
                options.decodeOptions().expecting(profile.format).expectingDimensions(256, 256);
        CancellationToken effective =
                () -> cancellation.isCancellationRequested() || operation.isCloseRequested();
        try {
            return RasterImages.decode(bytes, identity, decode, decoders, effective);
        } catch (SourceException failure) {
            String code = failure.terminal().code();
            if (operation.isCloseRequested()) {
                throw clientClosed("decode");
            }
            if (code.equals("SOURCE_CANCELLED")) {
                throw cancelled();
            }
            if (code.equals("SOURCE_LIMIT_EXCEEDED")) {
                return throwLimit(profile.tile.ordinal(), failure);
            }
            if (code.equals("IMAGE_DIMENSIONS_MISMATCH")) {
                throw HttpTileDiagnostics.failure(
                        identity.id(),
                        profile.tile.ordinal(),
                        "HTTP_TILE_IMAGE_INVALID",
                        "HTTP tile image dimensions are invalid",
                        Map.of(
                                "reason",
                                "dimensions",
                                "width",
                                failure.terminal().context().get("width"),
                                "height",
                                failure.terminal().context().get("height")));
            }
            if (CLOSED_IMAGE_CODES.contains(code)) {
                throw HttpTileDiagnostics.failure(
                        identity.id(),
                        profile.tile.ordinal(),
                        "HTTP_TILE_IMAGE_INVALID",
                        "HTTP tile image is invalid",
                        Map.of("reason", "decode", "imageCode", code));
            }
            throw new IllegalStateException("Image decoder returned an unexpected checked code");
        }
    }

    private RgbaPixelBuffer throwLimit(long ordinal, SourceException failure) {
        throw HttpTileDiagnostics.failure(
                identity.id(),
                ordinal,
                "SOURCE_LIMIT_EXCEEDED",
                "HTTP tile image decode limit exceeded",
                Map.of(
                        "scope",
                        "httpTileFetch",
                        "limit",
                        "imageDecode",
                        "requested",
                        failure.terminal().context().get("requested"),
                        "maximum",
                        failure.terminal().context().get("maximum")));
    }

    private void validateRegion(XyzTileRegion region) {
        if (region.zoom() > options.limits().zoom()) {
            throw new IllegalArgumentException("XYZ zoom exceeds configured limits");
        }
        if (region.tileCount() > options.limits().tilesPerRequest()
                || region.widthInTiles() > options.limits().regionAxisTiles()
                || region.heightInTiles() > options.limits().regionAxisTiles()) {
            throw new IllegalArgumentException("XYZ region exceeds configured limits");
        }
        long pixels = Math.multiplyExact(region.tileCount(), 256L * 256L);
        int width = Math.multiplyExact(region.widthInTiles(), 256);
        int height = Math.multiplyExact(region.heightInTiles(), 256);
        var limits = options.snapshotLimits().requestLimits();
        if (width > limits.outputDimension()
                || height > limits.outputDimension()
                || pixels > limits.sourceWindowPixels()
                || pixels > limits.outputPixels()
                || Math.multiplyExact(4L, pixels) > limits.decodedIntermediateBytes()
                || Math.multiplyExact(4L, pixels) > limits.ownedPayloadBytes()) {
            throw new IllegalArgumentException("XYZ mosaic exceeds snapshot limits");
        }
    }

    private void checkpoint(
            CancellationToken cancellation, FetchOperation operation, String closePhase) {
        if (cancellation.isCancellationRequested()) {
            throw cancelled();
        }
        if (operation.isCloseRequested()) {
            throw clientClosed(closePhase);
        }
    }

    private SourceException cancelled() {
        return HttpTileDiagnostics.failure(
                identity.id(),
                "SOURCE_CANCELLED",
                "HTTP tile fetch was cancelled",
                Map.of("operation", "http-tile-fetch"));
    }

    private SourceException clientClosed(String phase) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                "HTTP_TILE_CLIENT_CLOSED",
                "HTTP tile client closed during fetch",
                Map.of("phase", phase));
    }

    private SourceDiagnostic missingWarning(Tile tile, int status) {
        return HttpTileDiagnostics.warning(
                identity.id(),
                tile.ordinal(),
                "HTTP_TILE_MISSING",
                "HTTP tile is missing",
                Map.of(
                        "zoom",
                        Integer.toString(tile.zoom()),
                        "x",
                        Integer.toString(tile.x()),
                        "y",
                        Integer.toString(tile.y()),
                        "status",
                        Integer.toString(status)));
    }

    private SourceException invalidStatus(long ordinal, int status) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                ordinal,
                "HTTP_TILE_RESPONSE_INVALID",
                "HTTP tile response is outside the supported profile",
                Map.of(
                        "field",
                        "status",
                        "reason",
                        "unsupported",
                        "status",
                        Integer.toString(status)));
    }

    private SourceException invalidResponse(long ordinal, String field, String reason) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                ordinal,
                "HTTP_TILE_RESPONSE_INVALID",
                "HTTP tile response is outside the supported profile",
                Map.of("field", field, "reason", reason));
    }

    private SourceException requestFailed(String phase, String reason) {
        return requestFailed(1, phase, reason);
    }

    private SourceException requestFailed(long ordinal, String phase, String reason) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                ordinal,
                "HTTP_TILE_REQUEST_FAILED",
                "HTTP tile request failed",
                Map.of("phase", phase, "reason", reason));
    }

    private SourceException limit(long ordinal, String name, long requested, long maximum) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                ordinal,
                "SOURCE_LIMIT_EXCEEDED",
                "HTTP tile limit exceeded",
                Map.of(
                        "scope",
                        "httpTileFetch",
                        "limit",
                        name,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private void settleFailure(FetchOperation operation) {
        boolean settled = false;
        boolean interrupted = false;
        try {
            settled = operation.cleanup(options.closeTimeout());
        } catch (InterruptedException failure) {
            interrupted = true;
        }
        boolean shutdown = false;
        synchronized (lifecycle) {
            if (activeOperation == operation) {
                activeOperation = null;
            }
            if (!settled && !closed) {
                closed = true;
                shutdown = true;
            }
        }
        if (shutdown) {
            client.shutdownNow();
            executor.shutdownNow();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isClosed() {
        synchronized (lifecycle) {
            return closed;
        }
    }

    @Override
    public void close() {
        FetchOperation operation;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            operation = activeOperation;
            cache = Map.of();
        }
        if (operation != null) {
            operation.requestClose();
        }
        client.shutdownNow();
        executor.shutdownNow();
        if (operation == null && Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw closeFailure("client", "interrupted");
        }
        long deadline = System.nanoTime() + options.closeTimeout().toNanos();
        boolean interrupted = false;
        String resource = "body";
        try {
            if (operation != null) {
                if (!operation.cleanup(remaining(deadline))) {
                    throw closeFailure("body", "timeout");
                }
            }
            resource = "client";
            if (!client.awaitTermination(remaining(deadline))) {
                throw closeFailure("client", "timeout");
            }
            resource = "executor";
            if (!executor.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS)) {
                throw closeFailure("executor", "timeout");
            }
        } catch (InterruptedException failure) {
            interrupted = true;
            throw closeFailure(resource, "interrupted");
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Duration remaining(long deadline) {
        long nanos = Math.max(1L, deadline - System.nanoTime());
        return Duration.ofNanos(nanos);
    }

    private SourceException closeFailure(String resource, String reason) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                "HTTP_TILE_CLOSE_FAILED",
                "HTTP tile client close failed",
                Map.of("resource", resource, "reason", reason));
    }

    private static SourceException initFailure(String sourceId, String resource, String reason) {
        return HttpTileDiagnostics.failure(
                sourceId,
                "HTTP_TILE_CLIENT_INIT_FAILED",
                "HTTP tile client initialization failed",
                Map.of("resource", resource, "reason", reason));
    }

    private static final class ResponseProfile {
        private final Tile tile;
        private EncodedRasterFormat format;
        private long length = -1;
        private int missingStatus;
        private volatile BoundedBodySubscriber subscriber;

        ResponseProfile(Tile tile) {
            this.tile = tile;
        }
    }

    private record Tile(int index, long ordinal, int zoom, int x, int y) {
        TileKey key() {
            return new TileKey(zoom, x, y);
        }
    }

    private record TileKey(int zoom, int x, int y) {}

    private record PendingTile(
            ResponseProfile profile, CompletableFuture<HttpResponse<byte[]>> future) {}

    private static final class FetchOperation {
        private final AtomicBoolean closeRequested = new AtomicBoolean();
        private final AtomicBoolean transportCancelled = new AtomicBoolean();
        private final CompletableFuture<Boolean> cleanupResult = new CompletableFuture<>();
        private final List<CompletableFuture<?>> futures = new ArrayList<>();
        private final List<BoundedBodySubscriber> subscribers = new ArrayList<>();
        private boolean cleanupClaimed;
        private volatile long cleanupDeadline;

        synchronized void registerFuture(CompletableFuture<?> value) {
            if (transportCancelled.get()) {
                value.cancel(true);
            } else {
                futures.add(value);
            }
        }

        synchronized void registerSubscriber(BoundedBodySubscriber value) {
            if (transportCancelled.get()) {
                value.cancel();
            } else {
                subscribers.add(value);
            }
        }

        synchronized void release(CompletableFuture<?> request, BoundedBodySubscriber body) {
            for (int index = 0; index < futures.size(); index++) {
                if (futures.get(index) == request) {
                    futures.remove(index);
                    break;
                }
            }
            if (body != null) {
                for (int index = 0; index < subscribers.size(); index++) {
                    if (subscribers.get(index) == body) {
                        subscribers.remove(index);
                        break;
                    }
                }
            }
        }

        void requestClose() {
            closeRequested.set(true);
        }

        boolean isCloseRequested() {
            return closeRequested.get();
        }

        synchronized void cancelTransport() {
            transportCancelled.set(true);
            for (BoundedBodySubscriber body : subscribers) {
                body.cancel();
            }
            for (CompletableFuture<?> request : futures) {
                request.cancel(true);
            }
        }

        boolean cleanup(Duration timeout) throws InterruptedException {
            boolean owner;
            long deadline;
            synchronized (this) {
                owner = !cleanupClaimed;
                if (owner) {
                    cleanupDeadline = System.nanoTime() + timeout.toNanos();
                    cleanupClaimed = true;
                }
                deadline = cleanupDeadline;
            }
            if (owner) {
                cancelTransport();
                try {
                    boolean settled = awaitUntil(deadline);
                    cleanupResult.complete(settled);
                    return settled;
                } catch (InterruptedException failure) {
                    cleanupResult.complete(false);
                    throw failure;
                }
            }
            try {
                return cleanupResult.get(
                        Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (CancellationException | ExecutionException ignored) {
                return false;
            } catch (TimeoutException failure) {
                return false;
            }
        }

        private boolean awaitUntil(long deadline) throws InterruptedException {
            List<CompletableFuture<?>> requestSnapshot;
            List<BoundedBodySubscriber> bodySnapshot;
            synchronized (this) {
                requestSnapshot = List.copyOf(futures);
                bodySnapshot = List.copyOf(subscribers);
            }
            for (CompletableFuture<?> request : requestSnapshot) {
                if (!awaitFuture(request, deadline)) {
                    return false;
                }
            }
            for (BoundedBodySubscriber body : bodySnapshot) {
                if (!body.await(Math.max(1L, deadline - System.nanoTime()))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean awaitFuture(CompletableFuture<?> value, long deadline)
                throws InterruptedException {
            try {
                value.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                return true;
            } catch (CancellationException | ExecutionException ignored) {
                return true;
            } catch (TimeoutException failure) {
                return false;
            }
        }
    }

    private static final class DirectProxySelector extends ProxySelector {
        private static final DirectProxySelector INSTANCE = new DirectProxySelector();

        private DirectProxySelector() {}

        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            // No proxy connection can fail because this selector always uses a direct connection.
        }
    }
}
