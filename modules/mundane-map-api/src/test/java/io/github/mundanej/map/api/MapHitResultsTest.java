package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapHitResultsTest {
    @Test
    void preservesExactTopmostOrderAndDefensivelyCopies() {
        MapHit first = new MapHit(" layer ", "Feature");
        MapHit second = new MapHit("layer-2", "feature-2");
        List<MapHit> source = new ArrayList<>(List.of(first, second));

        MapHitResults results = MapHitResults.of(source);
        source.clear();

        assertEquals(2, results.size());
        assertEquals(first, results.topmost().orElseThrow());
        assertEquals(List.of(first, second), results.hits());
        assertEquals(
                List.of(first, second),
                java.util.stream.StreamSupport.stream(results.spliterator(), false).toList());
        assertEquals(results, MapHitResults.of(List.of(first, second)));
        assertEquals(results.hashCode(), MapHitResults.of(List.of(first, second)).hashCode());
        assertEquals("MapHitResults" + List.of(first, second), results.toString());
        assertThrows(UnsupportedOperationException.class, () -> results.hits().clear());
    }

    @Test
    void rejectsInvalidAndDuplicateIdentitiesAndAllowsEmptyResults() {
        MapHit hit = new MapHit("layer", "feature");
        assertThrows(IllegalArgumentException.class, () -> new MapHit(" ", "feature"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureSelection("layer", ""));
        assertThrows(IllegalArgumentException.class, () -> MapHitResults.of(List.of(hit, hit)));
        List<MapHit> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> MapHitResults.of(withNull));
        assertThrows(NullPointerException.class, () -> MapHitResults.of(null));
        assertTrue(MapHitResults.of(List.of()).topmost().isEmpty());
        assertEquals(
                new FeatureSelection("layer", "feature"), new FeatureSelection("layer", "feature"));
    }
}
