package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewFeatureSourceTest {
    @Test
    void rendersHitsAndSelectsSourceRecordsWithoutSyntheticFeatures() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    InMemoryFeatureSource source =
                            source(
                                    "source",
                                    new FeatureRecord(
                                            "points",
                                            "",
                                            new MultiPointGeometry(
                                                    io.github.mundanej.map.api.CoordinateSequence
                                                            .of(-10.0, 0.0, 10.0, 0.0)),
                                            Map.of()));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0.0, 0.0, 1.0));
                    view.setLayerBindings(List.of(binding("layer", source, false)));

                    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(
                            "points",
                            view.hitTest(40.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "points",
                            view.hitTest(60.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    view.setSelection(new FeatureSelection("layer", "offscreen-source-id"));
                    assertEquals("offscreen-source-id", view.selection().orElseThrow().featureId());
                    assertTrue(view.sourceReports().isEmpty());

                    view.close();
                    assertFalse(source.isClosed());
                });
    }

    @Test
    void ownedBindingsCloseOnReplacementAndOutsideQueriesPublishAStableWarning() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    InMemoryFeatureSource source =
                            source(
                                    "owned",
                                    new FeatureRecord(
                                            "point",
                                            "",
                                            new PointGeometry(new Coordinate(0.0, 0.0)),
                                            Map.of()));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 100_000_000.0, 0.0, 1.0));
                    view.setLayerBindings(List.of(binding("owned-layer", source, true)));

                    view.hitTest(50.0, 50.0, 0.0);
                    assertEquals(
                            "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN",
                            view.sourceReports().get("owned-layer").entries().getFirst().code());

                    view.setLayerBindings(List.of());
                    assertTrue(source.isClosed());
                    assertTrue(view.sourceReports().isEmpty());
                    view.close();
                });
    }

    @Test
    void rejectsMissingUnknownAndDuplicateSourceIdentityBeforeAttachment() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureRecord record =
                            new FeatureRecord(
                                    "point",
                                    "",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    Map.of());
                    InMemoryFeatureSource missing =
                            InMemoryFeatureSource.open(
                                    new SourceIdentity("missing", "missing"), List.of(record));
                    InMemoryFeatureSource unknown =
                            InMemoryFeatureSource.open(
                                    new SourceIdentity("unknown", "unknown"),
                                    List.of(record),
                                    Optional.empty(),
                                    Optional.of(
                                            CrsMetadata.unknown(
                                                    Optional.of("LOCAL"), Optional.empty())),
                                    FeatureSourceLimits.LEVEL_1);
                    MapView view = TestMapViews.identity();

                    CrsException missingFailure =
                            assertThrows(
                                    CrsException.class,
                                    () ->
                                            view.setLayerBindings(
                                                    List.of(binding("missing", missing, false))));
                    assertEquals("CRS_METADATA_MISSING", missingFailure.problem().code());
                    CrsException unknownFailure =
                            assertThrows(
                                    CrsException.class,
                                    () ->
                                            view.setLayerBindings(
                                                    List.of(binding("unknown", unknown, false))));
                    assertEquals("CRS_DEFINITION_UNKNOWN", unknownFailure.problem().code());

                    InMemoryFeatureSource first = source("duplicate", record);
                    InMemoryFeatureSource second = source("duplicate", record);
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    binding("first", first, false),
                                                    binding("second", second, false))));
                    assertTrue(view.layerBindings().isEmpty());
                    view.close();
                });
    }

    @Test
    void featureFactoryRejectsAContractThatLiesAboutItsMarkerRole() {
        InMemoryFeatureSource source =
                source(
                        "role",
                        new FeatureRecord(
                                "point",
                                "",
                                new PointGeometry(new Coordinate(0.0, 0.0)),
                                Map.of()));
        MarkerSymbol invalid =
                new MarkerSymbol() {
                    @Override
                    public SymbolRole role() {
                        return SymbolRole.LINE;
                    }

                    @Override
                    public double opacity() {
                        return 1.0;
                    }

                    @Override
                    public SymbolRendererKey rendererKey() {
                        return new SymbolRendererKey("test.invalid-role");
                    }
                };
        MapLayerBinding valid = binding("valid", source, false);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MapLayerBinding.borrowedFeature(
                                "invalid", "invalid", source, invalid, valid.line(), valid.fill()));
    }

    @Test
    void preservesCursorWarningsWhenViewStagingAccountingFailsAndClosesCursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    WarningSource source = new WarningSource();
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0.0, 0.0, 1.0));
                    view.setLayerBindings(List.of(binding("warning", source, false)));

                    assertTrue(view.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(source.cursorClosed);
                    assertEquals(
                            List.of("TEST_WARNING", "SOURCE_LIMIT_EXCEEDED"),
                            view.sourceReports().get("warning").entries().stream()
                                    .map(SourceDiagnostic::code)
                                    .toList());
                    view.close();
                });
    }

    @Test
    void replacementKeepsCloseFailurePrimaryAndClearsDetachedReports() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CloseFailingSource source = new CloseFailingSource();
                    MapView view = TestMapViews.identity();
                    view.setLayerBindings(List.of(binding("closing", source, true)));
                    view.addMapSourceReportListener(
                            ignored -> {
                                throw new IllegalStateException("listener");
                            });

                    SourceException failure =
                            assertThrows(
                                    SourceException.class, () -> view.setLayerBindings(List.of()));
                    assertEquals("TEST_CLOSE_FAILED", failure.terminal().code());
                    assertEquals(1, failure.getSuppressed().length);
                    assertEquals("listener", failure.getSuppressed()[0].getMessage());
                    assertTrue(view.sourceReports().isEmpty());
                    assertTrue(view.layerBindings().isEmpty());
                });
    }

    @Test
    void crsPreflightDiscardsTheCompleteSourceLayerBeforeHitEvaluation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    InMemoryFeatureSource source =
                            InMemoryFeatureSource.open(
                                    new SourceIdentity("geographic", "geographic"),
                                    List.of(
                                            new FeatureRecord(
                                                    "valid",
                                                    "",
                                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                                    Map.of()),
                                            new FeatureRecord(
                                                    "invalid",
                                                    "",
                                                    new LineStringGeometry(
                                                            CoordinateSequence.of(
                                                                    0.0, 0.0, 0.0, 90.0)),
                                                    Map.of())),
                                    Optional.empty(),
                                    Optional.of(
                                            CrsMetadata.recognized(
                                                    CrsDefinitions.EPSG_4326,
                                                    Optional.empty(),
                                                    Optional.empty())),
                                    FeatureSourceLimits.LEVEL_1);
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0.0, 0.0, 1_000_000.0));
                    view.setLayerBindings(List.of(binding("geographic", source, false)));

                    assertTrue(view.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                    assertEquals(
                            "CRS_COORDINATE_OUT_OF_DOMAIN",
                            view.sourceReports().get("geographic").entries().getLast().code());
                    view.close();
                });
    }

    @Test
    void fitUsesSourceMetadataWithoutOpeningACursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    WarningSource source = new WarningSource();
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setLayerBindings(List.of(binding("fit", source, false)));

                    view.fitToData(10.0);
                    assertEquals(0, source.openCount);
                    assertEquals(0.0, view.viewport().centerX());
                    assertEquals(0.0, view.viewport().centerY());
                    view.close();
                });
    }

    private static InMemoryFeatureSource source(String id, FeatureRecord record) {
        return InMemoryFeatureSource.open(
                new SourceIdentity(id, id),
                List.of(record),
                Optional.empty(),
                Optional.of(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty())),
                FeatureSourceLimits.LEVEL_1);
    }

    private static MapLayerBinding binding(String id, FeatureSource source, boolean owned) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(20, 90, 180), 10.0, 1.0);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 90, 180),
                                new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        var fill = SolidFillSymbol.of(Rgba.rgb(20, 90, 180), 1.0);
        return owned
                ? MapLayerBinding.ownedFeature(id, id, source, marker, line, fill)
                : MapLayerBinding.borrowedFeature(id, id, source, marker, line, fill);
    }

    private static class WarningSource implements FeatureSource {
        private final FeatureRecord record =
                new FeatureRecord(
                        "record", "", new PointGeometry(new Coordinate(0.0, 0.0)), Map.of());
        private final FeatureSourceLimits limits =
                new FeatureSourceLimits(new FeatureQueryLimits(10, 10, 10, 10, 100, 1, 10));
        private final FeatureSourceMetadata metadata =
                new FeatureSourceMetadata(
                        new SourceIdentity("warning-source", "warning-source"),
                        Optional.of(record.geometry().envelope()),
                        OptionalLong.of(1),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.empty(),
                                        Optional.empty())));
        private final DiagnosticReport warning =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "TEST_WARNING",
                                        DiagnosticSeverity.WARNING,
                                        metadata.identity().id(),
                                        Optional.of(DiagnosticLocation.empty()),
                                        "Test warning",
                                        Map.of())),
                        0);
        private boolean cursorClosed;
        private boolean closed;
        private int openCount;

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
            openCount++;
            return new FeatureCursor() {
                private int state;

                @Override
                public boolean advance() {
                    return state++ == 0;
                }

                @Override
                public FeatureRecord current() {
                    if (state != 1) {
                        throw new IllegalStateException("no current record");
                    }
                    return record;
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return state == 0 ? DiagnosticReport.empty() : warning;
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
        }
    }

    private static final class CloseFailingSource extends WarningSource {
        @Override
        public void close() {
            SourceDiagnostic terminal =
                    new SourceDiagnostic(
                            "TEST_CLOSE_FAILED",
                            DiagnosticSeverity.ERROR,
                            metadata().identity().id(),
                            Optional.of(DiagnosticLocation.empty()),
                            "Test close failure",
                            Map.of());
            throw new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
        }
    }
}
