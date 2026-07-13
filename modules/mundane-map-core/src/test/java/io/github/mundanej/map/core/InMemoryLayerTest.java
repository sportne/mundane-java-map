package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class InMemoryLayerTest {
    @Test
    void copiesFeaturesAndComputesEnvelope() {
        List<Feature> features = new ArrayList<>();
        features.add(point("a", 1.0, 2.0));
        features.add(point("b", 4.0, -1.0));

        InMemoryLayer layer = new InMemoryLayer("points", "Points", features);
        features.clear();

        assertEquals(2, layer.features().size());
        assertEquals(new Envelope(1.0, -1.0, 4.0, 2.0), layer.envelope().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> layer.features().clear());
    }

    private static Feature point(String id, double x, double y) {
        return new Feature(
                id,
                id,
                new PointGeometry(new Coordinate(x, y)),
                Map.of(),
                FeatureStyle.point(Rgba.rgb(1, 2, 3), 5.0));
    }
}
