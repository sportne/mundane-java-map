package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PackedElevationGridTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("elevation", "Elevation");
    private static final CrsMetadata CRS =
            CrsMetadata.unknown(Optional.of("LOCAL:ELEVATION"), Optional.empty());

    @Test
    void samplesUseRowMajorOrientationAndStructuralNoData() {
        BitSet noData = new BitSet();
        noData.set(4);
        PackedElevationGrid grid =
                PackedElevationGrid.copyOf(
                        metadata(3, 2), new double[] {10, 11, 12, 20, 21, 22}, noData);

        assertEquals(10.0, grid.sample(0, 0).orElseThrow());
        assertEquals(12.0, grid.sample(2, 0).orElseThrow());
        assertEquals(20.0, grid.sample(0, 1).orElseThrow());
        assertTrue(grid.sample(1, 1).isEmpty());
        assertEquals(22.0, grid.sample(2, 1).orElseThrow());
        assertEquals(ElevationSourceLimits.DEFAULTS, grid.limits());
        assertTrue(grid.openingDiagnostics().entries().isEmpty());
    }

    @Test
    void factoryDefensivelyCopiesInputsAndCanonicalizesPayloads() throws Exception {
        double[] samples = {-0.0, Double.NaN, 3.0, 4.0};
        BitSet noData = new BitSet();
        noData.set(1);
        PackedElevationGrid grid = PackedElevationGrid.copyOf(metadata(2, 2), samples, noData);

        samples[0] = 99;
        samples[2] = 88;
        noData.clear();
        noData.set(2);

        assertEquals(
                Double.doubleToRawLongBits(0.0),
                Double.doubleToRawLongBits(grid.sample(0, 0).orElseThrow()));
        assertTrue(grid.sample(1, 0).isEmpty());
        assertEquals(3.0, grid.sample(0, 1).orElseThrow());
        double[] retained = (double[]) field("elevations").get(grid);
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(retained[0]));
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(retained[1]));
    }

    @Test
    void factoryRejectsInvalidPayloadLengthMaskAndUnmaskedNonFiniteSamples() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PackedElevationGrid.copyOf(metadata(2, 2), new double[3], new BitSet()));

        BitSet outside = new BitSet();
        outside.set(4);
        assertThrows(
                IllegalArgumentException.class,
                () -> PackedElevationGrid.copyOf(metadata(2, 2), new double[4], outside));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata(2, 2),
                                new double[] {0, Double.POSITIVE_INFINITY, 2, 3},
                                new BitSet()));
        assertThrows(
                NullPointerException.class,
                () -> PackedElevationGrid.copyOf(metadata(2, 2), null, new BitSet()));
    }

    @Test
    void sampleRejectsEveryInvalidIndex() {
        PackedElevationGrid grid =
                PackedElevationGrid.copyOf(metadata(2, 2), new double[4], new BitSet());
        assertThrows(IndexOutOfBoundsException.class, () -> grid.sample(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.sample(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.sample(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.sample(0, 2));
    }

    @Test
    void dimensionsSamplesAndBytesAcceptEqualityAndRejectOneOver() {
        ElevationSourceMetadata metadata = metadata(3, 2);
        double[] samples = new double[6];
        BitSet mask = new BitSet();

        assertLimit(
                "columns",
                3,
                2,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, samples, mask, limits(2, 2, 6, 56, 1), report(0)));
        PackedElevationGrid.copyOf(metadata, samples, mask, limits(3, 2, 6, 56, 1), report(0))
                .close();
        PackedElevationGrid.copyOf(metadata, samples, mask, limits(4, 2, 6, 56, 1), report(0))
                .close();

        assertLimit(
                "rows",
                2,
                1,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, samples, mask, limits(3, 1, 6, 56, 1), report(0)));
        PackedElevationGrid.copyOf(metadata, samples, mask, limits(3, 3, 6, 56, 1), report(0))
                .close();

        assertLimit(
                "samples",
                6,
                5,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, samples, mask, limits(3, 2, 5, 56, 1), report(0)));
        PackedElevationGrid.copyOf(metadata, samples, mask, limits(3, 2, 7, 56, 1), report(0))
                .close();

        assertLimit(
                "retainedSampleBytes",
                56,
                55,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, samples, mask, limits(3, 2, 6, 55, 1), report(0)));
        PackedElevationGrid.copyOf(metadata, samples, mask, limits(3, 2, 6, 57, 1), report(0))
                .close();
    }

    @Test
    void packedAddressabilityUsesIntegerMaximumAndByteAccountingSaturates() {
        ElevationSourceMetadata tooLarge = metadata(46_341, 46_341);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                PackedElevationGrid.copyOf(
                                        tooLarge,
                                        new double[0],
                                        new BitSet(),
                                        new ElevationSourceLimits(
                                                50_000, 50_000, Long.MAX_VALUE, Long.MAX_VALUE, 1),
                                        report(0)));
        assertEquals("samples", failure.terminal().context().get("limit"));
        assertEquals(
                Integer.toString(Integer.MAX_VALUE), failure.terminal().context().get("maximum"));
        assertEquals(136_314_880L, PackedElevationGrid.retainedSampleBytes(16_777_216L));
        assertEquals(Long.MAX_VALUE, PackedElevationGrid.retainedSampleBytes(Long.MAX_VALUE));
    }

    @Test
    void openingReportRequiresMatchingWarningsWithinTheRetentionCeiling() {
        ElevationSourceMetadata metadata = metadata(2, 2);
        ElevationSourceLimits one = limits(2, 2, 4, 40, 1);
        ElevationSourceLimits two = limits(2, 2, 4, 40, 2);
        ElevationSourceLimits three = limits(2, 2, 4, 40, 3);
        DiagnosticReport oneWarning = report(1);
        DiagnosticReport twoWarnings = report(2);

        PackedElevationGrid.copyOf(metadata, new double[4], new BitSet(), one, oneWarning).close();
        PackedElevationGrid.copyOf(metadata, new double[4], new BitSet(), two, twoWarnings).close();
        PackedElevationGrid.copyOf(metadata, new double[4], new BitSet(), three, twoWarnings)
                .close();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, new double[4], new BitSet(), one, twoWarnings));

        DiagnosticReport wrongSource = new DiagnosticReport(List.of(warning("other", 0)), 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, new double[4], new BitSet(), two, wrongSource));
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_FAILED",
                        DiagnosticSeverity.ERROR,
                        IDENTITY.id(),
                        Optional.empty(),
                        "error",
                        Map.of());
        DiagnosticReport failed = new DiagnosticReport(List.of(terminal), 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PackedElevationGrid.copyOf(
                                metadata, new double[4], new BitSet(), two, failed));

        DiagnosticReport omitted = new DiagnosticReport(List.of(), 99);
        PackedElevationGrid.copyOf(metadata, new double[4], new BitSet(), one, omitted).close();
    }

    @Test
    void closeReleasesStorageAndRetainsImmutableOpeningValues() throws Exception {
        ElevationSourceMetadata metadata = metadata(2, 2);
        ElevationSourceLimits limits = limits(2, 2, 4, 40, 1);
        DiagnosticReport report = report(1);
        PackedElevationGrid grid =
                PackedElevationGrid.copyOf(
                        metadata, new double[] {1, 2, 3, 4}, new BitSet(), limits, report);

        grid.close();
        grid.close();

        assertTrue(grid.isClosed());
        assertSame(metadata, grid.metadata());
        assertSame(limits, grid.limits());
        assertSame(report, grid.openingDiagnostics());
        assertThrows(IllegalStateException.class, () -> grid.sample(0, 0));
        assertNull(field("elevations").get(grid));
        assertNull(field("noDataWords").get(grid));
    }

    private static ElevationSourceMetadata metadata(int columns, int rows) {
        return new ElevationSourceMetadata(
                IDENTITY, columns, rows, new Envelope(0, 0, 1, 1), CRS, ElevationUnit.METRE);
    }

    private static ElevationSourceLimits limits(
            int columns, int rows, long samples, long bytes, int warnings) {
        return new ElevationSourceLimits(columns, rows, samples, bytes, warnings);
    }

    private static DiagnosticReport report(int warningCount) {
        List<SourceDiagnostic> warnings = new ArrayList<>();
        for (int index = 0; index < warningCount; index++) {
            warnings.add(warning(IDENTITY.id(), index));
        }
        return new DiagnosticReport(warnings, 0);
    }

    private static SourceDiagnostic warning(String sourceId, int index) {
        return new SourceDiagnostic(
                "SOURCE_VALUE_SUBSTITUTED",
                DiagnosticSeverity.WARNING,
                sourceId,
                Optional.empty(),
                "warning",
                Map.of("index", Integer.toString(index)));
    }

    private static void assertLimit(
            String limit, long requested, long maximum, Runnable operation) {
        SourceException failure = assertThrows(SourceException.class, operation::run);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(DiagnosticSeverity.ERROR, failure.terminal().severity());
        assertEquals(IDENTITY.id(), failure.terminal().sourceId());
        assertTrue(failure.terminal().location().isEmpty());
        assertEquals(
                Map.of(
                        "scope",
                        "elevationOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                failure.terminal().context());
        assertEquals(1, failure.report().entries().size());
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = PackedElevationGrid.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
