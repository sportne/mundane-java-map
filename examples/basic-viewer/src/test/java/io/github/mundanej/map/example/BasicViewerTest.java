package io.github.mundanej.map.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.awt.MapView;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class BasicViewerTest {
    @Test
    void createsTheDocumentedMapWithoutOpeningAWindow() throws Exception {
        AtomicReference<MapView> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(BasicViewer.createMapView()));
        MapView map = result.get();

        assertEquals(1, map.layers().size());
        assertEquals(5, map.layers().getFirst().features().size());
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof PointGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof LineStringGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof PolygonGeometry));
        assertTrue(
                map.layers().getFirst().features().stream()
                        .noneMatch(
                                feature ->
                                        feature.symbol()
                                                instanceof
                                                io.github.mundanej.map.api.FeatureStyle));
    }
}
