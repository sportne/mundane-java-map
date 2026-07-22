package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.core.MapViewport;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StaticMapBackgroundCacheTest {
    @Test
    void rendersOverscanOffTheEdtAndPublishesOnTheEdt() throws Exception {
        CountDownLatch published = new CountDownLatch(1);
        AtomicBoolean renderedOnEdt = new AtomicBoolean(true);
        AtomicBoolean publishedOnEdt = new AtomicBoolean();
        AtomicReference<StaticMapBackgroundCache.Snapshot> result = new AtomicReference<>();
        try (StaticMapBackgroundCache cache =
                new StaticMapBackgroundCache(
                        viewport -> {
                            renderedOnEdt.set(EventQueue.isDispatchThread());
                            return image(viewport);
                        },
                        snapshot -> {
                            publishedOnEdt.set(EventQueue.isDispatchThread());
                            result.set(snapshot);
                            published.countDown();
                        },
                        failure -> {
                            throw new AssertionError(failure);
                        })) {
            cache.request(new MapViewport(100, 60, 0.0, 0.0, 10.0));
            assertTrue(published.await(10, TimeUnit.SECONDS));
        }

        StaticMapBackgroundCache.Snapshot snapshot = result.get();
        assertFalse(renderedOnEdt.get());
        assertTrue(publishedOnEdt.get());
        assertEquals(200, snapshot.renderViewport().width());
        assertEquals(120, snapshot.renderViewport().height());
        assertEquals(200, snapshot.image().getWidth());
        MapViewport requested = new MapViewport(100, 60, 0.0, 0.0, 10.0);
        assertTrue(StaticMapBackgroundCache.covers(snapshot.renderViewport(), requested));
        assertTrue(
                StaticMapBackgroundCache.covers(
                        snapshot.renderViewport(), requested.panByPixels(40.0, 0.0)));
        assertFalse(
                StaticMapBackgroundCache.covers(
                        snapshot.renderViewport(), requested.panByPixels(60.0, 0.0)));
        assertFalse(
                StaticMapBackgroundCache.covers(
                        snapshot.renderViewport(), requested.zoomAt(50.0, 30.0, 2.0)));
    }

    @Test
    void coalescesPendingRequestsToTheNewestViewport() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch twoPublished = new CountDownLatch(2);
        AtomicReference<MapViewport> latest = new AtomicReference<>();
        AtomicBoolean first = new AtomicBoolean(true);
        try (StaticMapBackgroundCache cache =
                new StaticMapBackgroundCache(
                        viewport -> {
                            if (first.compareAndSet(true, false)) {
                                firstStarted.countDown();
                                await(releaseFirst);
                            }
                            return image(viewport);
                        },
                        snapshot -> {
                            latest.set(snapshot.renderViewport());
                            twoPublished.countDown();
                        },
                        failure -> {
                            throw new AssertionError(failure);
                        })) {
            cache.request(new MapViewport(100, 60, 0.0, 0.0, 10.0));
            assertTrue(firstStarted.await(10, TimeUnit.SECONDS));
            cache.request(new MapViewport(100, 60, 100.0, 0.0, 10.0));
            cache.request(new MapViewport(100, 60, 200.0, 0.0, 10.0));
            releaseFirst.countDown();
            assertTrue(twoPublished.await(10, TimeUnit.SECONDS));
        }
        assertEquals(200.0, latest.get().centerX());
    }

    private static BufferedImage image(MapViewport viewport) {
        return new BufferedImage(viewport.width(), viewport.height(), BufferedImage.TYPE_INT_RGB);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
