package io.github.mundanej.map.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.awt.MapView;
import org.junit.jupiter.api.Test;

class BasicViewerTest {
    @Test
    void createsTheDocumentedMapWithoutOpeningAWindow() {
        MapView map = BasicViewer.createMapView();

        assertEquals(1, map.layers().size());
        assertEquals(5, map.layers().getFirst().features().size());
    }
}

