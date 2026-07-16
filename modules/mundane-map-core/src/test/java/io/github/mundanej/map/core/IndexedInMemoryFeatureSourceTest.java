package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
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
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class IndexedInMemoryFeatureSourceTest {
    @Test
    void explicitIndexedQueriesMatchLinearSourceOrderAndAttributes() {
        List<FeatureRecord> records = grid(4_096, 64);
        InMemoryFeatureSource linear = source(records, false, FeatureSourceLimits.LEVEL_1);
        InMemoryFeatureSource indexed = source(records, true, FeatureSourceLimits.LEVEL_1);
        SplittableRandom random = new SplittableRandom(0x4d554e44414e454aL);
        for (int iteration = 0; iteration < 256; iteration++) {
            int column = random.nextInt(65) - 1;
            int row = random.nextInt(65) - 1;
            Envelope bounds = new Envelope(column, row, column + 4, row + 3);
            AttributeSelection selection =
                    iteration % 3 == 0
                            ? AttributeSelection.ALL
                            : iteration % 3 == 1
                                    ? AttributeSelection.NONE
                                    : AttributeSelection.only(List.of("ordinal"));
            FeatureQuery query = new FeatureQuery(Optional.of(bounds), selection, Optional.empty());
            assertEquals(readOutcome(linear, query), readOutcome(indexed, query));
        }
        for (Envelope bounds :
                List.of(
                        new Envelope(-180, -90, -180, -90),
                        new Envelope(180, 90, 180, 90),
                        new Envelope(-180, -90, 180, 90),
                        new Envelope(10_000, 10_000, 20_000, 20_000))) {
            FeatureQuery query =
                    new FeatureQuery(Optional.of(bounds), AttributeSelection.ALL, Optional.empty());
            assertEquals(readOutcome(linear, query), readOutcome(indexed, query));
        }
        assertEquals(linear.openingDiagnostics(), indexed.openingDiagnostics());
        FeatureQueryLimits one = new FeatureQueryLimits(1, 4_096, 4_096, 4_096, 10_000, 100_000, 1);
        FeatureQuery failing =
                new FeatureQuery(Optional.empty(), AttributeSelection.ALL, Optional.of(one));
        SourceException linearFailure =
                assertThrows(SourceException.class, () -> read(linear, failing));
        SourceException indexedFailure =
                assertThrows(SourceException.class, () -> read(indexed, failing));
        assertEquals(linearFailure.report(), indexedFailure.report());
        assertEquals(linearFailure.terminal(), indexedFailure.terminal());
        assertFalse(linear.spatialIndex() != null);
        assertTrue(indexed.spatialIndex() != null);
    }

    @Test
    void absentBoundsRemainLinearAndIndexedCandidatesCanAdmitTighterWork() {
        List<FeatureRecord> records = grid(256, 16);
        FeatureQueryLimits sixteen = new FeatureQueryLimits(16, 16, 16, 16, 1_000, 4_096, 1);
        FeatureSourceLimits sourceLimits = new FeatureSourceLimits(FeatureQueryLimits.LEVEL_1);
        InMemoryFeatureSource linear = source(records, false, sourceLimits);
        InMemoryFeatureSource indexed = source(records, true, sourceLimits);
        FeatureQuery selective =
                new FeatureQuery(
                        Optional.of(new Envelope(15, 15, 15, 15)),
                        AttributeSelection.NONE,
                        Optional.of(sixteen));
        assertThrows(SourceException.class, () -> read(linear, selective));
        FeatureRecord last = records.getLast();
        assertEquals(
                List.of(new FeatureRecord(last.id(), last.name(), last.geometry(), Map.of())),
                read(indexed, selective));

        FeatureQuery full =
                new FeatureQuery(Optional.empty(), AttributeSelection.NONE, Optional.of(sixteen));
        assertThrows(SourceException.class, () -> read(indexed, full));
    }

    @Test
    void everyIndexLimitHasExactBoundaryAndStructuredFailure() {
        List<FeatureRecord> records = grid(17, 17);
        PackedFeatureSpatialIndex.Layout layout =
                PackedFeatureSpatialIndex.Layout.forRecords(records.size());
        FeatureIndexLimits exact =
                new FeatureIndexLimits(
                        records.size(),
                        layout.retainedBytes(),
                        layout.buildBytes(),
                        layout.queryBytes());
        InMemoryFeatureSource source = openIndexed(records, exact);
        assertEquals(layout.retainedBytes(), source.spatialIndex().retainedBytes());
        openIndexed(
                        records,
                        new FeatureIndexLimits(
                                records.size() + 1,
                                layout.retainedBytes() + 1,
                                layout.buildBytes() + 1,
                                layout.queryBytes() + 1))
                .close();

        assertLimit(records, exact.withMaximumRecords(16), "records", 17, 16);
        assertLimit(
                records,
                exact.withMaximumRetainedBytes(layout.retainedBytes() - 1),
                "retainedBytes",
                layout.retainedBytes(),
                layout.retainedBytes() - 1);
        assertLimit(
                records,
                exact.withMaximumBuildBytes(layout.buildBytes() - 1),
                "buildBytes",
                layout.buildBytes(),
                layout.buildBytes() - 1);
        assertLimit(
                records,
                exact.withMaximumQueryBytes(layout.queryBytes() - 1),
                "queryBytes",
                layout.queryBytes(),
                layout.queryBytes() - 1);
    }

    @Test
    void recordLimitPrecedesDuplicateValidationAndCancelledPlanReleasesCursor() {
        List<FeatureRecord> duplicate = List.of(point(0, 0, 0), point(0, 1, 0));
        SourceException recordLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                openIndexed(
                                        duplicate,
                                        FeatureIndexLimits.LEVEL_1.withMaximumRecords(1)));
        assertEquals("records", recordLimit.terminal().context().get("limit"));
        SourceException duplicateId =
                assertThrows(
                        SourceException.class,
                        () -> openIndexed(duplicate, FeatureIndexLimits.LEVEL_1));
        assertEquals("SOURCE_DUPLICATE_FEATURE_ID", duplicateId.terminal().code());

        InMemoryFeatureSource indexed = source(grid(128, 16), true, FeatureSourceLimits.LEVEL_1);
        CountingToken cancellation = new CountingToken(2);
        FeatureCursor cursor =
                indexed.openCursor(
                        new FeatureQuery(
                                Optional.of(new Envelope(0, 0, 15, 7)),
                                AttributeSelection.ALL,
                                Optional.empty()),
                        cancellation);
        SourceException failure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    @Test
    void recordCeilingRejectsAnOversizedListBeforeSnapshotIteration() {
        List<FeatureRecord> hostile =
                new AbstractList<>() {
                    @Override
                    public FeatureRecord get(int index) {
                        throw new AssertionError("record snapshot must not start");
                    }

                    @Override
                    public int size() {
                        return FeatureIndexLimits.LEVEL_1.maximumRecords() + 1;
                    }
                };
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () -> openIndexed(hostile, FeatureIndexLimits.LEVEL_1));
        assertEquals("records", failure.terminal().context().get("limit"));
        assertEquals("1000001", failure.terminal().context().get("requested"));
    }

    @Test
    void packedArrayAddressabilityRejectsBeforeSnapshotIteration() {
        List<FeatureRecord> hostile =
                new AbstractList<>() {
                    @Override
                    public FeatureRecord get(int index) {
                        throw new AssertionError("record snapshot must not start");
                    }

                    @Override
                    public int size() {
                        return Integer.MAX_VALUE;
                    }
                };
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                openIndexed(
                                        hostile,
                                        FeatureIndexLimits.LEVEL_1.withMaximumRecords(
                                                Integer.MAX_VALUE)));
        assertEquals("arrayAddressability", failure.terminal().context().get("limit"));
        assertEquals(
                Integer.toString(Integer.MAX_VALUE), failure.terminal().context().get("requested"));
        assertEquals(
                Integer.toString(PackedFeatureSpatialIndex.MAXIMUM_PACKED_RECORDS),
                failure.terminal().context().get("maximum"));
    }

    @Test
    void cancellationBeforePublicationNeverExposesCurrentAndReleasesSlot() {
        InMemoryFeatureSource indexed =
                source(List.of(point(0, 0, 0)), true, FeatureSourceLimits.LEVEL_1);
        CountingToken cancellation = new CountingToken(8);
        FeatureCursor cursor =
                indexed.openCursor(
                        new FeatureQuery(
                                Optional.of(new Envelope(0, 0, 0, 0)),
                                AttributeSelection.ALL,
                                Optional.empty()),
                        cancellation);
        SourceException failure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertThrows(IllegalStateException.class, cursor::current);
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();
    }

    @Test
    void everyCursorTerminalPathReleasesOrClosesTheSingleCursorSlot() {
        InMemoryFeatureSource indexed = source(grid(32, 8), true, FeatureSourceLimits.LEVEL_1);

        FeatureCursor exhausted = indexed.openCursor(FeatureQuery.all(), CancellationToken.none());
        while (exhausted.advance()) {
            exhausted.current();
        }
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();

        FeatureCursor early = indexed.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertTrue(early.advance());
        early.close();
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();

        FeatureQueryLimits one = new FeatureQueryLimits(1, 32, 32, 32, 1_000, 4_096, 1);
        FeatureCursor failed =
                indexed.openCursor(
                        new FeatureQuery(
                                Optional.empty(), AttributeSelection.ALL, Optional.of(one)),
                        CancellationToken.none());
        assertTrue(failed.advance());
        assertThrows(SourceException.class, failed::advance);
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();

        SwitchToken token = new SwitchToken();
        FeatureCursor cancelled = indexed.openCursor(FeatureQuery.all(), token);
        token.cancelled = true;
        assertThrows(SourceException.class, cancelled::advance);
        indexed.openCursor(FeatureQuery.all(), CancellationToken.none()).close();

        FeatureCursor sourceClosed =
                indexed.openCursor(FeatureQuery.all(), CancellationToken.none());
        indexed.close();
        assertTrue(sourceClosed.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> indexed.openCursor(FeatureQuery.all(), CancellationToken.none()));
    }

    @Test
    void indexedCursorPreservesPartialPublicationSlotAndCloseLifecycle() {
        InMemoryFeatureSource indexed = source(grid(32, 8), true, FeatureSourceLimits.LEVEL_1);
        FeatureQueryLimits one = new FeatureQueryLimits(1, 32, 32, 32, 1_000, 4_096, 1);
        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 7, 1)),
                        AttributeSelection.NONE,
                        Optional.of(one));
        FeatureCursor cursor = indexed.openCursor(query, CancellationToken.none());
        assertTrue(cursor.advance());
        FeatureRecord published = cursor.current();
        SourceException failure = assertThrows(SourceException.class, cursor::advance);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("record:0", published.id());
        assertEquals("record:0", published.id());

        FeatureCursor replacement =
                indexed.openCursor(FeatureQuery.all(), CancellationToken.none());
        assertThrows(
                IllegalStateException.class,
                () -> indexed.openCursor(FeatureQuery.all(), CancellationToken.none()));
        replacement.close();
        FeatureCursor sourceClosed =
                indexed.openCursor(
                        new FeatureQuery(
                                Optional.of(new Envelope(0, 0, 7, 3)),
                                AttributeSelection.ALL,
                                Optional.empty()),
                        CancellationToken.none());
        assertTrue(sourceClosed.advance());
        indexed.close();
        assertTrue(sourceClosed.isClosed());
    }

    @Test
    void indexedOpeningDefensivelySnapshotsTheCallerList() {
        ArrayList<FeatureRecord> mutable = new ArrayList<>(grid(17, 17));
        InMemoryFeatureSource indexed = openIndexed(mutable, FeatureIndexLimits.LEVEL_1);
        FeatureRecord retained = mutable.getFirst();
        mutable.clear();
        List<FeatureRecord> read = read(indexed, FeatureQuery.all());
        assertEquals(17, read.size());
        assertEquals(retained, read.getFirst());
        assertNotSame(mutable, read);
        assertSame(retained, read.getFirst());
        indexed.close();
    }

    private static void assertLimit(
            List<FeatureRecord> records,
            FeatureIndexLimits limits,
            String name,
            long requested,
            long maximum) {
        SourceException failure =
                assertThrows(SourceException.class, () -> openIndexed(records, limits));
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals("spatialIndexBuild", failure.terminal().context().get("scope"));
        assertEquals(name, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(Long.toString(maximum), failure.terminal().context().get("maximum"));
    }

    private static InMemoryFeatureSource openIndexed(
            List<FeatureRecord> records, FeatureIndexLimits indexLimits) {
        return InMemoryFeatureSource.openIndexed(
                new SourceIdentity("source", "Source"),
                records,
                Optional.empty(),
                Optional.empty(),
                FeatureSourceLimits.LEVEL_1,
                indexLimits);
    }

    private static InMemoryFeatureSource source(
            List<FeatureRecord> records, boolean indexed, FeatureSourceLimits limits) {
        SourceIdentity identity = new SourceIdentity("source", "Source");
        return indexed
                ? InMemoryFeatureSource.openIndexed(
                        identity,
                        records,
                        Optional.empty(),
                        Optional.empty(),
                        limits,
                        FeatureIndexLimits.LEVEL_1)
                : InMemoryFeatureSource.open(
                        identity, records, Optional.empty(), Optional.empty(), limits);
    }

    private static List<FeatureRecord> read(InMemoryFeatureSource source, FeatureQuery query) {
        return readOutcome(source, query).records();
    }

    private static QueryOutcome readOutcome(InMemoryFeatureSource source, FeatureQuery query) {
        List<FeatureRecord> result = new ArrayList<>();
        try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            while (cursor.advance()) {
                result.add(cursor.current());
            }
            return new QueryOutcome(List.copyOf(result), cursor.diagnostics());
        }
    }

    private static List<FeatureRecord> grid(int count, int columns) {
        List<FeatureRecord> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            result.add(point(ordinal, ordinal % columns, Math.floorDiv(ordinal, columns)));
        }
        return List.copyOf(result);
    }

    private static FeatureRecord point(int ordinal, double x, double y) {
        return new FeatureRecord(
                "record:" + ordinal,
                "",
                new PointGeometry(new Coordinate(x, y)),
                Map.of("ordinal", (long) ordinal));
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

    private static final class SwitchToken implements CancellationToken {
        private boolean cancelled;

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }
    }

    private record QueryOutcome(List<FeatureRecord> records, DiagnosticReport diagnostics) {}
}
