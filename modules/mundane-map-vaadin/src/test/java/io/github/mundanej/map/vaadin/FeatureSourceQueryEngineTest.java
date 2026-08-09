package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class FeatureSourceQueryEngineTest {
    @Test
    void queriesAndTransformsEveryGeometryFamilyInSourceOrder() {
        PolygonGeometry polygon = polygon(0);
        List<FeatureRecord> records =
                List.of(
                        record("point", new PointGeometry(new Coordinate(0, 0))),
                        record(
                                "multipoint",
                                new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1))),
                        record("line", new LineStringGeometry(CoordinateSequence.of(0, 0, 2, 2))),
                        record(
                                "multiline",
                                MultiLineStringGeometry.ofParts(
                                        List.of(
                                                CoordinateSequence.of(0, 0, 1, 1),
                                                CoordinateSequence.of(2, 2, 3, 3)))),
                        record("polygon", polygon),
                        record(
                                "multipolygon",
                                MultiPolygonGeometry.ofPolygons(List.of(polygon, polygon(4)))));
        RecordingSource source = source("families", records, recognized3857());
        FeatureSourceBinding binding = binding("families", source, AttributeSelection.NONE, false);

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(100, 100, 0, 0, 1),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        assertFalse(result.cancelled());
        assertTrue(result.reports().isEmpty());
        assertEquals(
                List.of("point", "multipoint", "line", "multiline", "polygon", "multipolygon"),
                result.layers().getFirst().features().stream()
                        .map(feature -> feature.id())
                        .toList());
        assertInstanceOf(
                MultiPolygonGeometry.class,
                result.layers().getFirst().features().getLast().geometry());
        PolygonGeometry transformedPolygon =
                (PolygonGeometry) result.layers().getFirst().features().get(4).geometry();
        assertEquals(1, transformedPolygon.holes().size());
        MultiPolygonGeometry transformedMultiPolygon =
                (MultiPolygonGeometry) result.layers().getFirst().features().getLast().geometry();
        assertEquals(4, transformedMultiPolygon.ringCount());
        assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());
        assertEquals(1, source.maximumLiveCursors);
        assertEquals(0, source.liveCursors);
    }

    @Test
    void convertsDisplayEnvelopeToSourceCrsAndReportsMissingMetadata() {
        RecordingSource geographic =
                source(
                        "geographic",
                        List.of(record("origin", new PointGeometry(new Coordinate(0, 0)))),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())));
        FeatureSourceBinding binding =
                binding(
                        "geographic",
                        geographic,
                        AttributeSelection.only(List.of("needed")),
                        false);
        MapViewport viewport = new MapViewport(200, 100, 0, 0, 1000);

        FeatureSourceQueryEngine.Result transformed =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        Envelope expected =
                CrsRegistry.level1()
                        .operation(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_4326)
                        .transformQueryEnvelope(viewport.visibleWorldEnvelope())
                        .transformedEnvelope()
                        .orElseThrow();
        assertEquals(expected, geographic.lastQuery.sourceBounds().orElseThrow());
        assertEquals(AttributeSelection.only(List.of("needed")), geographic.lastQuery.attributes());
        PointGeometry origin =
                (PointGeometry) transformed.layers().getFirst().features().getFirst().geometry();
        assertEquals(0, origin.coordinate().x(), 1.0e-9);
        assertEquals(0, origin.coordinate().y(), 1.0e-9);

        RecordingSource missing = source("missing", List.of(), Optional.empty());
        FeatureSourceQueryEngine.Result failed =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "missing",
                                                        missing,
                                                        AttributeSelection.NONE,
                                                        false),
                                                true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertEquals(
                "CRS_METADATA_MISSING", failed.reports().get("missing").entries().getLast().code());
    }

    @Test
    void skipsInvisibleBindingsWithoutOpeningACursor() {
        RecordingSource source = source("hidden", List.of(), recognized3857());
        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "hidden",
                                                        source,
                                                        AttributeSelection.NONE,
                                                        false),
                                                false)),
                                MapViewport.initial(10, 10),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(result.layers().getFirst().features().isEmpty());
        assertEquals(0, source.openedCursors);
    }

    @Test
    void reportsUnknownUnavailableAndStrictDomainOperations() {
        RecordingSource unknown =
                source(
                        "unknown",
                        List.of(),
                        Optional.of(
                                CrsMetadata.unknown(
                                        Optional.of("LOCAL:UNKNOWN"), Optional.empty())));
        FeatureSourceQueryEngine.Result unknownResult =
                query(
                        unknown,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));
        assertEquals(
                "CRS_DEFINITION_UNKNOWN",
                unknownResult.reports().get("unknown").entries().getLast().code());

        CrsRegistry definitionsOnly =
                CrsRegistry.builder()
                        .registerDefinition(CrsDefinitions.EPSG_4326, List.of())
                        .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                        .build();
        RecordingSource unsupported =
                source(
                        "unsupported",
                        List.of(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())));
        FeatureSourceQueryEngine.Result unsupportedResult =
                query(
                        unsupported,
                        definitionsOnly,
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));
        assertEquals(
                "CRS_TRANSFORM_UNAVAILABLE",
                unsupportedResult.reports().get("unsupported").entries().getLast().code());

        RecordingSource geographic = source("geographic-domain", List.of(), geographicCrs());
        FeatureSourceQueryEngine.Result clipped =
                query(
                        geographic,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_4326,
                        new MapViewport(10, 10, 0, 85, 2));
        assertEquals(
                "CRS_QUERY_ENVELOPE_CLIPPED",
                clipped.reports().get("geographic-domain").entries().getLast().code());
        FeatureSourceQueryEngine.Result outside =
                query(
                        geographic,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_4326,
                        new MapViewport(10, 10, 0, 200, 1));
        assertEquals(
                "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN",
                outside.reports().get("geographic-domain").entries().getLast().code());
        assertEquals(1, geographic.openedCursors);
    }

    @Test
    void cancelsDuringOneLargeGeometryTransformation() {
        double[] packed = new double[200];
        for (int index = 0; index < packed.length; index += 2) {
            packed[index] = index;
            packed[index + 1] = index;
        }
        StaticSource source =
                new StaticSource(
                        "cancellation",
                        record("large", new LineStringGeometry(CoordinateSequence.of(packed))),
                        DiagnosticReport.empty(),
                        false);
        AtomicInteger checks = new AtomicInteger();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "cancellation",
                                                        source,
                                                        AttributeSelection.NONE,
                                                        false),
                                                true)),
                                new MapViewport(100, 100, 0, 0, 10),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                () -> checks.incrementAndGet() > 4);

        assertTrue(result.cancelled());
        assertEquals(5, checks.get());
        assertTrue(source.cursorClosed);
    }

    @Test
    void retainsOpeningWarningsWhenUnexpectedCursorFailureOccurs() {
        DiagnosticReport opening =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "SOURCE_OPENING_WARNING",
                                        DiagnosticSeverity.WARNING,
                                        "runtime",
                                        Optional.of(DiagnosticLocation.empty()),
                                        "Opening warning",
                                        Map.of())),
                        0);
        StaticSource source =
                new StaticSource(
                        "runtime",
                        record("unused", new PointGeometry(new Coordinate(0, 0))),
                        opening,
                        true);

        FeatureSourceQueryEngine.Result result =
                query(
                        source,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));

        assertEquals(
                List.of("SOURCE_OPENING_WARNING", "SOURCE_QUERY_FAILED"),
                result.reports().get("runtime").entries().stream()
                        .map(SourceDiagnostic::code)
                        .toList());
    }

    private static FeatureSourceQueryEngine engine() {
        return new FeatureSourceQueryEngine();
    }

    private static Optional<CrsMetadata> recognized3857() {
        return Optional.of(
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty()));
    }

    private static Optional<CrsMetadata> geographicCrs() {
        return Optional.of(
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_4326, Optional.of("EPSG:4326"), Optional.empty()));
    }

    private static FeatureSourceQueryEngine.Result query(
            FeatureSource source,
            CrsRegistry registry,
            io.github.mundanej.map.api.CrsDefinition mapCrs,
            io.github.mundanej.map.api.CrsDefinition displayCrs,
            MapViewport viewport) {
        return engine().query(
                        List.of(
                                new FeatureSourceQueryEngine.RequestBinding(
                                        binding(
                                                source.metadata().identity().id(),
                                                source,
                                                AttributeSelection.NONE,
                                                false),
                                        true)),
                        viewport,
                        registry,
                        mapCrs,
                        displayCrs,
                        CancellationToken.none());
    }

    private static FeatureRecord record(String id, Geometry geometry) {
        return new FeatureRecord(id, id, geometry, Map.of("needed", "value", "ignored", 2L));
    }

    private static PolygonGeometry polygon(double offset) {
        return new PolygonGeometry(
                CoordinateSequence.of(
                        offset,
                        offset,
                        offset + 4,
                        offset,
                        offset + 4,
                        offset + 4,
                        offset,
                        offset + 4,
                        offset,
                        offset),
                List.of(
                        CoordinateSequence.of(
                                offset + 1,
                                offset + 1,
                                offset + 2,
                                offset + 1,
                                offset + 2,
                                offset + 2,
                                offset + 1,
                                offset + 2,
                                offset + 1,
                                offset + 1)));
    }

    private static FeatureSourceBinding binding(
            String id, FeatureSource source, AttributeSelection attributes, boolean owned) {
        BindingFactory factory =
                owned ? FeatureSourceBinding::owned : FeatureSourceBinding::borrowed;
        return factory.create(
                id,
                id,
                source,
                marker(),
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 30, 40), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1),
                SolidFillSymbol.of(Rgba.rgb(40, 50, 60), 1),
                attributes,
                Optional.empty());
    }

    private static VectorMarkerSymbol marker() {
        VectorPath path =
                VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build();
        return VectorMarkerSymbol.filledScreen(
                path, new Envelope(0, 0, 1, 1), Rgba.rgb(10, 20, 30), 8, 1);
    }

    @FunctionalInterface
    private interface BindingFactory {
        FeatureSourceBinding create(
                String id,
                String name,
                FeatureSource source,
                VectorMarkerSymbol marker,
                SolidLineSymbol line,
                SolidFillSymbol fill,
                AttributeSelection attributes,
                Optional<io.github.mundanej.map.api.FeatureQueryLimits> tighterLimits);
    }

    private static final class RecordingSource implements FeatureSource {
        private final InMemoryFeatureSource delegate;
        private FeatureQuery lastQuery;
        private int liveCursors;
        private int maximumLiveCursors;
        private int openedCursors;

        private RecordingSource(InMemoryFeatureSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            lastQuery = query;
            openedCursors++;
            liveCursors++;
            maximumLiveCursors = Math.max(maximumLiveCursors, liveCursors);
            FeatureCursor cursor = delegate.openCursor(query, cancellation);
            return new FeatureCursor() {
                private boolean closed;

                @Override
                public boolean advance() {
                    return cursor.advance();
                }

                @Override
                public FeatureRecord current() {
                    return cursor.current();
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return cursor.diagnostics();
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        cursor.close();
                        liveCursors--;
                    }
                }
            };
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class StaticSource implements FeatureSource {
        private final InMemoryFeatureSource metadataDelegate;
        private final FeatureRecord record;
        private final DiagnosticReport opening;
        private final boolean failOnOpen;
        private boolean closed;
        private boolean cursorClosed;

        private StaticSource(
                String id, FeatureRecord record, DiagnosticReport opening, boolean failOnOpen) {
            metadataDelegate =
                    InMemoryFeatureSource.open(
                            new SourceIdentity(id, id),
                            List.of(),
                            Optional.empty(),
                            recognized3857(),
                            FeatureSourceLimits.LEVEL_1);
            this.record = record;
            this.opening = opening;
            this.failOnOpen = failOnOpen;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return metadataDelegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return FeatureSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return opening;
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (failOnOpen) {
                throw new IllegalStateException("deliberate cursor failure");
            }
            return new FeatureCursor() {
                private boolean advanced;

                @Override
                public boolean advance() {
                    if (advanced) {
                        return false;
                    }
                    advanced = true;
                    return true;
                }

                @Override
                public FeatureRecord current() {
                    if (!advanced) {
                        throw new IllegalStateException("cursor is not positioned");
                    }
                    return record;
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return DiagnosticReport.empty();
                }

                @Override
                public boolean isClosed() {
                    return cursorClosed;
                }

                @Override
                public void close() {
                    cursorClosed = true;
                }
            };
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            metadataDelegate.close();
        }
    }

    private static RecordingSource source(
            String id, List<FeatureRecord> records, Optional<CrsMetadata> crs) {
        return new RecordingSource(
                InMemoryFeatureSource.open(
                        new SourceIdentity(id, id),
                        records,
                        Optional.empty(),
                        crs,
                        FeatureSourceLimits.LEVEL_1));
    }
}
