package io.github.mundanej.map.io.http.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.image.EncodedRasterDecodeOptions;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HttpXyzTilesTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("tiles", "Tiles");

    @Test
    void fetchesPngWithFixedHeadersAndReturnsDetachedPlacedRaster() throws IOException {
        byte[] image = image("png", new Color(12, 34, 56, 255));
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> encoding = new AtomicReference<>();
        AtomicReference<String> agent = new AtomicReference<>();
        HttpServer server =
                server(
                        exchange -> {
                            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                            encoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
                            agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                            respond(exchange, 200, "image/png", image);
                        });
        RasterSource source;
        try (HttpXyzTileClient client = client(server, defaults())) {
            source = client.fetch(XyzTileRegion.single(1, 1, 0), CancellationToken.none());
        } finally {
            server.stop(0);
        }

        assertEquals("image/png, image/jpeg", accept.get());
        assertEquals("identity", encoding.get());
        assertEquals("mundane-java-map", agent.get());
        assertEquals(256, source.metadata().width());
        assertEquals(
                "EPSG:3857",
                source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
        Envelope bounds = source.metadata().mapBounds().orElseThrow();
        assertEquals(0.0, bounds.minX(), 1.0e-9);
        assertEquals(0.0, bounds.minY(), 1.0e-9);
        assertEquals(20_037_508.342789244, bounds.maxX(), 1.0e-6);
        assertEquals(20_037_508.342789244, bounds.maxY(), 1.0e-6);
        var read =
                source.read(
                        new RasterRequest(new RasterWindow(0, 0, 256, 256), 1, 1, Optional.empty()),
                        CancellationToken.none());
        assertEquals(0x0c2238ff, read.pixels().rgbaAt(0, 0));
        source.close();
    }

    @Test
    void fetchesJpegUsingExplicitDecoder() throws IOException {
        byte[] image = image("jpg", new Color(200, 40, 20));
        HttpServer server = server(exchange -> respond(exchange, 200, "image/jpeg", image));
        try (HttpXyzTileClient client = client(server, defaults());
                RasterSource source =
                        client.fetch(XyzTileRegion.single(0, 0, 0), CancellationToken.none())) {
            int rgba =
                    source.read(
                                    new RasterRequest(
                                            new RasterWindow(0, 0, 256, 256),
                                            1,
                                            1,
                                            Optional.empty()),
                                    CancellationToken.none())
                            .pixels()
                            .rgbaAt(0, 0);
            assertTrue(((rgba >>> 24) & 0xff) > 190);
            assertEquals(0xff, rgba & 0xff);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidResponseProfilesAndOversizedBodies() throws IOException {
        byte[] image = image("png", Color.BLUE);
        assertFailure(200, "text/plain", image, defaults(), "HTTP_TILE_RESPONSE_INVALID");
        assertFailure(
                200, "image/png; charset=binary", image, defaults(), "HTTP_TILE_RESPONSE_INVALID");
        assertFailure(200, "image/png", image, optionsWithBodyLimit(32), "SOURCE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsUnsafeTemplatesRegionsCancellationAndClosedClients() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpXyzTemplate.parse("https://example.test/{z}/{x}.png"));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpXyzTemplate.parse("https://user@example.test/{z}/{x}/{y}.png"));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpXyzTemplate.parse("https://example.test/{z}/../{x}/{y}.png"));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpXyzTemplate.parse("https://example.test/{z}/{x}/{y}/{q}.png"));
        for (String port : List.of("", "0", "01", "65536")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            HttpXyzTemplate.parse(
                                    "https://example.test:" + port + "/{z}/{x}/{y}.png"));
        }
        assertThrows(IllegalArgumentException.class, () -> XyzTileRegion.single(2, 4, 0));

        HttpServer server =
                server(exchange -> respond(exchange, 200, "image/png", image("png", Color.RED)));
        HttpXyzTemplate template = template(server);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HttpXyzTiles.open(
                                IDENTITY,
                                template,
                                HttpXyzClientOptions.defaults(),
                                AwtRasterDecoders.level1()));
        HttpXyzTileClient client = client(server, defaults());
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () -> client.fetch(XyzTileRegion.single(0, 0, 0), () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        client.close();
        assertTrue(client.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> client.fetch(XyzTileRegion.single(0, 0, 0), CancellationToken.none()));
        server.stop(0);
    }

    @Test
    void rejectsIncompatibleDecodeAndOwnershipOptionsBeforeNetwork() throws IOException {
        HttpServer server = server(exchange -> exchange.close());
        try {
            HttpXyzClientOptions defaults = defaults();
            EncodedRasterDecodeOptions expected =
                    defaults.decodeOptions().expecting(EncodedRasterFormat.PNG);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> open(server, withDecodeOptions(expected)));

            EncodedRasterDecodeOptions tooSmallEncoded =
                    defaults.decodeOptions()
                            .withImageLimits(
                                    defaults.decodeOptions()
                                            .imageLimits()
                                            .withMaximumEncodedBytes(1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> open(server, withDecodeOptions(tooSmallEncoded)));

            RasterRequestLimits decodeLimits =
                    new RasterRequestLimits(65_536, 256, 65_536, 1, 262_144, 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            open(
                                    server,
                                    withDecodeOptions(
                                            defaults.decodeOptions()
                                                    .withDecodeLimits(decodeLimits))));
            assertThrows(IllegalArgumentException.class, () -> open(server, withOwnedLimit(1)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesEncodingHeadersDeclaredLengthAndImageDimensions() throws IOException {
        byte[] valid = image("png", Color.BLUE);
        HttpServer encoded =
                server(
                        exchange -> {
                            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                            respond(exchange, 200, "image/png", valid);
                        });
        assertFetchCode(encoded, defaults(), "HTTP_TILE_RESPONSE_INVALID");

        HttpServer headers = server(exchange -> respond(exchange, 200, "image/png", valid));
        assertFetchCode(headers, withHeaderCountLimit(1), "SOURCE_LIMIT_EXCEEDED");
        HttpServer headerCharacters =
                server(exchange -> respond(exchange, 200, "image/png", valid));
        assertFetchCode(headerCharacters, withHeaderCharacterLimit(8), "SOURCE_LIMIT_EXCEEDED");

        assertTruncatedDeclaredLength(valid);

        byte[] wrongSize = image("png", Color.BLUE, 32, 32);
        HttpServer dimensions = server(exchange -> respond(exchange, 200, "image/png", wrongSize));
        assertFetchCode(dimensions, defaults(), "HTTP_TILE_IMAGE_INVALID");
    }

    @Test
    void cancellationSettlesBeforeClientReuse() throws Exception {
        byte[] valid = image("png", Color.GREEN);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server =
                server(
                        exchange -> {
                            if (requests.getAndIncrement() == 0) {
                                firstStarted.countDown();
                                await(releaseFirst);
                            }
                            try {
                                respond(exchange, 200, "image/png", valid);
                            } catch (IOException ignored) {
                                exchange.close();
                            }
                        });
        AtomicBoolean cancel = new AtomicBoolean();
        try (HttpXyzTileClient client = client(server, defaults())) {
            CompletableFuture<RasterSource> running =
                    CompletableFuture.supplyAsync(
                            () -> client.fetch(new XyzTileRegion(1, 0, 0, 1, 0), cancel::get));
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            assertThrows(
                    IllegalStateException.class,
                    () -> client.fetch(new XyzTileRegion(1, 0, 0, 1, 0), CancellationToken.none()));
            cancel.set(true);
            releaseFirst.countDown();
            CompletionException failed = assertThrows(CompletionException.class, running::join);
            assertEquals(
                    "SOURCE_CANCELLED", ((SourceException) failed.getCause()).terminal().code());
            try (RasterSource reused =
                    client.fetch(XyzTileRegion.single(0, 0, 0), CancellationToken.none())) {
                assertEquals(
                        0x00ff00ff,
                        reused.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 256, 256),
                                                1,
                                                1,
                                                Optional.empty()),
                                        CancellationToken.none())
                                .pixels()
                                .rgbaAt(0, 0));
            }
        } finally {
            releaseFirst.countDown();
            server.stop(0);
        }
    }

    @Test
    void closeWinsAnInflightFetchAndRenderingUsesDetachedPixels() throws Exception {
        byte[] valid = image("png", new Color(36, 104, 172));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server =
                server(
                        exchange -> {
                            started.countDown();
                            await(release);
                            try {
                                respond(exchange, 200, "image/png", valid);
                            } catch (IOException ignored) {
                                exchange.close();
                            }
                        });
        HttpXyzTileClient client = client(server, defaults());
        CompletableFuture<RasterSource> running =
                CompletableFuture.supplyAsync(
                        () ->
                                client.fetch(
                                        new XyzTileRegion(1, 0, 0, 1, 0),
                                        CancellationToken.none()));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> closing = CompletableFuture.runAsync(client::close);
        release.countDown();
        closing.join();
        CompletionException failed = assertThrows(CompletionException.class, running::join);
        assertEquals(
                "HTTP_TILE_CLIENT_CLOSED", ((SourceException) failed.getCause()).terminal().code());
        server.stop(0);

        HttpServer renderServer = server(exchange -> respond(exchange, 200, "image/png", valid));
        RasterSource source;
        try (HttpXyzTileClient renderClient = client(renderServer, defaults())) {
            source = renderClient.fetch(XyzTileRegion.single(0, 0, 0), CancellationToken.none());
        } finally {
            renderServer.stop(0);
        }
        assertRendered(source);
    }

    @Test
    void enforcesRequestTimeoutWithoutLeakingLocator() throws IOException {
        HttpServer server =
                server(
                        exchange -> {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                            }
                            exchange.close();
                        });
        HttpXyzClientOptions options = withTimeouts(Duration.ofMillis(100));
        try (HttpXyzTileClient client = client(server, options)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    client.fetch(
                                            XyzTileRegion.single(0, 0, 0),
                                            CancellationToken.none()));
            assertEquals("HTTP_TILE_REQUEST_FAILED", failure.terminal().code());
            assertEquals(
                    Map.of("phase", "wait", "reason", "timeout"), failure.terminal().context());
            assertFalse(failure.getMessage().contains("127.0.0.1"));
        } finally {
            server.stop(0);
        }
    }

    private static void assertFailure(
            int status, String media, byte[] body, HttpXyzClientOptions options, String code)
            throws IOException {
        HttpServer server = server(exchange -> respond(exchange, status, media, body));
        try (HttpXyzTileClient client = client(server, options)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    client.fetch(
                                            XyzTileRegion.single(0, 0, 0),
                                            CancellationToken.none()));
            assertEquals(code, failure.terminal().code());
        } finally {
            server.stop(0);
        }
    }

    private static void assertFetchCode(
            HttpServer server, HttpXyzClientOptions options, String code) {
        try (HttpXyzTileClient client = client(server, options)) {
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    client.fetch(
                                            XyzTileRegion.single(0, 0, 0),
                                            CancellationToken.none()));
            assertEquals(code, failure.terminal().code());
        } finally {
            server.stop(0);
        }
    }

    private static void assertTruncatedDeclaredLength(byte[] body) throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> serving =
                    CompletableFuture.runAsync(
                            () -> {
                                try (var socket = server.accept();
                                        var input = socket.getInputStream();
                                        var output = socket.getOutputStream()) {
                                    int state = 0;
                                    while (state < 4) {
                                        int next = input.read();
                                        if (next < 0) {
                                            throw new IOException("request ended before headers");
                                        }
                                        state =
                                                switch (state) {
                                                    case 0 -> next == '\r' ? 1 : 0;
                                                    case 1 -> next == '\n' ? 2 : 0;
                                                    case 2 -> next == '\r' ? 3 : 0;
                                                    default -> next == '\n' ? 4 : 0;
                                                };
                                    }
                                    output.write(
                                            ("HTTP/1.1 200 OK\r\n"
                                                            + "Content-Type: image/png\r\n"
                                                            + "Content-Length: "
                                                            + (body.length + 10)
                                                            + "\r\nConnection: close\r\n\r\n")
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .US_ASCII));
                                    output.write(body);
                                    output.flush();
                                } catch (IOException failure) {
                                    throw new java.io.UncheckedIOException(failure);
                                }
                            });
            HttpXyzTemplate template =
                    HttpXyzTemplate.parse(
                            "http://127.0.0.1:" + server.getLocalPort() + "/{z}/{x}/{y}.png");
            try (HttpXyzTileClient client =
                    HttpXyzTiles.open(IDENTITY, template, defaults(), AwtRasterDecoders.level1())) {
                SourceException failure =
                        assertThrows(
                                SourceException.class,
                                () ->
                                        client.fetch(
                                                XyzTileRegion.single(0, 0, 0),
                                                CancellationToken.none()));
                assertEquals("HTTP_TILE_RESPONSE_INVALID", failure.terminal().code());
                assertEquals(
                        Map.of("field", "contentLength", "reason", "mismatch"),
                        failure.terminal().context());
            }
            serving.join();
        }
    }

    @Test
    void cancellationBeforeBodySubscriptionCancelsWithoutDemand() {
        BoundedBodySubscriber subscriber = new BoundedBodySubscriber(16, -1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        subscriber.cancel();

        subscriber.onSubscribe(
                new java.util.concurrent.Flow.Subscription() {
                    @Override
                    public void request(long count) {
                        requests.incrementAndGet();
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                    }
                });

        assertTrue(cancelled.get());
        assertEquals(0, requests.get());
    }

    @Test
    void missingBodySubscriberCancelsImmediatelyWithoutDemandOrRetention() {
        CancellingBodySubscriber subscriber = new CancellingBodySubscriber();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        subscriber.onSubscribe(
                new java.util.concurrent.Flow.Subscription() {
                    @Override
                    public void request(long count) {
                        requests.incrementAndGet();
                    }

                    @Override
                    public void cancel() {
                        cancelled.set(true);
                    }
                });
        assertTrue(cancelled.get());
        assertEquals(0, requests.get());
        assertEquals(0, subscriber.getBody().toCompletableFuture().join().length);
    }

    @Test
    void rejectsConcurrencyAboveTheTileCeiling() {
        HttpXyzLimits limits = defaults().limits();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new HttpXyzLimits(
                                limits.templateCharacters(),
                                limits.zoom(),
                                1,
                                limits.regionAxisTiles(),
                                2,
                                limits.responseHeaders(),
                                limits.headerNameOrValueCharacters(),
                                limits.aggregateHeaderCharacters(),
                                limits.responseBodyBytes(),
                                limits.cumulativeResponseBytes(),
                                limits.ownedBytes(),
                                limits.warnings(),
                                limits.cacheEntries(),
                                limits.cacheBytes()));
    }

    @Test
    void closeReportsItsInterruptedResourceAndRestoresInterruptStatus() throws IOException {
        HttpServer server = server(exchange -> exchange.close());
        HttpXyzTileClient client = client(server, defaults());
        Thread.currentThread().interrupt();
        try {
            SourceException failure = assertThrows(SourceException.class, client::close);
            assertEquals("HTTP_TILE_CLOSE_FAILED", failure.terminal().code());
            assertEquals("interrupted", failure.terminal().context().get("reason"));
            assertEquals("client", failure.terminal().context().get("resource"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            if (!client.isClosed()) {
                client.close();
            }
            server.stop(0);
        }
    }

    @Test
    void coversExactWorldBoundariesAndRendersOutOfOrderRegionDeterministically() throws Exception {
        double world = WebMercatorProjection.WORLD_LIMIT;
        assertEquals(
                new XyzTileRegion(1, 0, 0, 1, 1),
                XyzTileRegion.covering(new Envelope(-world, -world, world, world), 1));
        assertEquals(
                XyzTileRegion.single(1, 1, 0),
                XyzTileRegion.covering(new Envelope(0, 0, world, world), 1));
        assertEquals(
                new XyzTileRegion(1, 0, 0, 1, 0),
                XyzTileRegion.covering(
                        new Envelope(Math.nextDown(0.0), 1.0, Math.nextUp(0.0), 2.0), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> XyzTileRegion.covering(new Envelope(1, 1, 1, 2), 4));

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch allSubmitted = new CountDownLatch(4);
        HttpServer server =
                server(
                        exchange -> {
                            int[] coordinate = coordinate(exchange);
                            int current = active.incrementAndGet();
                            maximumActive.accumulateAndGet(current, Math::max);
                            try {
                                allSubmitted.countDown();
                                if (!allSubmitted.await(5, TimeUnit.SECONDS)) {
                                    throw new IOException("configured concurrency was not reached");
                                }
                                Thread.sleep((3L - (coordinate[1] * 2L + coordinate[0])) * 30L);
                                Color color =
                                        List.of(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
                                                .get(coordinate[1] * 2 + coordinate[0]);
                                respond(exchange, 200, "image/png", image("png", color));
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                exchange.close();
                            } finally {
                                active.decrementAndGet();
                            }
                        });
        try (HttpXyzTileClient client = client(server, defaults());
                RasterSource source =
                        client.fetch(new XyzTileRegion(1, 0, 0, 1, 1), CancellationToken.none())) {
            assertEquals(512, source.metadata().width());
            assertEquals(512, source.metadata().height());
            assertEquals(0xff0000ff, pixel(source, 32, 32));
            assertEquals(0x00ff00ff, pixel(source, 288, 32));
            assertEquals(0x0000ffff, pixel(source, 32, 288));
            assertEquals(0xffff00ff, pixel(source, 288, 288));
            assertEquals(4, maximumActive.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingTilesAreTransparentWarningsAndMemoryCacheUsesCommittedLru() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean failLast = new AtomicBoolean();
        HttpServer server =
                server(
                        exchange -> {
                            int[] coordinate = coordinate(exchange);
                            requests.incrementAndGet();
                            if (coordinate[0] == 1 && coordinate[1] == 0) {
                                respond(exchange, 404, "text/plain", new byte[0]);
                            } else if (coordinate[0] == 3 && failLast.get()) {
                                respond(exchange, 500, "text/plain", new byte[0]);
                            } else {
                                respond(exchange, 200, "image/png", image("png", Color.MAGENTA));
                            }
                        });
        try (HttpXyzTileClient client = client(server, memoryOptions(2))) {
            try (RasterSource source =
                    client.fetch(new XyzTileRegion(2, 0, 0, 1, 0), CancellationToken.none())) {
                assertEquals(0xff00ffff, pixel(source, 10, 10));
                assertEquals(0x00000000, pixel(source, 300, 10));
                assertEquals(1, source.openingDiagnostics().entries().size());
                assertEquals(
                        "HTTP_TILE_MISSING",
                        source.openingDiagnostics().entries().getFirst().code());
                assertEquals(
                        "404",
                        source.openingDiagnostics().entries().getFirst().context().get("status"));
            }
            int afterFirst = requests.get();
            try (RasterSource cached =
                    client.fetch(XyzTileRegion.single(2, 0, 0), CancellationToken.none())) {
                assertEquals(256, cached.metadata().width());
                assertEquals(afterFirst, requests.get());
            }
            try (RasterSource admitted =
                    client.fetch(XyzTileRegion.single(2, 2, 0), CancellationToken.none())) {
                assertEquals(256, admitted.metadata().width());
                assertEquals(afterFirst + 1, requests.get());
            }
            try (RasterSource promoted =
                    client.fetch(XyzTileRegion.single(2, 0, 0), CancellationToken.none())) {
                assertEquals(0xff00ffff, pixel(promoted, 10, 10));
                assertEquals(afterFirst + 1, requests.get());
            }
            failLast.set(true);
            SourceException rolledBack =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    client.fetch(
                                            new XyzTileRegion(2, 0, 0, 3, 0),
                                            CancellationToken.none()));
            assertEquals("HTTP_TILE_RESPONSE_INVALID", rolledBack.terminal().code());
            assertEquals(afterFirst + 3, requests.get());
            failLast.set(false);
            try (RasterSource admitted =
                    client.fetch(XyzTileRegion.single(2, 3, 0), CancellationToken.none())) {
                assertEquals(256, admitted.metadata().height());
                assertEquals(afterFirst + 4, requests.get());
            }
            try (RasterSource evicted =
                    client.fetch(XyzTileRegion.single(2, 2, 0), CancellationToken.none())) {
                assertEquals(256, evicted.metadata().height());
                assertEquals(afterFirst + 5, requests.get());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelledRegionLeavesCacheMembershipAndOrderUnchanged() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean blockLast = new AtomicBoolean();
        CountDownLatch lastStarted = new CountDownLatch(1);
        CountDownLatch releaseLast = new CountDownLatch(1);
        HttpServer server =
                server(
                        exchange -> {
                            int[] coordinate = coordinate(exchange);
                            requests.incrementAndGet();
                            if (coordinate[0] == 3 && blockLast.get()) {
                                lastStarted.countDown();
                                await(releaseLast);
                            }
                            try {
                                respond(exchange, 200, "image/png", image("png", Color.CYAN));
                            } catch (IOException ignored) {
                                exchange.close();
                            }
                        });
        AtomicBoolean cancel = new AtomicBoolean();
        try (HttpXyzTileClient client = client(server, memoryOptions(2))) {
            try (RasterSource first =
                            client.fetch(XyzTileRegion.single(2, 0, 0), CancellationToken.none());
                    RasterSource second =
                            client.fetch(XyzTileRegion.single(2, 2, 0), CancellationToken.none());
                    RasterSource promoted =
                            client.fetch(XyzTileRegion.single(2, 0, 0), CancellationToken.none())) {
                assertEquals(256, first.metadata().width());
                assertEquals(256, second.metadata().width());
                assertEquals(256, promoted.metadata().width());
            }
            blockLast.set(true);
            CompletableFuture<RasterSource> running =
                    CompletableFuture.supplyAsync(
                            () -> client.fetch(new XyzTileRegion(2, 0, 0, 3, 0), cancel::get));
            assertTrue(lastStarted.await(5, TimeUnit.SECONDS));
            cancel.set(true);
            releaseLast.countDown();
            CompletionException cancelled = assertThrows(CompletionException.class, running::join);
            assertEquals(
                    "SOURCE_CANCELLED", ((SourceException) cancelled.getCause()).terminal().code());
            blockLast.set(false);
            int afterCancelled = requests.get();
            try (RasterSource admitted =
                            client.fetch(XyzTileRegion.single(2, 3, 0), CancellationToken.none());
                    RasterSource evicted =
                            client.fetch(XyzTileRegion.single(2, 2, 0), CancellationToken.none())) {
                assertEquals(256, admitted.metadata().width());
                assertEquals(256, evicted.metadata().width());
            }
            assertEquals(afterCancelled + 2, requests.get());
        } finally {
            releaseLast.countDown();
            server.stop(0);
        }
    }

    private static HttpXyzTileClient client(HttpServer server, HttpXyzClientOptions options) {
        return open(server, options);
    }

    private static HttpXyzTileClient open(HttpServer server, HttpXyzClientOptions options) {
        return HttpXyzTiles.open(IDENTITY, template(server), options, AwtRasterDecoders.level1());
    }

    private static HttpXyzTemplate template(HttpServer server) {
        return HttpXyzTemplate.parse(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png");
    }

    private static HttpXyzClientOptions defaults() {
        return HttpXyzClientOptions.defaults().allowingHttp();
    }

    private static HttpXyzClientOptions optionsWithBodyLimit(int bytes) {
        HttpXyzClientOptions defaults = defaults();
        HttpXyzLimits limits = defaults.limits();
        return new HttpXyzClientOptions(
                defaults.schemePolicy(),
                new HttpXyzLimits(
                        limits.templateCharacters(),
                        limits.zoom(),
                        limits.tilesPerRequest(),
                        limits.regionAxisTiles(),
                        limits.concurrency(),
                        limits.responseHeaders(),
                        limits.headerNameOrValueCharacters(),
                        limits.aggregateHeaderCharacters(),
                        bytes,
                        limits.cumulativeResponseBytes(),
                        limits.ownedBytes(),
                        limits.warnings(),
                        limits.cacheEntries(),
                        limits.cacheBytes()),
                defaults.snapshotLimits(),
                defaults.decodeOptions(),
                defaults.cachePolicy(),
                defaults.connectTimeout(),
                defaults.requestTimeout(),
                defaults.operationTimeout(),
                defaults.closeTimeout());
    }

    private static HttpXyzClientOptions withTimeouts(Duration duration) {
        HttpXyzClientOptions defaults = defaults();
        return new HttpXyzClientOptions(
                defaults.schemePolicy(),
                defaults.limits(),
                defaults.snapshotLimits(),
                defaults.decodeOptions(),
                defaults.cachePolicy(),
                duration,
                duration,
                duration,
                defaults.closeTimeout());
    }

    private static HttpXyzClientOptions withDecodeOptions(EncodedRasterDecodeOptions decode) {
        HttpXyzClientOptions defaults = defaults();
        return new HttpXyzClientOptions(
                defaults.schemePolicy(),
                defaults.limits(),
                defaults.snapshotLimits(),
                decode,
                defaults.cachePolicy(),
                defaults.connectTimeout(),
                defaults.requestTimeout(),
                defaults.operationTimeout(),
                defaults.closeTimeout());
    }

    private static HttpXyzClientOptions withOwnedLimit(long bytes) {
        HttpXyzClientOptions defaults = defaults();
        HttpXyzLimits limits = defaults.limits();
        return withLimits(
                new HttpXyzLimits(
                        limits.templateCharacters(),
                        limits.zoom(),
                        limits.tilesPerRequest(),
                        limits.regionAxisTiles(),
                        limits.concurrency(),
                        limits.responseHeaders(),
                        limits.headerNameOrValueCharacters(),
                        limits.aggregateHeaderCharacters(),
                        limits.responseBodyBytes(),
                        limits.cumulativeResponseBytes(),
                        bytes,
                        limits.warnings(),
                        limits.cacheEntries(),
                        Math.min(bytes, limits.cacheBytes())));
    }

    private static HttpXyzClientOptions withHeaderCountLimit(int count) {
        HttpXyzClientOptions defaults = defaults();
        HttpXyzLimits limits = defaults.limits();
        return withLimits(
                new HttpXyzLimits(
                        limits.templateCharacters(),
                        limits.zoom(),
                        limits.tilesPerRequest(),
                        limits.regionAxisTiles(),
                        limits.concurrency(),
                        count,
                        limits.headerNameOrValueCharacters(),
                        limits.aggregateHeaderCharacters(),
                        limits.responseBodyBytes(),
                        limits.cumulativeResponseBytes(),
                        limits.ownedBytes(),
                        limits.warnings(),
                        limits.cacheEntries(),
                        limits.cacheBytes()));
    }

    private static HttpXyzClientOptions withHeaderCharacterLimit(int characters) {
        HttpXyzClientOptions defaults = defaults();
        HttpXyzLimits limits = defaults.limits();
        return withLimits(
                new HttpXyzLimits(
                        limits.templateCharacters(),
                        limits.zoom(),
                        limits.tilesPerRequest(),
                        limits.regionAxisTiles(),
                        limits.concurrency(),
                        limits.responseHeaders(),
                        characters,
                        limits.aggregateHeaderCharacters(),
                        limits.responseBodyBytes(),
                        limits.cumulativeResponseBytes(),
                        limits.ownedBytes(),
                        limits.warnings(),
                        limits.cacheEntries(),
                        limits.cacheBytes()));
    }

    private static HttpXyzClientOptions withLimits(HttpXyzLimits limits) {
        HttpXyzClientOptions defaults = defaults();
        return new HttpXyzClientOptions(
                defaults.schemePolicy(),
                limits,
                defaults.snapshotLimits(),
                defaults.decodeOptions(),
                defaults.cachePolicy(),
                defaults.connectTimeout(),
                defaults.requestTimeout(),
                defaults.operationTimeout(),
                defaults.closeTimeout());
    }

    private static HttpXyzClientOptions memoryOptions(int entries) {
        HttpXyzClientOptions defaults = defaults();
        HttpXyzLimits limits = defaults.limits();
        return new HttpXyzClientOptions(
                defaults.schemePolicy(),
                new HttpXyzLimits(
                        limits.templateCharacters(),
                        limits.zoom(),
                        limits.tilesPerRequest(),
                        limits.regionAxisTiles(),
                        limits.concurrency(),
                        limits.responseHeaders(),
                        limits.headerNameOrValueCharacters(),
                        limits.aggregateHeaderCharacters(),
                        limits.responseBodyBytes(),
                        limits.cumulativeResponseBytes(),
                        limits.ownedBytes(),
                        limits.warnings(),
                        entries,
                        entries * 256L * 256L * 4L),
                defaults.snapshotLimits(),
                defaults.decodeOptions(),
                HttpTileCachePolicy.MEMORY,
                defaults.connectTimeout(),
                defaults.requestTimeout(),
                defaults.operationTimeout(),
                defaults.closeTimeout());
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler)
            throws IOException {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.setExecutor(command -> Thread.ofVirtual().start(command));
        server.start();
        return server;
    }

    private static int[] coordinate(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        int zoomEnd = path.indexOf('/', 1);
        int xEnd = path.indexOf('/', zoomEnd + 1);
        int suffix = path.indexOf('.', xEnd + 1);
        return new int[] {
            Integer.parseInt(path.substring(zoomEnd + 1, xEnd)),
            Integer.parseInt(path.substring(xEnd + 1, suffix))
        };
    }

    private static int pixel(RasterSource source, int column, int row) {
        return source.read(
                        new RasterRequest(
                                new RasterWindow(column, row, 1, 1), 1, 1, Optional.empty()),
                        CancellationToken.none())
                .pixels()
                .rgbaAt(0, 0);
    }

    private static void respond(HttpExchange exchange, int status, String media, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", media);
        exchange.sendResponseHeaders(status, body.length);
        try (exchange;
                var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static byte[] image(String format, Color color) throws IOException {
        return image(format, color, 256, 256);
    }

    private static byte[] image(String format, Color color, int width, int height)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static void assertRendered(RasterSource source) throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    SymbolRendererRegistry.builtIn());
                    try {
                        view.setSize(256, 256);
                        view.setViewport(
                                new MapViewport(
                                        256,
                                        256,
                                        0,
                                        0,
                                        2.0 * WebMercatorProjection.WORLD_LIMIT / 256.0));
                        view.setLayerBindings(
                                List.of(MapLayerBinding.ownedRaster("tile", "Tile", source)));
                        BufferedImage rendered =
                                new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                        var graphics = rendered.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        assertEquals(0xff2468ac, rendered.getRGB(128, 128));
                    } finally {
                        view.close();
                    }
                });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }
}
