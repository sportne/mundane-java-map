package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Executable compile sketches for the G4-001 source-contract decision. */
class SourceContractSketchesTest {
    private static final String SOURCE_ID = "sketch-source";
    private static final FeatureQueryLimits FEATURE_LIMITS =
            new FeatureQueryLimits(100, 100, 1_000, 1_000, 4_096, 65_536, 16);
    private static final RasterRequestLimits RASTER_LIMITS =
            new RasterRequestLimits(1_024, 64, 4_096, 65_536, 65_536, 16);

    @Test
    void inMemoryFeatureSourceSupportsEarlyCursorCloseAndRetainedValues() {
        FeatureRecord expected =
                new FeatureRecord(
                        "record:1",
                        "Example",
                        new PointGeometry(new Coordinate(1.0, 2.0)),
                        Map.of("name", "example"));
        InMemoryFeatureSource source = new InMemoryFeatureSource(List.of(expected));

        FeatureRecord retained;
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertTrue(cursor.advance());
            retained = cursor.current();
            assertTrue(cursor.diagnostics().entries().isEmpty());
        }

        source.close();
        assertTrue(source.isClosed());
        assertEquals(expected, retained);
        assertEquals(SOURCE_ID, source.metadata().identity().id());
    }

    @Test
    void exhaustedCursorRepeatedlyReturnsFalseWithoutClosing() {
        InMemoryFeatureSource source = new InMemoryFeatureSource(List.of());

        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
            assertFalse(cursor.isClosed());
            assertThrows(IllegalStateException.class, cursor::current);
        }
    }

    @Test
    void sourceReportDeliveryAndNamedOwnershipBindingsHaveExplicitTeardown() {
        SourceReportObserver observer = new SourceReportObserver();
        observer.publish(
                new MapSourceReportEvent(
                        "layer:1",
                        Optional.empty(),
                        Optional.of(new SyntheticRasterSource().openingDiagnostics())));
        assertEquals("layer:1", observer.lastEvent().orElseThrow().layerId());

        InMemoryFeatureSource borrowedSource = new InMemoryFeatureSource(List.of());
        try (FeatureSourceBinding binding = FeatureSourceBinding.borrowed(borrowedSource)) {
            assertEquals(borrowedSource, binding.source());
            assertFalse(borrowedSource.isClosed());
        }
        assertFalse(borrowedSource.isClosed());

        InMemoryFeatureSource ownedSource = new InMemoryFeatureSource(List.of());
        try (FeatureSourceBinding binding = FeatureSourceBinding.owned(ownedSource)) {
            assertEquals(ownedSource, binding.source());
            assertFalse(ownedSource.isClosed());
        }
        assertTrue(ownedSource.isClosed());
    }

    @Test
    void inMemoryFeatureSourceMapsPreCancellationToOneFatalDiagnostic() {
        InMemoryFeatureSource source =
                new InMemoryFeatureSource(
                        List.of(
                                new FeatureRecord(
                                        "record:1",
                                        "Example",
                                        new PointGeometry(new Coordinate(1.0, 2.0)),
                                        Map.of())));
        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();

        try (FeatureCursor cursor = source.openCursor(FeatureQuery.all(), cancellation.token())) {
            SourceException failure = assertThrows(SourceException.class, cursor::advance);
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertEquals(DiagnosticSeverity.ERROR, failure.terminal().severity());
            assertEquals(1, failure.report().entries().size());
        }
    }

    @Test
    void syntheticRasterSourceReturnsOwnedPixelsAndOpeningWarning() {
        SyntheticRasterSource source = new SyntheticRasterSource();
        RasterRequest request =
                new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 2, Optional.of(RASTER_LIMITS));

        RasterRead read = source.read(request, CancellationToken.none());
        int[] expected = {0x000000ff, 0x010001ff, 0x000101ff, 0x010100ff};
        assertArrayEquals(expected, read.pixels().rgba());
        assertEquals(request.sourceWindow(), read.sourceWindow());
        assertEquals(
                "SOURCE_VALUE_SUBSTITUTED", source.openingDiagnostics().entries().get(0).code());

        int[] copy = read.pixels().rgba();
        copy[0] = 0;
        assertArrayEquals(expected, read.pixels().rgba());
    }

    @Test
    void syntheticRasterSourceMapsMidOperationCancellationWithoutPublishingPixels() {
        SyntheticRasterSource source = new SyntheticRasterSource();
        RasterRequest request =
                new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 2, Optional.empty());
        CancellationToken cancellation = new CountingCancellationToken(2);

        SourceException failure =
                assertThrows(SourceException.class, () -> source.read(request, cancellation));
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertEquals(Map.of("operation", "raster-read"), failure.terminal().context());
        assertEquals(OptionalLong.of(0L), failure.terminal().location().orElseThrow().byteOffset());
        assertFalse(source.isClosed());
    }

    @Test
    void selectedAttributesDefensivelyPreserveRequestedOrder() {
        SelectedAttributes selected = new SelectedAttributes(List.of("second", "first"));
        FeatureQuery query =
                new FeatureQuery(Optional.empty(), selected, Optional.of(FEATURE_LIMITS));

        assertEquals(List.of("second", "first"), query.attributes().orderedNames());
    }

    interface CancellationToken {
        boolean isCancellationRequested();

        static CancellationToken none() {
            return () -> false;
        }
    }

    static final class CancellationSource {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CancellationToken token = cancelled::get;

        CancellationToken token() {
            return token;
        }

        void cancel() {
            cancelled.set(true);
        }
    }

    interface FeatureSource extends AutoCloseable {
        FeatureSourceMetadata metadata();

        FeatureSourceLimits limits();

        DiagnosticReport openingDiagnostics();

        FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation);

        boolean isClosed();

        @Override
        void close();
    }

    interface FeatureCursor extends AutoCloseable {
        boolean advance();

        FeatureRecord current();

        DiagnosticReport diagnostics();

        boolean isClosed();

        @Override
        void close();
    }

    interface RasterSource extends AutoCloseable {
        RasterSourceMetadata metadata();

        RasterSourceLimits limits();

        DiagnosticReport openingDiagnostics();

        RasterRead read(RasterRequest request, CancellationToken cancellation);

        boolean isClosed();

        @Override
        void close();
    }

    record MapSourceReportEvent(
            String layerId,
            Optional<DiagnosticReport> previous,
            Optional<DiagnosticReport> current) {}

    interface MapSourceReportListener {
        void onMapSourceReportChanged(MapSourceReportEvent event);
    }

    static final class SourceReportObserver implements MapSourceReportListener {
        private Optional<MapSourceReportEvent> lastEvent = Optional.empty();

        void publish(MapSourceReportEvent event) {
            onMapSourceReportChanged(event);
        }

        Optional<MapSourceReportEvent> lastEvent() {
            return lastEvent;
        }

        @Override
        public void onMapSourceReportChanged(MapSourceReportEvent event) {
            lastEvent = Optional.of(event);
        }
    }

    static final class FeatureSourceBinding implements AutoCloseable {
        private final FeatureSource source;
        private final boolean owned;

        private FeatureSourceBinding(FeatureSource source, boolean owned) {
            this.source = source;
            this.owned = owned;
        }

        static FeatureSourceBinding borrowed(FeatureSource source) {
            return new FeatureSourceBinding(source, false);
        }

        static FeatureSourceBinding owned(FeatureSource source) {
            return new FeatureSourceBinding(source, true);
        }

        FeatureSource source() {
            return source;
        }

        @Override
        public void close() {
            if (owned) {
                source.close();
            }
        }
    }

    record SourceIdentity(String id, String displayName) {}

    record FeatureSourceMetadata(
            SourceIdentity identity,
            Optional<Envelope> extent,
            OptionalLong featureCount,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadataSketch> crs) {}

    record RasterSourceMetadata(
            SourceIdentity identity,
            int width,
            int height,
            Optional<Envelope> mapBounds,
            Optional<CrsMetadataSketch> crs) {}

    record AttributeSchema(List<String> orderedFieldNames) {
        AttributeSchema {
            orderedFieldNames = List.copyOf(orderedFieldNames);
        }
    }

    record CrsMetadataSketch(String canonicalIdentifier) {}

    record FeatureRecord(
            String id, String name, Geometry geometry, Map<String, Object> attributes) {
        FeatureRecord {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }

    sealed interface AttributeSelection permits AllAttributes, NoAttributes, SelectedAttributes {
        List<String> orderedNames();
    }

    enum AllAttributes implements AttributeSelection {
        INSTANCE;

        @Override
        public List<String> orderedNames() {
            return List.of();
        }
    }

    enum NoAttributes implements AttributeSelection {
        INSTANCE;

        @Override
        public List<String> orderedNames() {
            return List.of();
        }
    }

    record SelectedAttributes(List<String> orderedNames) implements AttributeSelection {
        SelectedAttributes {
            orderedNames = List.copyOf(orderedNames);
        }
    }

    record FeatureQuery(
            Optional<Envelope> sourceBounds,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits) {
        static FeatureQuery all() {
            return new FeatureQuery(Optional.empty(), AllAttributes.INSTANCE, Optional.empty());
        }
    }

    record FeatureQueryLimits(
            long recordsExamined,
            long recordsReturned,
            long coordinatesReturned,
            long attributeValuesReturned,
            long decodedTextCharactersReturned,
            long ownedPayloadBytes,
            int retainedWarnings) {}

    record FeatureSourceLimits(FeatureQueryLimits queryLimits) {}

    record RasterWindow(int column, int row, int width, int height) {}

    record RasterRequest(
            RasterWindow sourceWindow,
            int outputWidth,
            int outputHeight,
            Optional<RasterRequestLimits> tighterLimits) {}

    record RasterRequestLimits(
            long sourceWindowPixels,
            int outputDimension,
            long outputPixels,
            long decodedIntermediateBytes,
            long ownedPayloadBytes,
            int retainedWarnings) {}

    record RasterSourceLimits(RasterRequestLimits requestLimits) {}

    record RasterRead(
            RasterWindow sourceWindow, RgbaPixelBuffer pixels, DiagnosticReport diagnostics) {}

    static final class RgbaPixelBuffer {
        private final int width;
        private final int height;
        private final int[] rgba;

        RgbaPixelBuffer(int width, int height, int[] rgba) {
            this.width = width;
            this.height = height;
            this.rgba = rgba.clone();
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        int[] rgba() {
            return rgba.clone();
        }
    }

    enum DiagnosticSeverity {
        WARNING,
        ERROR
    }

    record DiagnosticLocation(
            Optional<String> component,
            OptionalLong recordNumber,
            OptionalInt partIndex,
            OptionalInt fieldIndex,
            Optional<String> fieldName,
            OptionalLong byteOffset) {}

    record SourceDiagnostic(
            String code,
            DiagnosticSeverity severity,
            String sourceId,
            Optional<DiagnosticLocation> location,
            String message,
            Map<String, String> context) {
        SourceDiagnostic {
            context = Collections.unmodifiableMap(new TreeMap<>(context));
        }
    }

    record DiagnosticReport(List<SourceDiagnostic> entries, long omittedWarningCount) {
        DiagnosticReport {
            entries = List.copyOf(entries);
        }

        static DiagnosticReport empty() {
            return new DiagnosticReport(List.of(), 0L);
        }
    }

    @SuppressWarnings("serial")
    static final class SourceException extends RuntimeException {
        private final DiagnosticReport report;
        private final SourceDiagnostic terminal;

        SourceException(DiagnosticReport report, SourceDiagnostic terminal) {
            super(terminal.code());
            this.report = report;
            this.terminal = terminal;
        }

        DiagnosticReport report() {
            return report;
        }

        SourceDiagnostic terminal() {
            return terminal;
        }
    }

    static final class InMemoryFeatureSource implements FeatureSource {
        private final List<FeatureRecord> records;
        private final FeatureSourceMetadata metadata;
        private final FeatureSourceLimits limits = new FeatureSourceLimits(FEATURE_LIMITS);
        private boolean cursorOpen;
        private boolean closed;

        InMemoryFeatureSource(List<FeatureRecord> records) {
            this.records = List.copyOf(records);
            this.metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity(SOURCE_ID, "Sketch source"),
                            Optional.empty(),
                            OptionalLong.of(records.size()),
                            Optional.empty(),
                            Optional.empty());
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public FeatureSourceLimits limits() {
            return limits;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (closed || cursorOpen) {
                throw new IllegalStateException("Source is unavailable for a new cursor.");
            }
            cursorOpen = true;
            return new InMemoryCursor(records, cancellation, () -> cursorOpen = false);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            cursorOpen = false;
        }
    }

    static final class InMemoryCursor implements FeatureCursor {
        private final List<FeatureRecord> records;
        private final CancellationToken cancellation;
        private final Runnable release;
        private int index = -1;
        private boolean exhausted;
        private boolean released;
        private boolean closed;

        InMemoryCursor(
                List<FeatureRecord> records, CancellationToken cancellation, Runnable release) {
            this.records = records;
            this.cancellation = cancellation;
            this.release = release;
        }

        @Override
        public boolean advance() {
            requireOpen();
            if (exhausted) {
                return false;
            }
            if (cancellation.isCancellationRequested()) {
                closed = true;
                releaseOnce();
                throw sourceFailure("SOURCE_CANCELLED", "feature-query");
            }
            if (index + 1 >= records.size()) {
                exhausted = true;
                index = records.size();
                releaseOnce();
                return false;
            }
            index++;
            return true;
        }

        @Override
        public FeatureRecord current() {
            requireOpen();
            if (index < 0 || index >= records.size()) {
                throw new IllegalStateException("Cursor has no current record.");
            }
            return records.get(index);
        }

        @Override
        public DiagnosticReport diagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                releaseOnce();
            }
        }

        private void releaseOnce() {
            if (!released) {
                released = true;
                release.run();
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Cursor is closed.");
            }
        }
    }

    static final class SyntheticRasterSource implements RasterSource {
        private static final DiagnosticReport OPENING_WARNING =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "SOURCE_VALUE_SUBSTITUTED",
                                        DiagnosticSeverity.WARNING,
                                        SOURCE_ID,
                                        Optional.empty(),
                                        "One bounded value was substituted.",
                                        Map.of("component", "synthetic"))),
                        0L);
        private final RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        new SourceIdentity(SOURCE_ID, "Synthetic raster"),
                        2,
                        2,
                        Optional.of(new Envelope(0.0, 0.0, 2.0, 2.0)),
                        Optional.empty());
        private final RasterSourceLimits limits = new RasterSourceLimits(RASTER_LIMITS);
        private boolean closed;

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return limits;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return OPENING_WARNING;
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            if (closed) {
                throw new IllegalStateException("Raster source is closed.");
            }
            int[] pixels = new int[request.outputWidth() * request.outputHeight()];
            for (int row = 0; row < request.outputHeight(); row++) {
                if (cancellation.isCancellationRequested()) {
                    throw sourceFailure("SOURCE_CANCELLED", "raster-read");
                }
                for (int column = 0; column < request.outputWidth(); column++) {
                    pixels[row * request.outputWidth() + column] =
                            (column << 24) | (row << 16) | ((column ^ row) << 8) | 0xff;
                }
            }
            if (cancellation.isCancellationRequested()) {
                throw sourceFailure("SOURCE_CANCELLED", "raster-read");
            }
            return new RasterRead(
                    request.sourceWindow(),
                    new RgbaPixelBuffer(request.outputWidth(), request.outputHeight(), pixels),
                    DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class CountingCancellationToken implements CancellationToken {
        private final int cancelAtCheck;
        private int checks;

        CountingCancellationToken(int cancelAtCheck) {
            this.cancelAtCheck = cancelAtCheck;
        }

        @Override
        public boolean isCancellationRequested() {
            checks++;
            return checks >= cancelAtCheck;
        }
    }

    private static SourceException sourceFailure(String code, String operation) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        SOURCE_ID,
                        Optional.of(
                                new DiagnosticLocation(
                                        Optional.of("sketch"),
                                        OptionalLong.empty(),
                                        OptionalInt.empty(),
                                        OptionalInt.empty(),
                                        Optional.empty(),
                                        OptionalLong.of(0L))),
                        "The bounded source operation was cancelled.",
                        Map.of("operation", operation));
        return new SourceException(new DiagnosticReport(List.of(terminal), 0L), terminal);
    }
}
