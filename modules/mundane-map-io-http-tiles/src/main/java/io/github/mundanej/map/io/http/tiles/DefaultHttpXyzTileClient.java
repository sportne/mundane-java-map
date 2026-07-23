package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RgbaPixelBuffer;
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
import java.util.concurrent.atomic.AtomicReference;
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
                        2,
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
        FetchOperation operation = beginFetch();
        boolean published = false;
        try {
            checkpoint(cancellation, operation, "fetch");
            URI uri = template.resolve(region, options.limits().templateCharacters());
            HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(options.requestTimeout())
                            .header("Accept", "image/png, image/jpeg")
                            .header("Accept-Encoding", "identity")
                            .header("User-Agent", "mundane-java-map")
                            .GET()
                            .build();
            checkpoint(cancellation, operation, "fetch");
            ResponseProfile profile = new ResponseProfile();
            CompletableFuture<HttpResponse<byte[]>> future =
                    client.sendAsync(request, info -> subscriber(info, profile, operation));
            operation.registerFuture(future);
            HttpResponse<byte[]> response = await(future, profile, cancellation, operation);
            checkpoint(cancellation, operation, "decode");
            RgbaPixelBuffer pixels =
                    decode(response.body(), profile.format, cancellation, operation);
            checkpoint(cancellation, operation, "publish");
            RasterSource source =
                    new DetachedHttpTileRasterSource(
                            identity, region.singleTileBounds(), options.snapshotLimits(), pixels);
            synchronized (lifecycle) {
                if (closed || operation.isCloseRequested()) {
                    source.close();
                    throw clientClosed("publish");
                }
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
        validateHeaders(response, profile);
        BoundedBodySubscriber subscriber =
                new BoundedBodySubscriber(options.limits().responseBodyBytes(), profile.length);
        operation.registerSubscriber(subscriber);
        return subscriber;
    }

    private void validateHeaders(HttpResponse.ResponseInfo response, ResponseProfile profile) {
        int headerCount = 0;
        long aggregate = 0;
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            headerCount = Math.addExact(headerCount, entry.getValue().size());
            for (String value : entry.getValue()) {
                if (entry.getKey().length() > options.limits().headerNameOrValueCharacters()
                        || value.length() > options.limits().headerNameOrValueCharacters()) {
                    throw limit(
                            "headerCharacters",
                            Math.max(entry.getKey().length(), value.length()),
                            options.limits().headerNameOrValueCharacters());
                }
                aggregate = Math.addExact(aggregate, entry.getKey().length() + value.length());
            }
        }
        if (headerCount > options.limits().responseHeaders()) {
            throw limit("headers", headerCount, options.limits().responseHeaders());
        }
        if (aggregate > options.limits().aggregateHeaderCharacters()) {
            throw limit(
                    "headerCharacters", aggregate, options.limits().aggregateHeaderCharacters());
        }
        if (response.statusCode() != 200) {
            throw invalidStatus(response.statusCode());
        }
        List<String> types = response.headers().allValues("Content-Type");
        if (types.isEmpty()) {
            throw invalidResponse("contentType", "missing");
        }
        if (types.size() != 1) {
            throw invalidResponse("contentType", "duplicate");
        }
        String media = types.getFirst().strip().toLowerCase(Locale.ROOT);
        if (media.equals("image/png")) {
            profile.format = EncodedRasterFormat.PNG;
        } else if (media.equals("image/jpeg")) {
            profile.format = EncodedRasterFormat.JPEG;
        } else {
            throw invalidResponse("contentType", "unsupported");
        }
        List<String> encodings = response.headers().allValues("Content-Encoding");
        if (encodings.size() > 1) {
            throw invalidResponse("contentEncoding", "duplicate");
        }
        if (encodings.size() == 1 && !encodings.getFirst().strip().equalsIgnoreCase("identity")) {
            throw invalidResponse("contentEncoding", "unsupported");
        }
        List<String> lengths = response.headers().allValues("Content-Length");
        if (lengths.size() > 1) {
            throw invalidResponse("contentLength", "duplicate");
        }
        if (!lengths.isEmpty()) {
            try {
                String text = lengths.getFirst();
                if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
                    throw new NumberFormatException("non-decimal");
                }
                profile.length = Long.parseLong(text);
            } catch (NumberFormatException failure) {
                throw invalidResponse("contentLength", "syntax");
            }
            if (profile.length > options.limits().responseBodyBytes()) {
                throw limit("tileBodyBytes", profile.length, options.limits().responseBodyBytes());
            }
        }
    }

    private HttpResponse<byte[]> await(
            CompletableFuture<HttpResponse<byte[]>> future,
            ResponseProfile profile,
            CancellationToken cancellation,
            FetchOperation operation) {
        long deadline = System.nanoTime() + options.operationTimeout().toNanos();
        while (true) {
            checkpoint(cancellation, operation, "fetch");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw requestFailed("wait", "timeout");
            }
            try {
                return future.get(
                        Math.min(remaining, WAIT_QUANTUM.toNanos()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException ignored) {
                // Poll cancellation, close, and the operation deadline.
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw requestFailed("wait", "interrupted");
            } catch (ExecutionException failure) {
                throw mapFailure(failure.getCause(), profile, cancellation, operation);
            } catch (CancellationException failure) {
                checkpoint(cancellation, operation, "fetch");
                throw requestFailed("wait", "other");
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
                    "tileBodyBytes",
                    (long) options.limits().responseBodyBytes() + 1,
                    options.limits().responseBodyBytes());
        }
        if (cause instanceof BoundedBodySubscriber.BodyLengthException) {
            return invalidResponse("contentLength", "mismatch");
        }
        checkpoint(cancellation, operation, "fetch");
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return requestFailed("wait", "timeout");
        }
        if (cause instanceof UnknownHostException) {
            return requestFailed("dns", "io");
        }
        if (cause instanceof ConnectException) {
            return requestFailed("connect", "io");
        }
        if (cause instanceof SSLException) {
            return requestFailed("tls", "protocol");
        }
        if (cause instanceof IOException && profile.length >= 0) {
            return invalidResponse("contentLength", "mismatch");
        }
        if (cause instanceof ProtocolException) {
            return requestFailed("body", "protocol");
        }
        return requestFailed("send", cause instanceof IOException ? "io" : "other");
    }

    private RgbaPixelBuffer decode(
            byte[] bytes,
            EncodedRasterFormat format,
            CancellationToken cancellation,
            FetchOperation operation) {
        EncodedRasterDecodeOptions decode =
                options.decodeOptions().expecting(format).expectingDimensions(256, 256);
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
                return throwLimit(failure);
            }
            if (code.equals("IMAGE_DIMENSIONS_MISMATCH")) {
                throw HttpTileDiagnostics.failure(
                        identity.id(),
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
                        "HTTP_TILE_IMAGE_INVALID",
                        "HTTP tile image is invalid",
                        Map.of("reason", "decode", "imageCode", code));
            }
            throw new IllegalStateException("Image decoder returned an unexpected checked code");
        }
    }

    private RgbaPixelBuffer throwLimit(SourceException failure) {
        throw HttpTileDiagnostics.failure(
                identity.id(),
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
        if (!region.isSingleTile()) {
            throw new IllegalArgumentException("This client slice accepts exactly one XYZ tile");
        }
        if (region.zoom() > options.limits().zoom()) {
            throw new IllegalArgumentException("XYZ zoom exceeds configured limits");
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

    private SourceException invalidStatus(int status) {
        return HttpTileDiagnostics.failure(
                identity.id(),
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

    private SourceException invalidResponse(String field, String reason) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                "HTTP_TILE_RESPONSE_INVALID",
                "HTTP tile response is outside the supported profile",
                Map.of("field", field, "reason", reason));
    }

    private SourceException requestFailed(String phase, String reason) {
        return HttpTileDiagnostics.failure(
                identity.id(),
                "HTTP_TILE_REQUEST_FAILED",
                "HTTP tile request failed",
                Map.of("phase", phase, "reason", reason));
    }

    private SourceException limit(String name, long requested, long maximum) {
        return HttpTileDiagnostics.failure(
                identity.id(),
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
        operation.cancelTransport();
        boolean settled = false;
        boolean interrupted = false;
        try {
            settled = operation.await(options.closeTimeout());
        } catch (InterruptedException failure) {
            interrupted = true;
        }
        synchronized (lifecycle) {
            if (activeOperation == operation) {
                activeOperation = null;
            }
            if (!settled) {
                closed = true;
            }
        }
        if (!settled) {
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
        }
        if (operation != null) {
            operation.requestClose();
        }
        client.shutdownNow();
        executor.shutdownNow();
        long deadline = System.nanoTime() + options.closeTimeout().toNanos();
        boolean interrupted = false;
        String resource = "body";
        try {
            if (operation != null) {
                if (!operation.await(remaining(deadline))) {
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
        private EncodedRasterFormat format;
        private long length = -1;
    }

    private static final class FetchOperation {
        private final AtomicBoolean closeRequested = new AtomicBoolean();
        private final AtomicBoolean transportCancelled = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<?>> future = new AtomicReference<>();
        private final AtomicReference<BoundedBodySubscriber> subscriber = new AtomicReference<>();

        void registerFuture(CompletableFuture<?> value) {
            future.set(value);
            if (transportCancelled.get()) {
                value.cancel(true);
            }
        }

        void registerSubscriber(BoundedBodySubscriber value) {
            subscriber.set(value);
            if (transportCancelled.get()) {
                value.cancel();
            }
        }

        void requestClose() {
            closeRequested.set(true);
            cancelTransport();
        }

        boolean isCloseRequested() {
            return closeRequested.get();
        }

        void cancelTransport() {
            transportCancelled.set(true);
            BoundedBodySubscriber body = subscriber.get();
            if (body != null) {
                body.cancel();
            }
            CompletableFuture<?> request = future.get();
            if (request != null) {
                request.cancel(true);
            }
        }

        boolean await(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            CompletableFuture<?> request = future.get();
            if (request != null && !awaitFuture(request, deadline)) {
                return false;
            }
            BoundedBodySubscriber body = subscriber.get();
            return body == null || body.await(Math.max(1L, deadline - System.nanoTime()));
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
