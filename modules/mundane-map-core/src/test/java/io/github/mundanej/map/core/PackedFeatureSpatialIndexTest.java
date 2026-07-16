package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PackedFeatureSpatialIndexTest {
    @Test
    void exactLayoutsCoverTreeBoundaries() {
        assertLayout(0, 0, 0, 0, 0, 0, 0, 0);
        assertLayout(1, 1, 1, 0, 1, 41, 49, 12);
        assertLayout(15, 1, 1, 0, 1, 97, 217, 12);
        assertLayout(16, 1, 1, 0, 1, 101, 229, 12);
        assertLayout(17, 2, 3, 2, 2, 187, 323, 72);
    }

    @Test
    void deterministicPackedOrderHandlesEqualAndDegenerateEnvelopes() {
        List<FeatureRecord> records = new ArrayList<>();
        for (int ordinal = 0; ordinal < 33; ordinal++) {
            double coordinate = ordinal < 8 ? 5 : ordinal % 7;
            records.add(point(ordinal, coordinate, coordinate));
        }
        PackedFeatureSpatialIndex first = build(records);
        PackedFeatureSpatialIndex second = build(records);
        assertArrayEquals(first.recordOrdinalsCopy(), second.recordOrdinalsCopy());
        assertEquals(3, first.leafCount());
        assertEquals(4, first.nodeCount());
        assertEquals(2, first.height());
        assertEquals(first.nodeCount() - 1, first.edgeCount());
        int[] order = first.recordOrdinalsCopy();
        boolean[] seen = new boolean[records.size()];
        for (int ordinal : order) {
            assertTrue(!seen[ordinal]);
            seen[ordinal] = true;
        }
    }

    @Test
    void plansAreIndependentSourceOrdinalBitsetsAndReadOnlyConstructionIsConcurrent()
            throws Exception {
        PackedFeatureSpatialIndex index = build(grid(64, 16));
        Envelope query = new Envelope(4, 1, 8, 2);
        var first = index.plan(query, CancellationToken.none());
        var second = index.plan(query, CancellationToken.none());
        assertArrayEquals(first.wordsCopy(), second.wordsCopy());
        int firstOrdinal = first.nextCandidate(0, CancellationToken.none());
        assertTrue(firstOrdinal >= 0);
        assertTrue(first.contains(firstOrdinal));
        assertEquals(firstOrdinal, first.nextCandidate(firstOrdinal, CancellationToken.none()));
        assertTrue(first.nextCandidate(64, CancellationToken.none()) < 0);
        AtomicReference<long[]> firstConcurrent = new AtomicReference<>();
        AtomicReference<long[]> secondConcurrent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread one =
                new Thread(
                        () -> runPlan(index, query, firstConcurrent, failure),
                        "packed-index-plan-one");
        Thread two =
                new Thread(
                        () -> runPlan(index, query, secondConcurrent, failure),
                        "packed-index-plan-two");
        one.start();
        two.start();
        one.join();
        two.join();
        assertEquals(null, failure.get());
        assertArrayEquals(first.wordsCopy(), firstConcurrent.get());
        assertArrayEquals(first.wordsCopy(), secondConcurrent.get());
        assertTrue(first.candidateCount() >= 10);
    }

    @Test
    void plansCoverEmptyDisjointContainingEdgeCornerAndDomainQueries() {
        PackedFeatureSpatialIndex empty = build(List.of());
        assertEquals(
                0,
                empty.plan(new Envelope(-180, -90, 180, 90), CancellationToken.none())
                        .candidateCount());

        PackedFeatureSpatialIndex index =
                build(List.of(point(0, -180, -90), point(1, 0, 0), point(2, 180, 90)));
        assertEquals(
                0,
                index.plan(new Envelope(181, 91, 182, 92), CancellationToken.none())
                        .candidateCount());
        assertEquals(
                3,
                index.plan(new Envelope(-180, -90, 180, 90), CancellationToken.none())
                        .candidateCount());
        assertTrue(
                index.plan(new Envelope(-180, -90, -180, -90), CancellationToken.none())
                        .contains(0));
        assertTrue(
                index.plan(new Envelope(180, 90, 180, 90), CancellationToken.none()).contains(2));
    }

    @Test
    void candidatePlansProveZeroOneAndManyExactCounts() {
        Envelope origin = new Envelope(0, 0, 0, 0);
        PackedFeatureSpatialIndex singleton = build(List.of(point(0, 0, 0)));
        assertEquals(1, singleton.plan(origin, CancellationToken.none()).candidateCount());

        PackedFeatureSpatialIndex twoLeaves = build(grid(17, 17));
        assertEquals(
                0,
                twoLeaves
                        .plan(new Envelope(20, 20, 21, 21), CancellationToken.none())
                        .candidateCount());
        assertTrue(twoLeaves.plan(origin, CancellationToken.none()).candidateCount() > 1);
        assertEquals(
                17,
                twoLeaves
                        .plan(new Envelope(-1, -1, 17, 1), CancellationToken.none())
                        .candidateCount());
    }

    @Test
    void fixedComparisonCandidateTotalsMatchReviewedIndependentConstants() {
        int[] sizes = {32, 128, 512, 2_048, 8_192, 32_768, 131_072};
        long[] smokeTotals = {384, 928, 2_912, 10_064, 39_264, 151_200, 599_456};
        for (int index = 0; index < sizes.length; index++) {
            PackedFeatureSpatialIndex packed = build(comparisonGrid(sizes[index], index));
            assertEquals(
                    smokeTotals[index], comparisonCandidateTotal(packed, sizes[index], index, 24));
        }
        assertEquals(4_176, comparisonCandidateTotal(build(comparisonGrid(32, 0)), 32, 0, 256));
    }

    @Test
    void cancellationIsBoundedDuringEmptyPlanAndCandidateWordScanning() {
        PackedFeatureSpatialIndex empty = build(List.of());
        PollCancellation emptyCancellation = new PollCancellation(2);
        SourceException emptyFailure =
                assertThrows(
                        SourceException.class,
                        () -> empty.plan(new Envelope(-1, -1, 1, 1), emptyCancellation));
        assertEquals("SOURCE_CANCELLED", emptyFailure.terminal().code());
        assertEquals(2, emptyCancellation.polls);

        var plan = new PackedFeatureSpatialIndex.CandidatePlan("source", new long[9_000]);
        PollCancellation wordCancellation = new PollCancellation(2);
        SourceException wordFailure =
                assertThrows(SourceException.class, () -> plan.nextCandidate(0, wordCancellation));
        assertEquals("SOURCE_CANCELLED", wordFailure.terminal().code());
        assertEquals(2, wordCancellation.polls);
    }

    @Test
    void deterministicPackingCoversSpanningAxisDegenerateAndEveryEnvelopeTieField() {
        List<FeatureRecord> records = new ArrayList<>();
        records.add(line(0, -1_000_000, 0, 1_000_000, 0));
        records.add(line(1, 5, -20, 5, 20));
        records.add(line(2, -8, -4, 8, 4));
        records.add(line(3, -8, -3, 8, 5));
        records.add(line(4, -7, -4, 9, 4));
        records.add(line(5, -7, -3, 9, 5));
        records.add(line(6, -6, -2, 10, 6));
        records.add(line(7, -6, -2, 10, 6));
        for (int ordinal = 8; ordinal < 40; ordinal++) {
            records.add(point(ordinal, ordinal % 5, ordinal % 3));
        }
        PackedFeatureSpatialIndex first = build(records);
        PackedFeatureSpatialIndex second = build(List.copyOf(records));
        assertArrayEquals(first.recordOrdinalsCopy(), second.recordOrdinalsCopy());
        boolean[] seen = new boolean[records.size()];
        for (int ordinal : first.recordOrdinalsCopy()) {
            assertTrue(!seen[ordinal], "duplicate packed ordinal " + ordinal);
            seen[ordinal] = true;
        }
    }

    @Test
    void envelopeComparatorPinsEveryDeclaredTieKeyAndFinalStableKey() {
        Envelope wide = new Envelope(-2, -1, 2, 1);
        Envelope narrow = new Envelope(-1, -1, 1, 1);
        Envelope tall = new Envelope(-1, -2, 1, 2);
        Envelope shortEnvelope = new Envelope(-1, -1, 1, 1);
        Envelope maxXNegativeZero = new Envelope(-0.0, 0, -0.0, 0);
        Envelope maxXPositiveZero = new Envelope(-0.0, 0, 0.0, 0);
        Envelope maxYNegativeZero = new Envelope(0, -0.0, 0, -0.0);
        Envelope maxYPositiveZero = new Envelope(0, -0.0, 0, 0.0);
        assertTrue(PackedFeatureSpatialIndex.compareEnvelopeItems(wide, 0, narrow, 1, true) < 0);
        assertTrue(PackedFeatureSpatialIndex.compareEnvelopeItems(tall, 0, narrow, 1, false) < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(tall, 0, shortEnvelope, 1, true)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(wide, 0, shortEnvelope, 1, false)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                new Envelope(0, 0, 0, 0), 0, new Envelope(1, 0, 1, 0), 1, true)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                new Envelope(0, 0, 0, 0), 0, new Envelope(0, 1, 0, 1), 1, true)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                new Envelope(0, 0, 0, 0), 0, new Envelope(1, 0, 1, 0), 1, false)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                maxXNegativeZero, 0, maxXPositiveZero, 1, true)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                maxYNegativeZero, 0, maxYPositiveZero, 1, true)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                maxYNegativeZero, 0, maxYPositiveZero, 1, false)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                maxXNegativeZero, 0, maxXPositiveZero, 1, false)
                        < 0);
        assertTrue(
                PackedFeatureSpatialIndex.compareEnvelopeItems(
                                new Envelope(0, 0, 0, 0), 0, new Envelope(0, 1, 0, 1), 1, false)
                        < 0);
        assertTrue(PackedFeatureSpatialIndex.compareEnvelopeItems(wide, 3, wide, 4, true) < 0);
        assertTrue(PackedFeatureSpatialIndex.compareEnvelopeItems(wide, 4, wide, 3, false) > 0);
    }

    @Test
    void cancellationIsStagedAcrossNodeAndEntryTraversal() {
        PackedFeatureSpatialIndex index = build(grid(70_000, 350));
        Envelope full = new Envelope(-1, -1, 400, 250);
        PollCancellation nodeCancellation = new PollCancellation(3);
        SourceException nodeFailure =
                assertThrows(SourceException.class, () -> index.plan(full, nodeCancellation));
        assertEquals("SOURCE_CANCELLED", nodeFailure.terminal().code());
        assertEquals(3, nodeCancellation.polls);

        PollCancellation entryCancellation = new PollCancellation(4);
        SourceException entryFailure =
                assertThrows(SourceException.class, () -> index.plan(full, entryCancellation));
        assertEquals("SOURCE_CANCELLED", entryFailure.terminal().code());
        assertEquals(4, entryCancellation.polls);
    }

    @Test
    void maximumIntegerLayoutUsesCheckedNonOverflowingArithmetic() {
        PackedFeatureSpatialIndex.Layout layout =
                PackedFeatureSpatialIndex.Layout.forRecords(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, layout.recordCount());
        assertTrue(layout.nodeCount() > 0);
        assertTrue(layout.retainedBytes() > 0);
        assertTrue(layout.buildBytes() > layout.retainedBytes());
        assertTrue(layout.queryBytes() > 0);
        assertEquals(
                Integer.MAX_VALUE,
                PackedFeatureSpatialIndex.boundedEnd(1 << 30, 1 << 30, Integer.MAX_VALUE));
        assertEquals(
                Integer.MAX_VALUE,
                PackedFeatureSpatialIndex.boundedEnd(Integer.MAX_VALUE - 7, 16, Integer.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> PackedFeatureSpatialIndex.boundedEnd(0, 0, Integer.MAX_VALUE));
    }

    private static void runPlan(
            PackedFeatureSpatialIndex index,
            Envelope query,
            AtomicReference<long[]> result,
            AtomicReference<Throwable> failure) {
        try {
            result.set(index.plan(query, CancellationToken.none()).wordsCopy());
        } catch (Throwable thrown) {
            failure.compareAndSet(null, thrown);
        }
    }

    private static void assertLayout(
            int records,
            int leaves,
            int nodes,
            int edges,
            int height,
            long retained,
            long build,
            long query) {
        PackedFeatureSpatialIndex.Layout layout =
                PackedFeatureSpatialIndex.Layout.forRecords(records);
        assertEquals(leaves, layout.leafCount());
        assertEquals(nodes, layout.nodeCount());
        assertEquals(edges, layout.edgeCount());
        assertEquals(height, layout.height());
        assertEquals(retained, layout.retainedBytes());
        assertEquals(build, layout.buildBytes());
        assertEquals(query, layout.queryBytes());
    }

    private static PackedFeatureSpatialIndex build(List<FeatureRecord> records) {
        return PackedFeatureSpatialIndex.build("source", records, FeatureIndexLimits.LEVEL_1);
    }

    private static List<FeatureRecord> grid(int count, int columns) {
        List<FeatureRecord> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            result.add(point(ordinal, ordinal % columns, Math.floorDiv(ordinal, columns)));
        }
        return result;
    }

    private static List<FeatureRecord> comparisonGrid(int size, int sizeIndex) {
        int columns = 8 << sizeIndex;
        List<FeatureRecord> result = new ArrayList<>(size);
        for (int ordinal = 0; ordinal < size; ordinal++) {
            result.add(
                    point(
                            ordinal,
                            Math.floorMod(ordinal, columns) * 1_000.0,
                            Math.floorDiv(ordinal, columns) * 1_000.0));
        }
        return result;
    }

    private static long comparisonCandidateTotal(
            PackedFeatureSpatialIndex index, int size, int sizeIndex, int viewportCount) {
        long result = 0;
        for (int ordinal = 0; ordinal < viewportCount; ordinal++) {
            result =
                    Math.addExact(
                            result,
                            index.plan(
                                            comparisonViewport(size, sizeIndex, ordinal),
                                            CancellationToken.none())
                                    .candidateCount());
        }
        return result;
    }

    private static Envelope comparisonViewport(int size, int sizeIndex, int ordinal) {
        int columns = 8 << sizeIndex;
        int rows = 4 << sizeIndex;
        double maxX = (columns - 1) * 1_000.0;
        double maxY = (rows - 1) * 1_000.0;
        int kind = Math.floorMod(ordinal, 6);
        if (kind == 0) {
            return new Envelope(maxX + 500, maxY + 500, maxX + 1_500, maxY + 1_500);
        }
        if (kind == 1) {
            int column = Math.floorMod(37 * ordinal, columns);
            int row = Math.floorMod(53 * ordinal, rows);
            double x = column * 1_000.0;
            double y = row * 1_000.0;
            return new Envelope(x - 500, y - 500, x, y);
        }
        if (kind == 5) {
            return new Envelope(-500, -500, maxX + 500, maxY + 500);
        }
        int selected =
                switch (kind) {
                    case 2 -> Math.max(1, size / 1_024);
                    case 3 -> Math.max(1, size / 128);
                    case 4 -> Math.max(1, size / 8);
                    default -> throw new AssertionError("unreachable");
                };
        int exponent = Integer.numberOfTrailingZeros(selected);
        int width = 1 << Math.floorDiv(exponent, 2);
        int height = Math.floorDiv(selected, width);
        int originColumn = Math.floorMod(37 * ordinal, columns - width + 1);
        int originRow = Math.floorMod(53 * ordinal, rows - height + 1);
        return new Envelope(
                originColumn * 1_000.0 - 500,
                originRow * 1_000.0 - 500,
                (originColumn + width - 1) * 1_000.0 + 500,
                (originRow + height - 1) * 1_000.0 + 500);
    }

    private static FeatureRecord point(int ordinal, double x, double y) {
        return new FeatureRecord(
                "record:" + ordinal, "", new PointGeometry(new Coordinate(x, y)), Map.of());
    }

    private static FeatureRecord line(int ordinal, double x1, double y1, double x2, double y2) {
        return new FeatureRecord(
                "record:" + ordinal,
                "",
                new LineStringGeometry(CoordinateSequence.of(x1, y1, x2, y2)),
                Map.of());
    }

    private static final class PollCancellation implements CancellationToken {
        private final int cancellingPoll;
        private int polls;

        private PollCancellation(int cancellingPoll) {
            this.cancellingPoll = cancellingPoll;
        }

        @Override
        public boolean isCancellationRequested() {
            return ++polls >= cancellingPoll;
        }
    }
}
