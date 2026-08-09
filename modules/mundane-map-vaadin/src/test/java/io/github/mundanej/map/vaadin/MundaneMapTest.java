package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MundaneMapTest {
    @Test
    void configuresSnapshotsBackgroundViewportAndFit() {
        MundaneMap map = new MundaneMap();
        assertEquals("100%", map.getWidth());
        assertEquals("400px", map.getHeight());
        assertTrue(map.fitToContents(0) == false);
        assertThrows(IllegalArgumentException.class, () -> map.fitToContents(Double.NaN));

        InMemoryLayer source = layer();
        map.setSnapshotLayers(List.of(source));
        assertEquals(1, map.snapshotLayers().size());
        assertFalse(map.snapshotLayers().getFirst() == source);
        assertTrue(map.diagnostic().isEmpty());
        map.setBackground(new Rgba(1, 2, 3, 4));
        assertEquals(new Rgba(1, 2, 3, 4), map.background());
        map.setViewport(new MapViewport(320, 200, 50, 60, 2));
        assertEquals(50, map.viewport().centerX());
        MundaneMapException oversized =
                assertThrows(
                        MundaneMapException.class,
                        () -> map.setViewport(MapViewport.initial(16_385, 1)));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, oversized.code());
        assertTrue(map.fitToContents(10));
        assertEquals(new Coordinate(5, 5), map.viewport().visibleWorldEnvelope().center());
        assertEquals(1, map.encodedSceneForTest().get("protocolVersion"));
    }

    @Test
    void acceptsOnlyCurrentFiniteSettledViewportAndNotifiesListeners() {
        MundaneMap map = new MundaneMap();
        map.setSnapshotLayers(List.of(layer()));
        map.setViewport(new MapViewport(100, 80, 0, 0, 1));
        List<MapViewport> accepted = new ArrayList<>();
        var registration = map.addViewportChangeListener(accepted::add);

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                0,
                120,
                90,
                3,
                4,
                0.5);
        assertEquals(new MapViewport(120, 90, 3, 4, 0.5), map.viewport());
        assertEquals(List.of(map.viewport()), accepted);
        assertTrue(map.diagnostic().isEmpty());

        registration.remove();
        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                1,
                121,
                91,
                4,
                5,
                0.6);
        assertEquals(1, accepted.size());

        map.acceptSettledViewport(
                2,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                2,
                1,
                1,
                0,
                0,
                1);
        assertEquals(
                MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                map.diagnostic().orElseThrow().code());

        map.acceptSettledViewport(1, -1, 0, 0, 2, 1, 1, 0, 0, 1);
        assertEquals(MundaneMapException.STALE_GENERATION, map.diagnostic().orElseThrow().code());

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                2,
                0,
                1,
                Double.NaN,
                0,
                1);
        assertEquals(MundaneMapException.NON_FINITE_VALUE, map.diagnostic().orElseThrow().code());

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                1,
                1,
                1,
                0,
                0,
                1);
        assertEquals(
                MundaneMapException.EVENT_SEQUENCE_INVALID, map.diagnostic().orElseThrow().code());

        map.setEnabled(false);
        map.acceptSettledViewport(9, -1, -1, -1, -1, 1, 1, 0, 0, 1);
        assertEquals(MundaneMapException.DISABLED, map.diagnostic().orElseThrow().code());
    }

    @Test
    void rejectedWrappedViewportDoesNotAdvanceTheAuthoritativeGeneration() {
        MundaneMap map = new MundaneMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setHorizontalWrap(wrap);
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        long generation = map.viewportGenerationForTest();

        boolean accepted =
                map.acceptSettledViewport(
                        1,
                        (double) map.componentGenerationForTest(),
                        (double) map.sceneGenerationForTest(),
                        (double) generation,
                        0,
                        100,
                        100,
                        wrap.period() * (HorizontalWrap.COPY_INDEX_HARD_MAXIMUM + 1L),
                        0,
                        1);

        assertFalse(accepted);
        assertEquals(generation, map.viewportGenerationForTest());
        assertEquals(0.0, map.viewport().centerX());
        assertEquals("WORLD_WRAP_PRECISION_EXCEEDED", map.diagnostic().orElseThrow().code());
        map.close();
    }

    @Test
    void reportsBoundedClientFailuresAndStaleMessages() {
        MundaneMap map = new MundaneMap();
        map.setSnapshotLayers(List.of(layer()));
        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                "x".repeat(5000));
        MundaneMapException failure = map.diagnostic().orElseThrow();
        assertEquals(MundaneMapException.CLIENT_FAILURE, failure.code());
        assertEquals(Map.of("phase", "canvas"), failure.context());

        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                null);
        assertEquals(MundaneMapException.CLIENT_FAILURE, map.diagnostic().orElseThrow().code());

        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                MundaneMapException.BROWSER_CAPABILITY_UNSUPPORTED);
        assertEquals(
                MundaneMapException.BROWSER_CAPABILITY_UNSUPPORTED,
                map.diagnostic().orElseThrow().code());

        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                MundaneMapException.RESOURCE_UNAVAILABLE);
        assertEquals(
                MundaneMapException.RESOURCE_UNAVAILABLE, map.diagnostic().orElseThrow().code());

        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED);
        assertEquals(
                SymbolException.HATCH_SEGMENT_LIMIT_EXCEEDED,
                map.diagnostic().orElseThrow().code());

        map.acceptClientFailure(9, 0, 0, "stale");
        assertEquals(
                MundaneMapException.PROTOCOL_VERSION_UNSUPPORTED,
                map.diagnostic().orElseThrow().code());
    }

    @Test
    void coalescesSettledViewportsAtTheAuthoritativeTenPerSecondRate() {
        long[] now = {10};
        List<Runnable> pendingFlushes = new ArrayList<>();
        MundaneMap map = new MundaneMap(() -> now[0], pendingFlushes::add);
        List<MapViewport> notifications = new ArrayList<>();
        map.addViewportChangeListener(notifications::add);
        map.setSnapshotLayers(List.of(layer()));
        for (int sequence = 0; sequence < 10; sequence++) {
            map.acceptSettledViewport(
                    1,
                    (double) map.componentGenerationForTest(),
                    (double) map.sceneGenerationForTest(),
                    (double) map.viewportGenerationForTest(),
                    sequence,
                    100,
                    100,
                    sequence,
                    0,
                    1);
        }
        assertEquals(9, map.viewport().centerX());
        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                10,
                100,
                100,
                10,
                0,
                1);
        assertEquals(9, map.viewport().centerX());
        assertEquals(11, map.viewportGenerationForTest());
        assertEquals(1, pendingFlushes.size());
        now[0] += 101_000_000;
        pendingFlushes.removeFirst().run();
        assertEquals(10, map.viewport().centerX());
        assertEquals(11, notifications.size());
        now[0] += 100_000_000;
        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                11,
                100,
                100,
                11,
                0,
                1);
        assertEquals(11, map.viewport().centerX());
        assertEquals(12, notifications.size());
    }

    @Test
    void rejectsOversizedSettledViewportWithoutReplacingTheAcceptedValue() {
        MundaneMap map = new MundaneMap();
        MapViewport before = map.viewport();

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                0,
                16_385,
                1,
                0,
                0,
                1);

        assertEquals(before, map.viewport());
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, map.diagnostic().orElseThrow().code());
    }

    @Test
    void attachDetachDisableReattachAndCloseAreIdempotent() {
        MundaneMap map = new MundaneMap();
        map.setSnapshotLayers(List.of(layer()));
        map.onAttach(new AttachEvent(map, true));
        long firstAttach = map.componentGenerationForTest();
        map.onDetach(new DetachEvent(map));
        map.onAttach(new AttachEvent(map, false));
        assertTrue(map.componentGenerationForTest() > firstAttach);
        map.setEnabled(false);
        assertFalse(map.isEnabled());
        map.setEnabled(true);
        assertTrue(map.isEnabled());

        map.close();
        map.close();
        assertTrue(map.snapshotLayers().isEmpty());
        assertThrows(MundaneMapException.class, () -> map.setSnapshotLayers(List.of()));
        assertThrows(MundaneMapException.class, () -> map.setViewport(MapViewport.initial(1, 1)));
        assertThrows(MundaneMapException.class, () -> map.setBackground(Rgba.TRANSPARENT));
        assertThrows(MundaneMapException.class, () -> map.addViewportChangeListener(value -> {}));
        assertThrows(MundaneMapException.class, () -> map.fitToContents(0));
        assertThrows(MundaneMapException.class, () -> map.setEnabled(false));
        map.acceptSettledViewport(1, 0, 0, 0, 0, 1, 1, 0, 0, 1);
        assertEquals(MundaneMapException.CLOSED, map.diagnostic().orElseThrow().code());
        map.acceptClientFailure(1, 0, 0, "failure");
        assertEquals(MundaneMapException.CLOSED, map.diagnostic().orElseThrow().code());
        map.onAttach(new AttachEvent(map, false));
        map.onDetach(new DetachEvent(map));
    }

    @Test
    void diagnosticExceptionOwnsStableValidatedContext() {
        Map<String, String> source = new java.util.LinkedHashMap<>();
        source.put("key", "value");
        MundaneMapException exception = new MundaneMapException("CODE", "Message", source);
        source.clear();
        assertEquals("CODE", exception.code());
        assertEquals(Map.of("key", "value"), exception.context());
        assertThrows(UnsupportedOperationException.class, () -> exception.context().put("x", "y"));
        assertThrows(
                NullPointerException.class,
                () -> assertEquals("", new MundaneMapException(null, "m", Map.of()).code()));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals("", new MundaneMapException(" ", "m", Map.of()).code()));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals("", new MundaneMapException("c", " ", Map.of()).code()));
        assertThrows(
                NullPointerException.class,
                () -> assertTrue(new MundaneMapException("c", "m", null).context().isEmpty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertTrue(
                                new MundaneMapException("c", "m", Map.of(" ", "v"))
                                        .context()
                                        .isEmpty()));
        assertThrows(
                NullPointerException.class,
                () ->
                        assertTrue(
                                new MundaneMapException(
                                                "c",
                                                "m",
                                                java.util.Collections.singletonMap("k", null))
                                        .context()
                                        .isEmpty()));
        assertSame(exception, exception.fillInStackTrace());
    }

    private static InMemoryLayer layer() {
        VectorPath square =
                VectorPath.builder()
                        .moveTo(0, 0)
                        .lineTo(1, 0)
                        .lineTo(1, 1)
                        .lineTo(0, 1)
                        .close()
                        .build();
        Feature feature =
                new Feature(
                        "feature",
                        "Feature",
                        new PointGeometry(new Coordinate(5, 5)),
                        Map.of(),
                        VectorMarkerSymbol.filledScreen(
                                square, new Envelope(0, 0, 1, 1), Rgba.rgb(10, 20, 30), 8, 1));
        return new InMemoryLayer("layer", "Layer", List.of(feature));
    }
}
