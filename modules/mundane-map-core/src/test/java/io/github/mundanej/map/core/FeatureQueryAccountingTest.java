package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureQueryAccountingTest {
    @Test
    void acceptsExactRecordLimitAndRejectsTheNextProspectively() {
        FeatureQueryLimits limits = new FeatureQueryLimits(1, 1, 1, 1, 16, 128, 1);
        FeatureQueryAccounting accounting = new FeatureQueryAccounting("source", limits);
        accounting.recordExamined();
        accounting.recordReturned(point(), 0, CancellationToken.none());
        SourceException failure = assertThrows(SourceException.class, accounting::recordExamined);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("recordsExamined", failure.terminal().context().get("limit"));
    }

    @Test
    void pollsCancellationWithinLargePackedCoordinateAccounting() {
        double[] packed = new double[4_097 * 2];
        FeatureRecord record =
                new FeatureRecord(
                        "id", "", new MultiPointGeometry(CoordinateSequence.of(packed)), Map.of());
        CountingToken token = new CountingToken();
        FeatureQueryAccounting accounting =
                new FeatureQueryAccounting("source", FeatureQueryLimits.LEVEL_1);
        SourceException failure =
                assertThrows(
                        SourceException.class, () -> accounting.recordReturned(record, 0, token));
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(2, token.polls);
    }

    @Test
    void singularPolygonChargesEveryHoleReferenceSlot() {
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(0, 0, 2, 0, 2, 2, 0, 0),
                        List.of(CoordinateSequence.of(1, 1, 1.5, 1, 1.5, 1.5, 1, 1)));
        FeatureRecord record = new FeatureRecord("id", "", polygon, Map.of());
        FeatureQueryLimits oneByteShort = new FeatureQueryLimits(1, 1, 8, 1, 2, 139, 1);
        FeatureQueryAccounting accounting = new FeatureQueryAccounting("source", oneByteShort);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> accounting.recordReturned(record, 0, CancellationToken.none()));
        assertEquals("ownedPayloadBytes", failure.terminal().context().get("limit"));
        assertEquals("140", failure.terminal().context().get("requested"));
    }

    @Test
    void retainedReferenceSlotsAreChargedProspectively() {
        FeatureQueryAccounting accounting =
                new FeatureQueryAccounting("source", new FeatureQueryLimits(1, 1, 1, 1, 2, 27, 1));
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> accounting.recordReturned(point(), 1, CancellationToken.none()));
        assertEquals("28", failure.terminal().context().get("requested"));
        assertThrows(
                IllegalArgumentException.class,
                () -> accounting.recordReturned(point(), -1, CancellationToken.none()));
    }

    @Test
    void cumulativeArithmeticOverflowSaturatesAndFails() throws ReflectiveOperationException {
        FeatureQueryAccounting accounting =
                new FeatureQueryAccounting(
                        "source",
                        new FeatureQueryLimits(
                                Long.MAX_VALUE,
                                Long.MAX_VALUE,
                                Long.MAX_VALUE,
                                Long.MAX_VALUE,
                                Long.MAX_VALUE,
                                Long.MAX_VALUE,
                                1));
        Field payload = FeatureQueryAccounting.class.getDeclaredField("payloadBytes");
        payload.setAccessible(true);
        payload.setLong(accounting, Long.MAX_VALUE - 1);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> accounting.recordReturned(point(), 0, CancellationToken.none()));
        assertEquals(Long.toString(Long.MAX_VALUE), failure.terminal().context().get("requested"));
        assertEquals("ownedPayloadBytes", failure.terminal().context().get("limit"));
    }

    @Test
    void recordsReturnedAcceptsOneLessAndExactThenRejectsOneOver() {
        FeatureRecord record = point();
        assertAccepted(record, limits(2, 100, 100, 100, 1_000), 1);
        assertAccepted(record, limits(1, 100, 100, 100, 1_000), 1);
        FeatureQueryAccounting accounting =
                new FeatureQueryAccounting("source", limits(1, 100, 100, 100, 1_000));
        accounting.recordReturned(record, 0, CancellationToken.none());
        assertLimitFailure(accounting, record, "recordsReturned", "2", "1");
    }

    @Test
    void coordinatesAcceptsOneLessAndExactThenRejectsOneOver() {
        FeatureRecord record =
                new FeatureRecord(
                        "a",
                        "",
                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        Map.of());
        assertAccepted(record, limits(100, 3, 100, 100, 1_000), 1);
        assertAccepted(record, limits(100, 2, 100, 100, 1_000), 1);
        assertLimitFailure(
                new FeatureQueryAccounting("source", limits(100, 1, 100, 100, 1_000)),
                record,
                "coordinatesReturned",
                "2",
                "1");
    }

    @Test
    void attributeValuesAcceptOneLessAndExactThenRejectOneOver() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("a", 1L);
        attributes.put("b", 2L);
        FeatureRecord record =
                new FeatureRecord("a", "", new PointGeometry(new Coordinate(0, 0)), attributes);
        assertAccepted(record, limits(100, 100, 3, 100, 1_000), 1);
        assertAccepted(record, limits(100, 100, 2, 100, 1_000), 1);
        assertLimitFailure(
                new FeatureQueryAccounting("source", limits(100, 100, 1, 100, 1_000)),
                record,
                "attributeValuesReturned",
                "2",
                "1");
    }

    @Test
    void decodedTextAcceptsOneLessAndExactThenRejectsOneOver() {
        FeatureRecord record =
                new FeatureRecord("a", "", new PointGeometry(new Coordinate(0, 0)), Map.of());
        assertAccepted(record, limits(100, 100, 100, 2, 1_000), 1);
        assertAccepted(record, limits(100, 100, 100, 1, 1_000), 1);
        FeatureQueryAccounting accounting =
                new FeatureQueryAccounting("source", limits(100, 100, 100, 1, 1_000));
        accounting.recordReturned(record, 0, CancellationToken.none());
        assertLimitFailure(accounting, record, "decodedTextCharactersReturned", "2", "1");
    }

    @Test
    void payloadAcceptsOneLessAndExactThenRejectsOneOver() {
        FeatureRecord record =
                new FeatureRecord("a", "", new PointGeometry(new Coordinate(0, 0)), Map.of());
        assertAccepted(record, limits(100, 100, 100, 100, 19), 1);
        assertAccepted(record, limits(100, 100, 100, 100, 18), 1);
        assertLimitFailure(
                new FeatureQueryAccounting("source", limits(100, 100, 100, 100, 17)),
                record,
                "ownedPayloadBytes",
                "18",
                "17");
    }

    private static FeatureQueryLimits limits(
            long recordsReturned, long coordinates, long attributes, long text, long payload) {
        return new FeatureQueryLimits(
                100, recordsReturned, coordinates, attributes, text, payload, 1);
    }

    private static void assertAccepted(
            FeatureRecord record, FeatureQueryLimits limits, int repetitions) {
        FeatureQueryAccounting accounting = new FeatureQueryAccounting("source", limits);
        for (int index = 0; index < repetitions; index++) {
            assertDoesNotThrow(
                    () -> accounting.recordReturned(record, 0, CancellationToken.none()));
        }
    }

    private static void assertLimitFailure(
            FeatureQueryAccounting accounting,
            FeatureRecord record,
            String limit,
            String requested,
            String maximum) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> accounting.recordReturned(record, 0, CancellationToken.none()));
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(requested, failure.terminal().context().get("requested"));
        assertEquals(maximum, failure.terminal().context().get("maximum"));
    }

    private static FeatureRecord point() {
        return new FeatureRecord("id", "", new PointGeometry(new Coordinate(0, 0)), Map.of());
    }

    private static final class CountingToken implements CancellationToken {
        private int polls;

        @Override
        public boolean isCancellationRequested() {
            return ++polls == 2;
        }
    }
}
