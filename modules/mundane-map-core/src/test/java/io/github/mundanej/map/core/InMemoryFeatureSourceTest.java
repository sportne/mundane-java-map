package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryFeatureSourceTest {
    @Test
    void filtersProjectsInStableOrderAndReleasesExhaustedCursor() {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(record("a", 0), record("b", 10)));
        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(-1, -1, 1, 1)),
                        AttributeSelection.NONE,
                        Optional.empty());
        try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("a", cursor.current().id());
            assertEquals(Map.of(), cursor.current().attributes());
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
        try (FeatureCursor ignored =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertFalse(ignored.isClosed());
        }
    }

    @Test
    void enforcesOneCursorAndCancellationWhileLeavingSourceReusable() {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"), List.of(record("a", 0)));
        FeatureCursor first = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
        first.close();
        CancellationSource cancellation = new CancellationSource();
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation.token());
        cancellation.cancel();
        SourceException failure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertFalse(source.isClosed());
        source.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    @Test
    void rejectsDuplicateIdsBeforeReturningSource() {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                InMemoryFeatureSource.open(
                                        new SourceIdentity("source", "Source"),
                                        List.of(record("a", 0), record("a", 1))));
        assertEquals("SOURCE_DUPLICATE_FEATURE_ID", failure.terminal().code());
        assertEquals("0", failure.terminal().context().get("firstIndex"));
    }

    private static FeatureRecord record(String id, double x) {
        return new FeatureRecord(
                id, id, new PointGeometry(new Coordinate(x, 0)), Map.of("name", id));
    }
}
