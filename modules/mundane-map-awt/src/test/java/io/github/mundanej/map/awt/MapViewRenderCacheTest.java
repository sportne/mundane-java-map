package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MapViewRenderCacheTest {
    @Test
    void offEdtCloseMarshalsCleanupAfterEdtPaint() throws Exception {
        MapView view = warmCachedView(BuiltInMarker.CROSS);

        assertDoesNotThrow(view::close);
        assertCacheEmpty(view);
    }

    @Test
    void preInterruptedCloseWaitsForCleanupAndRestoresInterrupt() throws Exception {
        MapView view = warmCachedView(BuiltInMarker.DIAMOND);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread closer =
                new Thread(
                        () -> {
                            Thread.currentThread().interrupt();
                            try {
                                view.close();
                            } catch (Throwable thrown) {
                                failure.set(thrown);
                            } finally {
                                interruptRestored.set(Thread.currentThread().isInterrupted());
                            }
                        },
                        "pre-interrupted-map-close");

        closer.start();
        closer.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(closer.isAlive(), "close did not finish after EDT cleanup");
        assertNull(failure.get());
        assertTrue(interruptRestored.get());
        assertCacheEmpty(view);
    }

    @Test
    void interruptionDuringCloseCannotReturnBeforeEdtCleanup() throws Exception {
        MapView view = warmCachedView(BuiltInMarker.TRIANGLE);
        CountDownLatch edtBlocked = new CountDownLatch(1);
        CountDownLatch releaseEdt = new CountDownLatch(1);
        CountDownLatch closeCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        EventQueue.invokeLater(
                () -> {
                    edtBlocked.countDown();
                    awaitUninterruptibly(releaseEdt);
                });
        assertTrue(edtBlocked.await(5, TimeUnit.SECONDS), "EDT blocker did not start");

        Thread closer =
                new Thread(
                        () -> {
                            try {
                                view.close();
                            } catch (Throwable thrown) {
                                failure.set(thrown);
                            } finally {
                                interruptRestored.set(Thread.currentThread().isInterrupted());
                                closeCompleted.countDown();
                            }
                        },
                        "interrupted-map-close");
        closer.start();
        try {
            awaitWaiting(closer);
            closer.interrupt();
            assertFalse(
                    closeCompleted.await(100, TimeUnit.MILLISECONDS),
                    "close returned while its EDT cleanup was blocked");
        } finally {
            releaseEdt.countDown();
        }
        assertTrue(closeCompleted.await(5, TimeUnit.SECONDS), "close did not finish");
        closer.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(closer.isAlive());
        assertNull(failure.get());
        assertTrue(interruptRestored.get());
        assertCacheEmpty(view);
    }

    @Test
    void vectorTemplateColdAndWarmPaintsAreEquivalentAndWarmBuildsNothing() throws Exception {
        onEdt(
                () -> {
                    MapView view = view(AwtRenderCacheMode.VECTOR_TEMPLATE);
                    try {
                        view.setLayers(List.of(layer(marker(BuiltInMarker.STAR))));
                        Paint cold = paint(view);
                        Paint warm = paint(view);
                        assertEquals(1, cold.result().cacheMetrics().vectorTemplate().misses());
                        assertEquals(1, cold.result().cacheMetrics().vectorTemplate().admissions());
                        assertEquals(1, warm.result().cacheMetrics().vectorTemplate().hits());
                        assertEquals(0, warm.result().cacheMetrics().vectorTemplate().builds());
                        assertEquals(cold.pixels(), warm.pixels());
                    } finally {
                        view.close();
                    }
                });
    }

    @Test
    void publicViewUsesEvidenceRetainedVectorTemplateCache() throws Exception {
        onEdt(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    try {
                        view.setSize(100, 100);
                        view.setLayers(List.of(layer(marker(BuiltInMarker.DIAMOND))));
                        paint(view);
                        assertEquals(
                                1, paint(view).result().cacheMetrics().vectorTemplate().hits());
                    } finally {
                        view.close();
                    }
                });
    }

    @Test
    void clearAndCloseDiscardRetainedTemplates() throws Exception {
        onEdt(
                () -> {
                    MapView view = view(AwtRenderCacheMode.VECTOR_TEMPLATE);
                    view.setLayers(List.of(layer(marker(BuiltInMarker.TRIANGLE))));
                    paint(view);
                    assertEquals(1, paint(view).result().cacheMetrics().vectorTemplate().hits());
                    view.clearVectorTemplateCacheForEvidence();
                    assertEquals(1, paint(view).result().cacheMetrics().vectorTemplate().misses());
                    view.close();
                });
    }

    @Test
    void disabledEvidenceModeBuildsWithoutRetaining() throws Exception {
        onEdt(
                () -> {
                    MapView view = view(AwtRenderCacheMode.DISABLED);
                    try {
                        view.setLayers(List.of(layer(marker(BuiltInMarker.SQUARE))));
                        CachePartitionMetrics first =
                                paint(view).result().cacheMetrics().vectorTemplate();
                        CachePartitionMetrics second =
                                paint(view).result().cacheMetrics().vectorTemplate();
                        assertEquals(0, first.requests());
                        assertEquals(1, first.builds());
                        assertEquals(0, second.requests());
                        assertEquals(1, second.builds());
                    } finally {
                        view.close();
                    }
                });
    }

    private static MapView view(AwtRenderCacheMode mode) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builtIn(),
                        ScreenGeometryOptimizationMode.LEVEL1,
                        mode);
        view.setSize(100, 100);
        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
        return view;
    }

    private static MapView warmCachedView(BuiltInMarker builtInMarker) throws Exception {
        AtomicReference<MapView> reference = new AtomicReference<>();
        onEdt(
                () -> {
                    MapView view = view(AwtRenderCacheMode.VECTOR_TEMPLATE);
                    view.setLayers(List.of(layer(marker(builtInMarker))));
                    paint(view);
                    assertEquals(1, paint(view).result().cacheMetrics().vectorTemplate().hits());
                    reference.set(view);
                });
        return reference.get();
    }

    private static void assertCacheEmpty(MapView view) throws Exception {
        onEdt(
                () ->
                        assertEquals(
                                0,
                                paint(view)
                                        .result()
                                        .cacheMetrics()
                                        .vectorTemplate()
                                        .currentEntries()));
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(
                Thread.State.WAITING, thread.getState(), "closer did not wait for EDT cleanup");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static InMemoryLayer layer(VectorMarkerSymbol marker) {
        Feature feature =
                new Feature("point", "", new PointGeometry(new Coordinate(0, 0)), Map.of(), marker);
        return new InMemoryLayer("layer", "layer", List.of(feature));
    }

    private static VectorMarkerSymbol marker(BuiltInMarker marker) {
        return VectorMarkerSymbol.filledScreen(
                BuiltInMarkers.path(marker),
                BuiltInMarkers.viewBox(),
                Rgba.rgb(210, 70, 30),
                18,
                1);
    }

    private static Paint paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            ScreenGeometryPaintResult result = view.paintWithScreenGeometryResult(graphics);
            return new Paint(
                    result,
                    java.util.Arrays.stream(image.getRGB(0, 0, 100, 100, null, 0, 100))
                            .boxed()
                            .toList());
        } finally {
            graphics.dispose();
        }
    }

    private static void onEdt(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) {
                throw exception;
            }
            if (failure.get() instanceof Error error) {
                throw error;
            }
            throw new InvocationTargetException(failure.get());
        }
    }

    private record Paint(ScreenGeometryPaintResult result, List<Integer> pixels) {}

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
