package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.VectorPath;
import java.awt.EventQueue;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AwtRenderCacheTest {
    @Test
    void vectorPreflightWeightAndIdentityKeyAreExact() throws Exception {
        onEdt(
                () -> {
                    VectorPath path = triangle();
                    VectorPath2D.StreamCounts counts = VectorPath2D.preflight(path);
                    assertEquals(4, counts.strokeCommands());
                    assertEquals(6, counts.strokeOrdinates());
                    assertEquals(4, counts.fillCommands());
                    assertEquals(6, counts.fillOrdinates());
                    assertEquals(284, AwtRenderCache.vectorTemplateWeight(path, counts));

                    AwtRenderCache cache = new AwtRenderCache();
                    AwtRenderCache.CacheEventCollector events =
                            new AwtRenderCache.CacheEventCollector();
                    admit(cache, events, path, true);
                    assertNotNull(cache.lookupVectorTemplate(path, events).hit());
                    VectorPath equalFresh =
                            VectorPath.of(path.toCommandArray(), path.toOrdinateArray());
                    assertNull(cache.lookupVectorTemplate(equalFresh, events).hit());
                });
    }

    @Test
    void successfulEntriesPromoteAndEvictInLruOrder() throws Exception {
        onEdt(
                () -> {
                    VectorPath first = VectorPath.builder().moveTo(0, 0).lineTo(1, 1).build();
                    VectorPath second = VectorPath.builder().moveTo(2, 2).lineTo(3, 3).build();
                    VectorPath third = VectorPath.builder().moveTo(4, 4).lineTo(5, 5).build();
                    AwtRenderCache cache = new AwtRenderCache(2, 20_000, 10_000);
                    AwtRenderCache.CacheEventCollector events =
                            new AwtRenderCache.CacheEventCollector();
                    admit(cache, events, first, true);
                    admit(cache, events, second, true);
                    AwtRenderCache.VectorLookup firstHit =
                            cache.lookupVectorTemplate(first, events);
                    cache.completeVectorTemplate(firstHit, first, firstHit.hit(), true, events);
                    admit(cache, events, third, true);

                    assertNull(cache.lookupVectorTemplate(second, events).hit());
                    assertNotNull(cache.lookupVectorTemplate(first, events).hit());
                    CachePartitionMetrics metrics = events.result().vectorTemplate();
                    assertEquals(1, metrics.evictions());
                    assertEquals(2, metrics.currentEntries());
                });
    }

    @Test
    void failedAndOversizedWorkDoesNotAdmitOrEvict() throws Exception {
        onEdt(
                () -> {
                    VectorPath small = VectorPath.builder().moveTo(0, 0).lineTo(1, 1).build();
                    long smallWeight =
                            AwtRenderCache.vectorTemplateWeight(
                                    small, VectorPath2D.preflight(small));
                    AwtRenderCache cache = new AwtRenderCache(1, smallWeight, smallWeight);
                    AwtRenderCache.CacheEventCollector events =
                            new AwtRenderCache.CacheEventCollector();
                    admit(cache, events, small, true);

                    VectorPath failed = VectorPath.builder().moveTo(2, 2).lineTo(3, 3).build();
                    admit(cache, events, failed, false);
                    VectorPath oversized =
                            VectorPath.builder().moveTo(0, 0).lineTo(1, 1).lineTo(2, 2).build();
                    AwtRenderCache.VectorLookup miss =
                            cache.lookupVectorTemplate(oversized, events);
                    cache.buildVectorTemplate(oversized, events);

                    assertNull(miss.hit());
                    assertNotNull(cache.lookupVectorTemplate(small, events).hit());
                    assertEquals(1, events.result().vectorTemplate().bypasses());
                    assertEquals(0, events.result().vectorTemplate().evictions());
                });
    }

    @Test
    void clearRemovesRetainedTemplates() throws Exception {
        onEdt(
                () -> {
                    VectorPath path = triangle();
                    AwtRenderCache cache = new AwtRenderCache();
                    AwtRenderCache.CacheEventCollector events =
                            new AwtRenderCache.CacheEventCollector();
                    admit(cache, events, path, true);
                    cache.clear();
                    assertNull(cache.lookupVectorTemplate(path, events).hit());
                });
    }

    @Test
    void mixedOpenClosedTemplateIsImmutableAcrossTransforms() throws Exception {
        onEdt(
                () -> {
                    VectorPath path =
                            VectorPath.builder()
                                    .moveTo(0, 0)
                                    .lineTo(1, 0)
                                    .lineTo(0, 1)
                                    .close()
                                    .moveTo(2, 2)
                                    .lineTo(3, 3)
                                    .build();
                    VectorPath2D.StreamCounts counts = VectorPath2D.preflight(path);
                    assertEquals(6, counts.strokeCommands());
                    assertEquals(10, counts.strokeOrdinates());
                    assertEquals(4, counts.fillCommands());
                    assertEquals(6, counts.fillOrdinates());
                    AwtRenderCache cache = new AwtRenderCache();
                    AwtRenderCache.CacheEventCollector events =
                            new AwtRenderCache.CacheEventCollector();
                    AwtRenderCache.VectorLookup lookup = cache.lookupVectorTemplate(path, events);
                    VectorPath2D.Converted converted = cache.buildVectorTemplate(path, events);
                    String strokeBefore = pathSignature(converted.strokePath());
                    String fillBefore = pathSignature(converted.fillPath());
                    cache.completeVectorTemplate(lookup, path, converted, true, events);

                    new AffineTransform(3, 0, 0, 2, 10, -5)
                            .createTransformedShape(converted.strokePath());
                    new AffineTransform(3, 0, 0, 2, 10, -5)
                            .createTransformedShape(converted.fillPath());

                    VectorPath2D.Converted hit = cache.lookupVectorTemplate(path, events).hit();
                    assertSame(converted, hit);
                    assertEquals(strokeBefore, pathSignature(hit.strokePath()));
                    assertEquals(fillBefore, pathSignature(hit.fillPath()));
                });
    }

    @Test
    void partitionLimitEqualityAndAdmissionFailureAreTransactional() {
        AwtRenderCache.Partition<String, String> equality =
                new AwtRenderCache.Partition<>(2, 100, 100);
        AwtRenderCache.MutablePartitionMetrics events =
                new AwtRenderCache.MutablePartitionMetrics();
        equality.admit("a", "A", 100, events);
        assertEquals(1, equality.size());
        assertEquals(100, equality.logicalBytes());
        equality.admit("oversized", "X", 101, events);
        assertEquals(1, equality.size());
        assertEquals(100, equality.logicalBytes());

        AwtRenderCache.Partition<String, String> transactional =
                new AwtRenderCache.Partition<>(2, 20, 20);
        transactional.admit("a", "A", 10, new AwtRenderCache.MutablePartitionMetrics());
        transactional.admit("b", "B", 10, new AwtRenderCache.MutablePartitionMetrics());
        assertThrows(
                ArithmeticException.class,
                () ->
                        transactional.admit(
                                "c",
                                "C",
                                10,
                                new AwtRenderCache.MutablePartitionMetrics(Long.MAX_VALUE, 0)));
        assertEquals(2, transactional.size());
        assertEquals(20, transactional.logicalBytes());
        assertEquals("A", transactional.lookup("a", new AwtRenderCache.MutablePartitionMetrics()));
        assertEquals("B", transactional.lookup("b", new AwtRenderCache.MutablePartitionMetrics()));
    }

    @Test
    void directCacheAccessRejectsNonEdtThreads() {
        AwtRenderCache cache = new AwtRenderCache();
        assertThrows(IllegalStateException.class, cache::clear);
        assertThrows(
                IllegalStateException.class,
                () ->
                        cache.lookupVectorTemplate(
                                triangle(), new AwtRenderCache.CacheEventCollector()));
    }

    private static void admit(
            AwtRenderCache cache,
            AwtRenderCache.CacheEventCollector events,
            VectorPath path,
            boolean rendered) {
        AwtRenderCache.VectorLookup lookup = cache.lookupVectorTemplate(path, events);
        VectorPath2D.Converted built = cache.buildVectorTemplate(path, events);
        cache.completeVectorTemplate(lookup, path, built, rendered, events);
    }

    private static VectorPath triangle() {
        return VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build();
    }

    private static String pathSignature(java.awt.Shape shape) {
        StringBuilder result = new StringBuilder();
        double[] coordinates = new double[6];
        PathIterator iterator = shape.getPathIterator(null);
        while (!iterator.isDone()) {
            int kind = iterator.currentSegment(coordinates);
            result.append(kind).append(':');
            int values =
                    switch (kind) {
                        case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 2;
                        case PathIterator.SEG_QUADTO -> 4;
                        case PathIterator.SEG_CUBICTO -> 6;
                        case PathIterator.SEG_CLOSE -> 0;
                        default -> throw new AssertionError(kind);
                    };
            for (int index = 0; index < values; index++) {
                result.append(Double.doubleToLongBits(coordinates[index])).append(',');
            }
            iterator.next();
        }
        return result.toString();
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
