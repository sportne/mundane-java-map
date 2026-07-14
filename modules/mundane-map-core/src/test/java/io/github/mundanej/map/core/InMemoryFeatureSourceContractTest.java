package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryFeatureSourceContractTest {
    @Test
    void metadataFilteringAndProjectionPreserveExactOrder() {
        AttributeSchema schema =
                new AttributeSchema(
                        List.of(
                                new AttributeField("name", AttributeType.TEXT, false),
                                new AttributeField("count", AttributeType.INTEGER, false)));
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(
                                record("a", 0, Map.of("name", "A", "count", 1)),
                                record("b", 1, Map.of("name", "B", "count", 2))),
                        Optional.of(schema),
                        Optional.empty(),
                        FeatureSourceLimits.LEVEL_1);
        assertEquals(2, source.metadata().featureCount().orElseThrow());
        assertEquals(new Envelope(0, 0, 1, 0), source.metadata().extent().orElseThrow());
        assertEquals(DiagnosticReport.empty(), source.openingDiagnostics());
        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 1, 0)),
                        AttributeSelection.only(List.of("count", "name")),
                        Optional.empty());
        try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            assertTrue(cursor.advance());
            assertEquals("a", cursor.current().id());
            assertEquals(
                    List.of("count", "name"), List.copyOf(cursor.current().attributes().keySet()));
            assertTrue(cursor.advance());
            assertEquals("b", cursor.current().id());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void schemaValidationAndUnknownQueryFieldsAreDeterministic() {
        AttributeSchema schema =
                new AttributeSchema(List.of(new AttributeField("name", AttributeType.TEXT, false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        InMemoryFeatureSource.open(
                                new SourceIdentity("source", "Source"),
                                List.of(record("a", 0, Map.of("other", "A"))),
                                Optional.of(schema),
                                Optional.empty(),
                                FeatureSourceLimits.LEVEL_1));
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(record("a", 0, Map.of("name", "A"))),
                        Optional.of(schema),
                        Optional.empty(),
                        FeatureSourceLimits.LEVEL_1);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.openCursor(
                                        new FeatureQuery(
                                                Optional.empty(),
                                                AttributeSelection.only(List.of("missing")),
                                                Optional.empty()),
                                        CancellationToken.none()));
        assertEquals("SOURCE_QUERY_ATTRIBUTE_UNKNOWN", failure.terminal().code());
        source.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    @Test
    void cursorStatesAndSourceCloseReleaseSlotsExactlyOnce() {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"), List.of(record("a", 0, Map.of())));
        FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertThrows(IllegalStateException.class, cursor::current);
        assertTrue(cursor.advance());
        FeatureRecord retained = cursor.current();
        assertFalse(cursor.advance());
        assertFalse(cursor.advance());
        assertFalse(cursor.isClosed());
        assertThrows(IllegalStateException.class, cursor::current);
        cursor.close();
        cursor.close();
        assertTrue(cursor.isClosed());
        FeatureCursor second = source.openCursor(FeatureQuery.all(), CancellationToken.none());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertTrue(second.isClosed());
        assertEquals("a", retained.id());
        assertEquals(1, source.metadata().featureCount().orElseThrow());
        assertThrows(
                IllegalStateException.class,
                () -> source.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void tighterLimitFailureReleasesCursorAndAnIncreaseIsRejected() {
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(record("a", 0, Map.of()), record("b", 1, Map.of())));
        FeatureQueryLimits tooLarge =
                new FeatureQueryLimits(
                        FeatureQueryLimits.LEVEL_1.recordsExamined() + 1, 1, 1, 1, 1, 1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        source.openCursor(
                                new FeatureQuery(
                                        Optional.empty(),
                                        AttributeSelection.ALL,
                                        Optional.of(tooLarge)),
                                CancellationToken.none()));
        FeatureQueryLimits oneRecord = new FeatureQueryLimits(1, 2, 2, 1, 2, 128, 1);
        FeatureCursor cursor =
                source.openCursor(
                        new FeatureQuery(
                                Optional.empty(), AttributeSelection.NONE, Optional.of(oneRecord)),
                        CancellationToken.none());
        assertTrue(cursor.advance());
        SourceException failure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        source.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    @Test
    void preCancellationWinsBeforeUnknownFieldValidation() {
        AttributeSchema schema =
                new AttributeSchema(List.of(new AttributeField("name", AttributeType.TEXT, false)));
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(record("a", 0, Map.of("name", "A"))),
                        Optional.of(schema),
                        Optional.empty(),
                        FeatureSourceLimits.LEVEL_1);
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.openCursor(
                                        new FeatureQuery(
                                                Optional.empty(),
                                                AttributeSelection.only(List.of("missing")),
                                                Optional.empty()),
                                        cancellation.token()));
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
    }

    @Test
    void cancellationIsPolledDuringOnlyValidationAndProjection() {
        List<AttributeField> fields = new ArrayList<>();
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (int index = 0; index < 4_097; index++) {
            String name = "f" + index;
            fields.add(new AttributeField(name, AttributeType.INTEGER, false));
            attributes.put(name, (long) index);
            names.add(name);
        }
        InMemoryFeatureSource known =
                InMemoryFeatureSource.open(
                        new SourceIdentity("known", "Known"),
                        List.of(record("a", 0, attributes)),
                        Optional.of(new AttributeSchema(fields)),
                        Optional.empty(),
                        FeatureSourceLimits.LEVEL_1);
        CountingToken validationToken = new CountingToken(3);
        SourceException validationFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                known.openCursor(
                                        new FeatureQuery(
                                                Optional.empty(),
                                                AttributeSelection.only(names),
                                                Optional.empty()),
                                        validationToken));
        assertEquals("SOURCE_CANCELLED", validationFailure.terminal().code());
        assertEquals(3, validationToken.polls);

        InMemoryFeatureSource dynamic =
                InMemoryFeatureSource.open(
                        new SourceIdentity("dynamic", "Dynamic"),
                        List.of(record("a", 0, Map.of())));
        CountingToken projectionToken = new CountingToken(4);
        FeatureCursor cursor =
                dynamic.openCursor(
                        new FeatureQuery(
                                Optional.empty(), AttributeSelection.only(names), Optional.empty()),
                        projectionToken);
        SourceException projectionFailure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_CANCELLED", projectionFailure.terminal().code());
        assertEquals(4, projectionToken.polls);
        dynamic.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    private static FeatureRecord record(String id, double x, Map<String, Object> attributes) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, 0)), attributes);
    }

    private static final class CountingToken implements CancellationToken {
        private final int cancellationPoll;
        private int polls;

        private CountingToken(int cancellationPoll) {
            this.cancellationPoll = cancellationPoll;
        }

        @Override
        public boolean isCancellationRequested() {
            return ++polls == cancellationPoll;
        }
    }
}
