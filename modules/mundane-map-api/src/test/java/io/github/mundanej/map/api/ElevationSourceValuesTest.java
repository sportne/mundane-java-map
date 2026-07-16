package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ElevationSourceValuesTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("terrain", "Terrain");
    private static final CrsMetadata UNKNOWN_CRS =
            CrsMetadata.unknown(Optional.of("LOCAL:TERRAIN"), Optional.empty());

    @Test
    void unitsExposeTheirExactFixedMetreFactors() {
        assertEquals(1.0, ElevationUnit.METRE.metresPerUnit());
        assertEquals(0.3048, ElevationUnit.INTERNATIONAL_FOOT.metresPerUnit());
        assertEquals(1200.0 / 3937.0, ElevationUnit.US_SURVEY_FOOT.metresPerUnit());
    }

    @Test
    void metadataDefinesExactEndpointsSpacingAndNorthToSouthRows() {
        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        IDENTITY,
                        5,
                        3,
                        new Envelope(10.0, 20.0, 18.0, 26.0),
                        UNKNOWN_CRS,
                        ElevationUnit.METRE);

        assertEquals(15L, metadata.sampleCount());
        assertEquals(2.0, metadata.columnSpacing());
        assertEquals(3.0, metadata.rowSpacing());
        assertEquals(new Coordinate(10.0, 26.0), metadata.sampleCoordinate(0, 0));
        assertEquals(new Coordinate(18.0, 26.0), metadata.sampleCoordinate(4, 0));
        assertEquals(new Coordinate(10.0, 20.0), metadata.sampleCoordinate(0, 2));
        assertEquals(new Coordinate(14.0, 23.0), metadata.sampleCoordinate(2, 1));
        assertSame(UNKNOWN_CRS, metadata.crs());
    }

    @Test
    void metadataRejectsInvalidShapeBoundsAndCollapsedAdjacentPosts() {
        assertThrows(
                IllegalArgumentException.class, () -> metadata(1, 2, new Envelope(0, 0, 1, 1)));
        assertThrows(
                IllegalArgumentException.class, () -> metadata(2, 1, new Envelope(0, 0, 1, 1)));
        assertThrows(
                IllegalArgumentException.class, () -> metadata(2, 2, new Envelope(0, 0, 0, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> metadata(5, 2, new Envelope(1.0e16, 0, 1.0e16 + 4.0, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> metadata(2, 5, new Envelope(0, 1.0e16, 1, 1.0e16 + 4.0)));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ElevationSourceMetadata(
                                IDENTITY,
                                2,
                                2,
                                new Envelope(0, 0, 1, 1),
                                null,
                                ElevationUnit.METRE));
    }

    @Test
    void metadataCoordinatesRejectEveryInvalidIndex() {
        ElevationSourceMetadata metadata = metadata(2, 2, new Envelope(0, 0, 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> metadata.sampleCoordinate(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> metadata.sampleCoordinate(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> metadata.sampleCoordinate(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> metadata.sampleCoordinate(0, 2));
    }

    @Test
    void limitsExposeDefaultsAndRejectEveryNonPositiveCeiling() {
        assertEquals(4_096, ElevationSourceLimits.DEFAULTS.maximumColumns());
        assertEquals(4_096, ElevationSourceLimits.DEFAULTS.maximumRows());
        assertEquals(16_777_216L, ElevationSourceLimits.DEFAULTS.maximumSamples());
        assertEquals(136_314_880L, ElevationSourceLimits.DEFAULTS.maximumRetainedSampleBytes());
        assertEquals(256, ElevationSourceLimits.DEFAULTS.maximumRetainedWarnings());

        assertThrows(
                IllegalArgumentException.class, () -> new ElevationSourceLimits(0, 1, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ElevationSourceLimits(1, 0, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ElevationSourceLimits(1, 1, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ElevationSourceLimits(1, 1, 1, 0, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new ElevationSourceLimits(1, 1, 1, 1, 0));
    }

    @Test
    void interfaceCloseContractMarksFirstFailurePrimaryAndNeverRetries() {
        RuntimeException firstCause = new RuntimeException("first");
        RuntimeException secondCause = new RuntimeException("second");
        ContractSource source = new ContractSource(List.of(firstCause, secondCause));

        SourceException failure = assertThrows(SourceException.class, source::close);
        assertTrue(source.isClosed());
        assertEquals("SOURCE_CLOSE_FAILED", failure.terminal().code());
        assertSame(firstCause, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(secondCause, failure.getSuppressed()[0].getCause());
        assertEquals(2, source.cleanupAttempts);

        source.close();
        assertEquals(2, source.cleanupAttempts);
        assertEquals(IDENTITY, source.metadata().identity());
        assertEquals(1, source.openingDiagnostics().entries().size());
        assertThrows(IllegalStateException.class, () -> source.sample(0, 0));
    }

    private static ElevationSourceMetadata metadata(int columns, int rows, Envelope bounds) {
        return new ElevationSourceMetadata(
                IDENTITY, columns, rows, bounds, UNKNOWN_CRS, ElevationUnit.METRE);
    }

    private static final class ContractSource implements ElevationSource {
        private final ElevationSourceMetadata metadata =
                ElevationSourceValuesTest.metadata(2, 2, new Envelope(0, 0, 1, 1));
        private final DiagnosticReport openingDiagnostics =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "SOURCE_VALUE_SUBSTITUTED",
                                        DiagnosticSeverity.WARNING,
                                        IDENTITY.id(),
                                        Optional.empty(),
                                        "warning",
                                        Map.of())),
                        0);
        private final List<RuntimeException> cleanupFailures;
        private boolean closed;
        private int cleanupAttempts;

        private ContractSource(List<RuntimeException> cleanupFailures) {
            this.cleanupFailures = new ArrayList<>(cleanupFailures);
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public ElevationSourceLimits limits() {
            return ElevationSourceLimits.DEFAULTS;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return openingDiagnostics;
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            metadata.sampleCoordinate(column, row);
            return OptionalDouble.of(0.0);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            SourceException primary = null;
            for (RuntimeException cause : cleanupFailures) {
                cleanupAttempts++;
                SourceException failure = closeFailure(cause);
                if (primary == null) {
                    primary = failure;
                } else {
                    primary.addSuppressed(failure);
                }
            }
            if (primary != null) {
                throw primary;
            }
        }

        private SourceException closeFailure(RuntimeException cause) {
            SourceDiagnostic terminal =
                    new SourceDiagnostic(
                            "SOURCE_CLOSE_FAILED",
                            DiagnosticSeverity.ERROR,
                            IDENTITY.id(),
                            Optional.empty(),
                            "Elevation source close failed",
                            Map.of());
            return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
        }
    }
}
