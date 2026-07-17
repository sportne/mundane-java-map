package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.ElevationValue;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ElevationQueriesTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("query", "Query");

    @Test
    void analyticNonSquarePlaneCoversExactInteriorEdgesAndOutsideUlps() {
        ElevationSourceMetadata metadata =
                metadata(
                        5,
                        3,
                        new Envelope(10, 20, 18, 26),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326, Optional.empty(), Optional.empty()),
                        ElevationUnit.INTERNATIONAL_FOOT);
        double[] values = new double[Math.toIntExact(metadata.sampleCount())];
        for (int row = 0; row < metadata.rowCount(); row++) {
            for (int column = 0; column < metadata.columnCount(); column++) {
                Coordinate coordinate = metadata.sampleCoordinate(column, row);
                values[row * metadata.columnCount() + column] =
                        2.0 * coordinate.x() + 3.0 * coordinate.y();
            }
        }
        try (ElevationSource source = PackedElevationGrid.copyOf(metadata, values, new BitSet())) {
            assertValue(
                    source,
                    new Coordinate(14, 23),
                    ElevationQueryMode.NEAREST,
                    97.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(13, 22),
                    ElevationQueryMode.BILINEAR,
                    92.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(13, 26),
                    ElevationQueryMode.BILINEAR,
                    104.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(10, 21.5),
                    ElevationQueryMode.BILINEAR,
                    84.5,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(10, 26),
                    ElevationQueryMode.BILINEAR,
                    98.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(18, 26),
                    ElevationQueryMode.BILINEAR,
                    114.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(10, 20),
                    ElevationQueryMode.BILINEAR,
                    80.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(18, 20),
                    ElevationQueryMode.BILINEAR,
                    96.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(13, 20),
                    ElevationQueryMode.BILINEAR,
                    86.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(18, 21.5),
                    ElevationQueryMode.BILINEAR,
                    100.5,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(14, 22),
                    ElevationQueryMode.BILINEAR,
                    94.0,
                    ElevationUnit.INTERNATIONAL_FOOT);
            assertValue(
                    source,
                    new Coordinate(13, 23),
                    ElevationQueryMode.BILINEAR,
                    95.0,
                    ElevationUnit.INTERNATIONAL_FOOT);

            assertTrue(
                    ElevationQueries.query(
                                    source,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(Math.nextDown(10.0), 23),
                                    ElevationQueryMode.NEAREST)
                            .isEmpty());
            assertTrue(
                    ElevationQueries.query(
                                    source,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(Math.nextUp(18.0), 23),
                                    ElevationQueryMode.NEAREST)
                            .isEmpty());
        }
    }

    @Test
    void nearestTiesChooseLowerNumericIndexesAndReadOnlyThatPost() {
        RecordingSource source = recordingGrid(3, 3, new Envelope(0, 0, 2, 2));

        assertValue(source, new Coordinate(0.5, 2), ElevationQueryMode.NEAREST, 0.0);
        assertEquals(List.of(new Post(0, 0)), source.calls);
        source.calls.clear();

        assertValue(source, new Coordinate(0, 1.5), ElevationQueryMode.NEAREST, 0.0);
        assertEquals(List.of(new Post(0, 0)), source.calls);
        source.calls.clear();

        assertValue(source, new Coordinate(0.5, 1.5), ElevationQueryMode.NEAREST, 0.0);
        assertEquals(List.of(new Post(0, 0)), source.calls);
        source.calls.clear();

        assertValue(
                source,
                new Coordinate(0.5000000000000001, 1.4999999999999998),
                ElevationQueryMode.NEAREST,
                11.0);
        assertEquals(List.of(new Post(1, 1)), source.calls);
    }

    @Test
    void bilinearReadsOnlyPositiveContributorsInStableOrder() {
        RecordingSource source = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));

        assertValue(source, new Coordinate(0, 2), ElevationQueryMode.BILINEAR, 0.0);
        assertEquals(List.of(new Post(0, 0)), source.calls);
        source.calls.clear();

        assertValue(source, new Coordinate(1, 2), ElevationQueryMode.BILINEAR, 0.5);
        assertEquals(List.of(new Post(0, 0), new Post(1, 0)), source.calls);
        source.calls.clear();

        assertValue(source, new Coordinate(0, 1), ElevationQueryMode.BILINEAR, 5.0);
        assertEquals(List.of(new Post(0, 0), new Post(0, 1)), source.calls);
        source.calls.clear();

        assertValue(source, new Coordinate(1, 1), ElevationQueryMode.BILINEAR, 5.5);
        assertEquals(
                List.of(new Post(0, 0), new Post(1, 0), new Post(0, 1), new Post(1, 1)),
                source.calls);
    }

    @Test
    void bilinearStopsAtFirstNoDataAndNeverReadsZeroWeightNeighbors() {
        for (int absent = 0; absent < 4; absent++) {
            RecordingSource source = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
            source.noData.add(
                    List.of(new Post(0, 0), new Post(1, 0), new Post(0, 1), new Post(1, 1))
                            .get(absent));
            assertTrue(
                    ElevationQueries.query(
                                    source,
                                    CrsDefinitions.EPSG_4326,
                                    new Coordinate(1, 1),
                                    ElevationQueryMode.BILINEAR)
                            .isEmpty());
            assertEquals(absent + 1, source.calls.size());
        }

        RecordingSource exact = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        exact.noData.addAll(List.of(new Post(1, 0), new Post(0, 1), new Post(1, 1)));
        assertValue(exact, new Coordinate(0, 2), ElevationQueryMode.BILINEAR, 0.0);
        assertEquals(List.of(new Post(0, 0)), exact.calls);

        RecordingSource nearest = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        nearest.noData.add(new Post(0, 0));
        assertTrue(
                ElevationQueries.query(
                                nearest,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(0.25, 1.75),
                                ElevationQueryMode.NEAREST)
                        .isEmpty());
        assertEquals(List.of(new Post(0, 0)), nearest.calls);
    }

    @Test
    void recognizedMismatchRetainedUnknownAndDomainPrecedenceAreExact() {
        RecordingSource recognized = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        CrsException mismatch =
                assertThrows(
                        CrsException.class,
                        () ->
                                ElevationQueries.query(
                                        recognized,
                                        CrsDefinitions.EPSG_3857,
                                        new Coordinate(1, 1),
                                        ElevationQueryMode.NEAREST));
        assertProblem(
                mismatch,
                "CRS_DEFINITION_MISMATCH",
                Map.of(
                        "actualCrs", "EPSG:3857",
                        "expectedCrs", "EPSG:4326",
                        "operation", "elevationQuery"));

        CrsMetadata unknown =
                CrsMetadata.unknown(
                        Optional.of("LOCAL:UNKNOWN"), Optional.of("unparsed secret definition"));
        RecordingSource retained =
                new RecordingSource(
                        metadata(2, 2, new Envelope(0, 0, 2, 2), unknown, ElevationUnit.METRE),
                        (column, row) -> OptionalDouble.of(row * 10.0 + column));
        assertValue(retained, new Coordinate(1, 1), ElevationQueryMode.BILINEAR, 5.5);
        CrsDefinition equalDefinition =
                new CrsDefinition(
                        CrsDefinitions.EPSG_4326.canonicalIdentifier(),
                        CrsDefinitions.EPSG_4326.kind(),
                        CrsDefinitions.EPSG_4326.xAxis(),
                        CrsDefinitions.EPSG_4326.yAxis(),
                        CrsDefinitions.EPSG_4326.coordinateDomain());
        assertTrue(
                ElevationQueries.query(
                                recognized,
                                equalDefinition,
                                new Coordinate(1, 1),
                                ElevationQueryMode.NEAREST)
                        .isPresent());

        RecordingSource invalidEnvelope =
                new RecordingSource(
                        metadata(
                                2,
                                2,
                                new Envelope(-181, -90, -180, -89),
                                unknown,
                                ElevationUnit.METRE),
                        (column, row) -> OptionalDouble.of(0));
        CrsException envelope =
                assertThrows(
                        CrsException.class,
                        () ->
                                ElevationQueries.query(
                                        invalidEnvelope,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(181, 91),
                                        ElevationQueryMode.NEAREST));
        assertProblem(
                envelope,
                "CRS_ENVELOPE_OUT_OF_DOMAIN",
                Map.of(
                        "axis", "x",
                        "maximum", "180.0",
                        "minimum", "-180.0",
                        "operation", "elevationQuery",
                        "sourceCrs", "EPSG:4326",
                        "value", "-181.0"));

        CrsException coordinate =
                assertThrows(
                        CrsException.class,
                        () ->
                                ElevationQueries.query(
                                        retained,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(181, 91),
                                        ElevationQueryMode.NEAREST));
        assertProblem(
                coordinate,
                "CRS_COORDINATE_OUT_OF_DOMAIN",
                Map.of(
                        "axis", "x",
                        "maximum", "180.0",
                        "minimum", "-180.0",
                        "operation", "elevationQuery",
                        "sourceCrs", "EPSG:4326",
                        "value", "181.0"));
        assertFalse(coordinate.getMessage().contains("LOCAL:UNKNOWN"));

        CrsException yCoordinate =
                assertThrows(
                        CrsException.class,
                        () ->
                                ElevationQueries.query(
                                        retained,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(1, 91),
                                        ElevationQueryMode.NEAREST));
        assertEquals("y", yCoordinate.problem().context().get("axis"));

        assertEnvelopeEndpoint(new Envelope(179, 0, 181, 1), "x", "181.0");
        assertEnvelopeEndpoint(new Envelope(0, -91, 1, -90), "y", "-91.0");
        assertEnvelopeEndpoint(new Envelope(0, 89, 1, 91), "y", "91.0");
    }

    @Test
    void lifecycleOwnershipMetadataAndSourceContractPrecedenceAreExplicit() {
        RecordingSource source = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        assertThrows(
                NullPointerException.class, () -> ElevationQueries.query(null, null, null, null));
        assertThrows(
                NullPointerException.class, () -> ElevationQueries.query(source, null, null, null));
        assertThrows(
                NullPointerException.class,
                () -> ElevationQueries.query(source, CrsDefinitions.EPSG_4326, null, null));
        assertThrows(
                NullPointerException.class,
                () ->
                        ElevationQueries.query(
                                source, CrsDefinitions.EPSG_4326, new Coordinate(1, 1), null));

        assertValue(source, new Coordinate(1, 1), ElevationQueryMode.NEAREST, 0.0);
        assertEquals(1, source.metadataCalls);
        assertFalse(source.isClosed());
        assertEquals(0, source.closeCalls);

        source.closed = true;
        int metadataCalls = source.metadataCalls;
        assertThrows(
                IllegalStateException.class,
                () ->
                        ElevationQueries.query(
                                source,
                                CrsDefinitions.EPSG_3857,
                                new Coordinate(1, 1),
                                ElevationQueryMode.NEAREST));
        assertEquals(metadataCalls, source.metadataCalls);

        RecordingSource nullMetadata = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        nullMetadata.returnNullMetadata = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        ElevationQueries.query(
                                nullMetadata,
                                CrsDefinitions.EPSG_3857,
                                new Coordinate(1, 1),
                                ElevationQueryMode.NEAREST));

        RuntimeException metadataFailure = new RuntimeException("metadata");
        RecordingSource throwingMetadata = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        throwingMetadata.metadataFailure = metadataFailure;
        assertSame(
                metadataFailure,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                ElevationQueries.query(
                                        throwingMetadata,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(1, 1),
                                        ElevationQueryMode.NEAREST)));

        RecordingSource nullSample = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        nullSample.nullSample = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        ElevationQueries.query(
                                nullSample,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(0, 2),
                                ElevationQueryMode.NEAREST));

        RecordingSource nonFinite =
                new RecordingSource(
                        source.metadataValue,
                        (column, row) -> OptionalDouble.of(Double.POSITIVE_INFINITY));
        assertThrows(
                IllegalStateException.class,
                () ->
                        ElevationQueries.query(
                                nonFinite,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(0, 2),
                                ElevationQueryMode.NEAREST));

        RuntimeException sampleFailure = new RuntimeException("sample");
        RecordingSource throwingSample = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        throwingSample.sampleFailure = sampleFailure;
        assertSame(
                sampleFailure,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                ElevationQueries.query(
                                        throwingSample,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(0, 2),
                                        ElevationQueryMode.NEAREST)));

        RecordingSource signedZero = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        signedZero.sampler = (column, row) -> OptionalDouble.of(-0.0);
        ElevationValue zero =
                ElevationQueries.query(
                                signedZero,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(0, 2),
                                ElevationQueryMode.NEAREST)
                        .orElseThrow();
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(zero.value()));
    }

    @Test
    void extremeValuesWeightsAndNearMaximumIndexesRemainFiniteAndBounded() {
        RecordingSource extremes = recordingGrid(2, 2, new Envelope(0, 0, 2, 2));
        extremes.sampler =
                (column, row) ->
                        OptionalDouble.of(column == row ? -Double.MAX_VALUE : Double.MAX_VALUE);
        ElevationValue center =
                ElevationQueries.query(
                                extremes,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(1, 1),
                                ElevationQueryMode.BILINEAR)
                        .orElseThrow();
        assertTrue(Double.isFinite(center.value()));
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(center.value()));

        CrsDefinition hugeCrs = hugeProjectedCrs();
        RecordingSource roundedWeight =
                new RecordingSource(
                        metadata(
                                2,
                                2,
                                new Envelope(1, 0, Double.MAX_VALUE, 1),
                                CrsMetadata.recognized(hugeCrs, Optional.empty(), Optional.empty()),
                                ElevationUnit.METRE),
                        (column, row) ->
                                column == 1 ? OptionalDouble.empty() : OptionalDouble.of(4));
        assertTrue(
                ElevationQueries.query(
                                roundedWeight,
                                hugeCrs,
                                new Coordinate(Math.nextUp(1.0), 1),
                                ElevationQueryMode.BILINEAR)
                        .isEmpty());
        assertEquals(List.of(new Post(0, 0), new Post(1, 0)), roundedWeight.calls);

        RecordingSource roundedToOne =
                new RecordingSource(
                        roundedWeight.metadataValue,
                        (column, row) ->
                                column == 0 ? OptionalDouble.empty() : OptionalDouble.of(4));
        assertTrue(
                ElevationQueries.query(
                                roundedToOne,
                                hugeCrs,
                                new Coordinate(Math.nextDown(Double.MAX_VALUE), 1),
                                ElevationQueryMode.BILINEAR)
                        .isEmpty());
        assertEquals(List.of(new Post(0, 0)), roundedToOne.calls);

        int count = Integer.MAX_VALUE;
        ElevationSourceMetadata hugeMetadata =
                metadata(
                        count,
                        2,
                        new Envelope(0, 0, count - 1.0, 1),
                        CrsMetadata.recognized(hugeCrs, Optional.empty(), Optional.empty()),
                        ElevationUnit.METRE);
        RecordingSource huge =
                new RecordingSource(hugeMetadata, (column, row) -> OptionalDouble.of(column + row));
        Coordinate position = hugeMetadata.sampleCoordinate(count - 2, 0);
        ElevationValue hugeValue =
                ElevationQueries.query(huge, hugeCrs, position, ElevationQueryMode.NEAREST)
                        .orElseThrow();
        assertEquals(count - 2.0, hugeValue.value());
        assertEquals(ElevationUnit.METRE, hugeValue.unit());
        assertEquals(List.of(new Post(count - 2, 0)), huge.calls);
        assertEquals(1, huge.metadataCalls);

        huge.calls.clear();
        Coordinate highTie = new Coordinate(count - 1.5, 1);
        ElevationValue highNearest =
                ElevationQueries.query(huge, hugeCrs, highTie, ElevationQueryMode.NEAREST)
                        .orElseThrow();
        assertEquals(count - 2.0, highNearest.value());
        assertEquals(List.of(new Post(count - 2, 0)), huge.calls);

        huge.calls.clear();
        ElevationValue highBilinear =
                ElevationQueries.query(huge, hugeCrs, highTie, ElevationQueryMode.BILINEAR)
                        .orElseThrow();
        assertEquals(count - 1.5, highBilinear.value());
        assertEquals(List.of(new Post(count - 2, 0), new Post(count - 1, 0)), huge.calls);
    }

    private static void assertValue(
            ElevationSource source,
            Coordinate coordinate,
            ElevationQueryMode mode,
            double expected) {
        assertValue(source, coordinate, mode, expected, ElevationUnit.METRE);
    }

    private static void assertValue(
            ElevationSource source,
            Coordinate coordinate,
            ElevationQueryMode mode,
            double expected,
            ElevationUnit expectedUnit) {
        ElevationValue value =
                ElevationQueries.query(source, CrsDefinitions.EPSG_4326, coordinate, mode)
                        .orElseThrow();
        assertEquals(expected, value.value());
        assertEquals(expectedUnit, value.unit());
    }

    private static void assertProblem(
            CrsException failure, String code, Map<String, String> context) {
        assertEquals(code, failure.problem().code());
        assertEquals(context, failure.problem().context());
        assertEquals(
                context.keySet().stream().sorted().toList(),
                new ArrayList<>(failure.problem().context().keySet()));
    }

    private static void assertEnvelopeEndpoint(Envelope bounds, String axis, String value) {
        CrsMetadata unknown = CrsMetadata.unknown(Optional.of("LOCAL:DOMAIN"), Optional.empty());
        RecordingSource source =
                new RecordingSource(
                        metadata(2, 2, bounds, unknown, ElevationUnit.METRE),
                        (column, row) -> OptionalDouble.of(0));
        CrsException failure =
                assertThrows(
                        CrsException.class,
                        () ->
                                ElevationQueries.query(
                                        source,
                                        CrsDefinitions.EPSG_4326,
                                        new Coordinate(0, 0),
                                        ElevationQueryMode.NEAREST));
        assertEquals("CRS_ENVELOPE_OUT_OF_DOMAIN", failure.problem().code());
        assertEquals(axis, failure.problem().context().get("axis"));
        assertEquals(value, failure.problem().context().get("value"));
    }

    private static RecordingSource recordingGrid(int columns, int rows, Envelope bounds) {
        return new RecordingSource(
                metadata(
                        columns,
                        rows,
                        bounds,
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326, Optional.empty(), Optional.empty()),
                        ElevationUnit.METRE),
                (column, row) -> OptionalDouble.of(row * 10.0 + column));
    }

    private static ElevationSourceMetadata metadata(
            int columns, int rows, Envelope bounds, CrsMetadata crs, ElevationUnit unit) {
        return new ElevationSourceMetadata(IDENTITY, columns, rows, bounds, crs, unit);
    }

    private static CrsDefinition hugeProjectedCrs() {
        return new CrsDefinition(
                "LOCAL:HUGE",
                CrsKind.PROJECTED,
                new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                new Envelope(0, -1, Double.MAX_VALUE, 2));
    }

    private record Post(int column, int row) {}

    @FunctionalInterface
    private interface Sampler {
        OptionalDouble sample(int column, int row);
    }

    private static final class RecordingSource implements ElevationSource {
        private final ElevationSourceMetadata metadataValue;
        private final List<Post> calls = new ArrayList<>();
        private final List<Post> noData = new ArrayList<>();
        private Sampler sampler;
        private RuntimeException metadataFailure;
        private RuntimeException sampleFailure;
        private boolean returnNullMetadata;
        private boolean nullSample;
        private boolean closed;
        private int metadataCalls;
        private int closeCalls;

        private RecordingSource(ElevationSourceMetadata metadataValue, Sampler sampler) {
            this.metadataValue = metadataValue;
            this.sampler = sampler;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            metadataCalls++;
            if (metadataFailure != null) {
                throw metadataFailure;
            }
            return returnNullMetadata ? null : metadataValue;
        }

        @Override
        public ElevationSourceLimits limits() {
            return ElevationSourceLimits.DEFAULTS;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            calls.add(new Post(column, row));
            if (sampleFailure != null) {
                throw sampleFailure;
            }
            if (nullSample) {
                return null;
            }
            return noData.contains(new Post(column, row))
                    ? OptionalDouble.empty()
                    : sampler.sample(column, row);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closeCalls++;
            closed = true;
        }
    }
}
