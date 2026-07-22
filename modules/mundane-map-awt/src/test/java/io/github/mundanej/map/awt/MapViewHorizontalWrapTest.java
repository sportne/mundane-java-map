package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapViewHorizontalWrapTest {
    private static final HorizontalWrap WRAP = HorizontalWrap.webMercator();

    @Test
    void profileAndBindingOptInRemainExplicitTransactionalAndPreAttachmentOnly() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source = source("lifecycle", 0.0, FeatureQueryLimits.LEVEL_1);
                    MapLayerBinding binding = binding("repeating", source);
                    assertEquals(HorizontalWrapMode.NONE, binding.horizontalWrapMode());
                    binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);

                    MapView view = TestMapViews.identity();
                    view.setSize(200, 100);
                    assertThrows(
                            IllegalStateException.class,
                            () -> view.setLayerBindings(List.of(binding)));
                    assertTrue(view.layerBindings().isEmpty());

                    view.setHorizontalWrap(WRAP);
                    assertEquals(Optional.of(WRAP), view.horizontalWrap());
                    view.setLayerBindings(List.of(binding));
                    assertThrows(
                            IllegalStateException.class,
                            () -> binding.setHorizontalWrapMode(HorizontalWrapMode.NONE));
                    assertThrows(IllegalStateException.class, view::clearHorizontalWrap);
                    assertEquals(Optional.of(WRAP), view.horizontalWrap());

                    view.setLayerBindings(List.of());
                    view.clearHorizontalWrap();
                    assertTrue(view.horizontalWrap().isEmpty());
                    binding.setHorizontalWrapMode(HorizontalWrapMode.NONE);
                    view.close();
                    assertFalse(source.isClosed());
                });
    }

    @Test
    void profileReplacementFailureKeepsOldProfileSelectionAndExactDisplayCrs() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CrsDefinition sameBoundsDifferentCrs =
                            new CrsDefinition(
                                    "CUSTOM:3857-BOUNDS",
                                    CrsDefinitions.EPSG_3857.kind(),
                                    CrsDefinitions.EPSG_3857.xAxis(),
                                    CrsDefinitions.EPSG_3857.yAxis(),
                                    CrsDefinitions.EPSG_3857.coordinateDomain());
                    var registry =
                            io.github.mundanej.map.core.CrsRegistry.builder()
                                    .registerDefinition(sameBoundsDifferentCrs, List.of())
                                    .build();
                    MapView wrongCrs =
                            new MapView(registry, sameBoundsDifferentCrs, sameBoundsDifferentCrs);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> wrongCrs.setHorizontalWrap(HorizontalWrap.webMercator()));
                    assertTrue(wrongCrs.horizontalWrap().isEmpty());
                    wrongCrs.close();

                    RecordingSource source = source("transaction", 0.0, FeatureQueryLimits.LEVEL_1);
                    MapView view = TestMapViews.identity();
                    view.setSize(600, 120);
                    view.setViewport(new MapViewport(600, 120, 0.0, 0.0, WRAP.period() / 200.0));
                    view.setHorizontalWrap(WRAP);
                    view.setLayerBindings(List.of(repeating(binding("transaction", source))));
                    FeatureSelection selection = new FeatureSelection("transaction", "point");
                    view.setSelection(selection);

                    HorizontalWrap tooNarrow =
                            new HorizontalWrap(
                                    WRAP.canonicalMinimumX(),
                                    WRAP.canonicalMaximumX(),
                                    1,
                                    WRAP.maximumAbsoluteCopyIndex());
                    assertThrows(
                            io.github.mundanej.map.core.HorizontalWrapException.class,
                            () -> view.setHorizontalWrap(tooNarrow));
                    assertEquals(Optional.of(WRAP), view.horizontalWrap());
                    assertEquals(Optional.of(selection), view.selection());
                    view.close();
                });
    }

    @Test
    void splitSeamQueriesDeduplicateIdentityAndRenderOneLogicalPointCopy() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source =
                            source(
                                    "seam",
                                    -WebMercatorProjection.WORLD_LIMIT,
                                    FeatureQueryLimits.LEVEL_1);
                    MapLayerBinding binding = repeating(binding("global", source));
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(200, 100);
                    view.setViewport(
                            new MapViewport(200, 100, WebMercatorProjection.WORLD_LIMIT, 0.0, 1.0));
                    view.setLayerBindings(List.of(binding));

                    assertEquals(
                            "point",
                            view.hitTest(100.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertEquals(2, source.queries.size());
                    assertEquals(2, source.closedCursors);
                    assertTrue(view.sourceReports().isEmpty());
                    view.close();
                });
    }

    @Test
    void conflictingDuplicateIdentityFailsTheCompleteSplitQuery() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source =
                            source(
                                    "conflict",
                                    WRAP.canonicalMinimumX(),
                                    FeatureQueryLimits.LEVEL_1);
                    source.secondQueryRecord =
                            point("point", "Conflicting", WRAP.canonicalMinimumX() + 1.0);
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(200, 100);
                    view.setViewport(new MapViewport(200, 100, WRAP.canonicalMaximumX(), 0.0, 1.0));
                    view.setLayerBindings(List.of(repeating(binding("conflict", source))));

                    assertTrue(view.hitTest(100.0, 50.0, 1.0).topmost().isEmpty());
                    assertEquals(
                            "SOURCE_DUPLICATE_FEATURE_ID",
                            view.sourceReports().get("conflict").entries().getLast().code());
                    assertEquals(2, source.closedCursors);
                    view.close();
                });
    }

    @Test
    void laterSplitFailureRetainsWarningsInCanonicalQueryOrder() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source =
                            source(
                                    "warnings",
                                    WRAP.canonicalMinimumX(),
                                    FeatureQueryLimits.LEVEL_1);
                    source.firstQueryDiagnostics = warning("warnings", "FIRST_QUERY_WARNING");
                    source.secondQueryDiagnostics = warning("warnings", "SECOND_QUERY_WARNING");
                    source.secondQueryFailure =
                            failure(
                                    "warnings",
                                    warning("warnings", "SECOND_QUERY_WARNING"),
                                    "SECOND_QUERY_FAILED");
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(200, 100);
                    view.setViewport(new MapViewport(200, 100, WRAP.canonicalMaximumX(), 0.0, 1.0));
                    view.setLayerBindings(List.of(repeating(binding("warnings", source))));

                    assertTrue(view.hitTest(100.0, 50.0, 1.0).topmost().isEmpty());
                    assertEquals(
                            List.of(
                                    "FIRST_QUERY_WARNING",
                                    "SECOND_QUERY_WARNING",
                                    "SECOND_QUERY_FAILED"),
                            view.sourceReports().get("warnings").entries().stream()
                                    .map(SourceDiagnostic::code)
                                    .toList());
                    assertEquals(2, source.closedCursors);
                    view.close();
                });
    }

    @Test
    void fullWorldQueryRendersMarkersAndLabelsInAscendingVisualCopies() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source =
                            source(
                                    "multiworld",
                                    List.of(
                                            point("first", "First", 0.0),
                                            point("second", "Second", 0.0)),
                                    FeatureQueryLimits.LEVEL_1);
                    MapLayerBinding binding = repeating(binding("global", source));
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(600, 120);
                    view.setViewport(new MapViewport(600, 120, 0.0, 0.0, WRAP.period() / 200.0));
                    view.setLayerBindings(List.of(binding));

                    BufferedImage image = paint(view, 600, 120);
                    assertEquals(1, source.queries.size());
                    for (double x : new double[] {100.0, 300.0, 500.0}) {
                        assertEquals(
                                "second",
                                view.hitTest(x, 60.0, 1.0).topmost().orElseThrow().featureId());
                        assertTrue(countNonWhite(image, (int) x + 7, 40, (int) x + 65, 80) > 10);
                    }
                    view.close();
                });
    }

    @Test
    void multipointsRenderAcrossCopiesAndLargeToleranceReturnsOneLogicalHit() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureRecord multipoint =
                            new FeatureRecord(
                                    "multipoint",
                                    "Multipoint",
                                    new MultiPointGeometry(
                                            CoordinateSequence.of(
                                                    WRAP.canonicalMinimumX(),
                                                    0.0,
                                                    WRAP.canonicalMinimumX() + 20.0,
                                                    0.0)),
                                    Map.of());
                    RecordingSource source =
                            source("multipoint", List.of(multipoint), FeatureQueryLimits.LEVEL_1);
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(600, 120);
                    view.setViewport(new MapViewport(600, 120, 0.0, 0.0, WRAP.period() / 200.0));
                    view.setLayerBindings(List.of(repeating(binding("multipoint", source))));

                    assertEquals(
                            "multipoint",
                            view.hitTest(200.0, 60.0, 1.0).topmost().orElseThrow().featureId());
                    assertEquals(1, view.hitTest(300.0, 60.0, 600.0).size());
                    view.close();
                });
    }

    @Test
    void wrappedLabelRequestAndTextBudgetsFailBeforeMarkerPaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<FeatureRecord> tooManyLabels = new ArrayList<>();
                    for (int index = 0; index < 1_366; index++) {
                        tooManyLabels.add(point("request-" + index, "Label", 0.0));
                    }
                    assertWrappedLabelFailure(
                            source("label-requests", tooManyLabels, FeatureQueryLimits.LEVEL_1),
                            600,
                            "label-requests");

                    String longLabel = "x".repeat(4_096);
                    List<FeatureRecord> tooMuchText = new ArrayList<>();
                    for (int index = 0; index < 33; index++) {
                        tooMuchText.add(point("text-" + index, longLabel, 0.0));
                    }
                    assertWrappedLabelFailure(
                            source("label-text", tooMuchText, FeatureQueryLimits.LEVEL_1),
                            400,
                            "label-text");
                });
    }

    @Test
    void localAndRepeatingLayersKeepLayerOrderAndLocalContentDoesNotRepeat() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource global =
                            source(
                                    "global-source",
                                    WRAP.canonicalMinimumX(),
                                    FeatureQueryLimits.LEVEL_1);
                    RecordingSource local =
                            source(
                                    "local-source",
                                    WRAP.canonicalMaximumX(),
                                    FeatureQueryLimits.LEVEL_1);
                    MapLayerBinding globalBinding = repeating(binding("global", global));
                    MapLayerBinding localBinding = binding("local", local);
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(200, 100);
                    view.setViewport(new MapViewport(200, 100, WRAP.canonicalMaximumX(), 0.0, 1.0));
                    view.setLayerBindings(List.of(globalBinding, localBinding));

                    assertEquals(
                            "local",
                            view.hitTest(100.0, 50.0, 0.0).topmost().orElseThrow().layerId());
                    view.setLayerBindings(List.of(localBinding, globalBinding));
                    assertEquals(
                            "global",
                            view.hitTest(100.0, 50.0, 0.0).topmost().orElseThrow().layerId());
                    view.close();
                });
    }

    @Test
    void fitRemainsCanonicalAndMatchesNonWrappedCompatibilityBehavior() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView ordinary = TestMapViews.identity();
                    ordinary.setSize(320, 180);
                    ordinary.setLayerBindings(
                            List.of(
                                    binding(
                                            "ordinary",
                                            source(
                                                    "ordinary-source",
                                                    0.0,
                                                    FeatureQueryLimits.LEVEL_1))));
                    ordinary.fitToData(12.0);

                    MapView wrapped = TestMapViews.identity();
                    wrapped.setSize(320, 180);
                    wrapped.setHorizontalWrap(WRAP);
                    wrapped.setLayerBindings(
                            List.of(
                                    repeating(
                                            binding(
                                                    "wrapped",
                                                    source(
                                                            "wrapped-source",
                                                            0.0,
                                                            FeatureQueryLimits.LEVEL_1)))));
                    wrapped.fitToData(12.0);

                    assertEquals(ordinary.viewport(), wrapped.viewport());
                    ordinary.close();
                    wrapped.close();
                });
    }

    @Test
    void aggregateCopyLimitsAndCancellationDiscardTheCompleteWrappedLayer() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureQueryLimits oneRecord =
                            new FeatureQueryLimits(10, 1, 10, 10, 100, 10_000, 10);
                    RecordingSource limited = source("limited", 0.0, oneRecord);
                    MapLayerBinding limitedBinding = repeating(binding("limited", limited));
                    MapView limitedView = TestMapViews.identity();
                    limitedView.setHorizontalWrap(WRAP);
                    limitedView.setSize(600, 120);
                    limitedView.setViewport(
                            new MapViewport(600, 120, 0.0, 0.0, WRAP.period() / 200.0));
                    limitedView.setLayerBindings(List.of(limitedBinding));
                    assertTrue(limitedView.hitTest(300.0, 60.0, 1.0).topmost().isEmpty());
                    assertEquals(
                            "SOURCE_LIMIT_EXCEEDED",
                            limitedView.sourceReports().get("limited").entries().getLast().code());
                    limitedView.close();

                    RecordingSource cancelled =
                            source("cancelled", 0.0, FeatureQueryLimits.LEVEL_1);
                    MapLayerBinding cancelledBinding = repeating(binding("cancelled", cancelled));
                    cancelled.afterFirstAdvance = cancelledBinding::cancelCurrentOperation;
                    MapView cancelledView = TestMapViews.identity();
                    cancelledView.setHorizontalWrap(WRAP);
                    cancelledView.setSize(200, 100);
                    cancelledView.setViewport(new MapViewport(200, 100, 0.0, 0.0, 1.0));
                    cancelledView.setLayerBindings(List.of(cancelledBinding));
                    assertTrue(cancelledView.hitTest(100.0, 50.0, 1.0).topmost().isEmpty());
                    assertEquals(
                            "SOURCE_CANCELLED",
                            cancelledView
                                    .sourceReports()
                                    .get("cancelled")
                                    .entries()
                                    .getLast()
                                    .code());
                    assertTrue(cancelled.closedCursors > 0);
                    cancelledView.close();
                });
    }

    @Test
    void unsupportedProgrammaticViewportPublishesStableWrapDiagnostic() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RecordingSource source = source("precision", 0.0, FeatureQueryLimits.LEVEL_1);
                    MapView view = TestMapViews.identity();
                    view.setHorizontalWrap(WRAP);
                    view.setSize(200, 100);
                    view.setLayerBindings(List.of(repeating(binding("precision", source))));
                    double center = WRAP.period() * (WRAP.maximumAbsoluteCopyIndex() + 1.0);
                    view.setViewport(new MapViewport(200, 100, center, 0.0, 1.0));

                    assertTrue(view.hitTest(100.0, 50.0, 1.0).topmost().isEmpty());
                    assertEquals(
                            "WORLD_WRAP_PRECISION_EXCEEDED",
                            view.sourceReports().get("precision").entries().getLast().code());
                    assertTrue(source.queries.isEmpty());
                    view.close();
                });
    }

    @Test
    void realShapefilePointSourceUsesTheSameWrappedMapViewPath(@TempDir Path temporary)
            throws Exception {
        Path path = temporary.resolve("dateline.shp");
        Files.write(path, pointShapefile(WRAP.canonicalMinimumX(), 0.0));
        FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("shape", "shape"),
                        path,
                        ShapefileOpenOptions.defaults().withCrsOverride(CrsDefinitions.EPSG_3857));
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        MapView view = TestMapViews.identity();
                        view.setHorizontalWrap(WRAP);
                        view.setSize(200, 100);
                        view.setViewport(
                                new MapViewport(200, 100, WRAP.canonicalMaximumX(), 0.0, 1.0));
                        view.setLayerBindings(List.of(repeating(binding("shape-layer", source))));
                        assertEquals(
                                "record:1",
                                view.hitTest(100.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                        view.close();
                    });
        } finally {
            source.close();
        }
        assertTrue(source.isClosed());
    }

    private static MapLayerBinding repeating(MapLayerBinding binding) {
        binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
        return binding;
    }

    private static MapLayerBinding binding(String id, FeatureSource source) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(20, 90, 180), 10.0, 1.0);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 90, 180),
                                new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        return MapLayerBinding.borrowedFeature(
                id, id, source, marker, line, SolidFillSymbol.of(Rgba.rgb(20, 90, 180), 1.0));
    }

    private static RecordingSource source(String id, double x, FeatureQueryLimits queryLimits) {
        return source(id, List.of(point("point", "Repeated", x)), queryLimits);
    }

    private static RecordingSource source(
            String id, List<FeatureRecord> records, FeatureQueryLimits queryLimits) {
        return new RecordingSource(id, records, new FeatureSourceLimits(queryLimits));
    }

    private static FeatureRecord point(String id, String name, double x) {
        return new FeatureRecord(id, name, new PointGeometry(new Coordinate(x, 0.0)), Map.of());
    }

    private static void assertWrappedLabelFailure(
            RecordingSource source, int width, String bindingId) {
        MapView view = TestMapViews.identity();
        view.setHorizontalWrap(WRAP);
        view.setSize(width, 120);
        view.setViewport(new MapViewport(width, 120, 0.0, 0.0, WRAP.period() / 200.0));
        view.setLayerBindings(List.of(repeating(binding(bindingId, source))));

        BufferedImage image = paint(view, width, 120);
        SourceDiagnostic terminal = view.sourceReports().get(bindingId).entries().getLast();
        assertEquals("SOURCE_LIMIT_EXCEEDED", terminal.code());
        assertEquals("worldWrap", terminal.context().get("scope"));
        assertEquals("labels", terminal.context().get("limit"));
        assertEquals(0, countNonWhite(image, 0, 0, width, 120));
        view.close();
    }

    private static DiagnosticReport warning(String sourceId, String code) {
        return new DiagnosticReport(
                List.of(
                        new SourceDiagnostic(
                                code,
                                DiagnosticSeverity.WARNING,
                                sourceId,
                                Optional.of(DiagnosticLocation.empty()),
                                code,
                                Map.of())),
                0);
    }

    private static SourceException failure(
            String sourceId, DiagnosticReport warnings, String code) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        code,
                        Map.of());
        List<SourceDiagnostic> entries = new ArrayList<>(warnings.entries());
        entries.add(terminal);
        return new SourceException(
                new DiagnosticReport(entries, warnings.omittedWarningCount()), terminal);
    }

    private static BufferedImage paint(MapView view, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int countNonWhite(
            BufferedImage image, int minimumX, int minimumY, int maximumX, int maximumY) {
        int result = 0;
        for (int y = Math.max(0, minimumY); y < Math.min(image.getHeight(), maximumY); y++) {
            for (int x = Math.max(0, minimumX); x < Math.min(image.getWidth(), maximumX); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    result++;
                }
            }
        }
        return result;
    }

    private static byte[] pointShapefile(double x, double y) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(128);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(9994);
        bytes.position(24);
        bytes.putInt(64);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1000);
        bytes.putInt(1);
        bytes.putDouble(x);
        bytes.putDouble(y);
        bytes.putDouble(x);
        bytes.putDouble(y);
        bytes.putDouble(0.0);
        bytes.putDouble(0.0);
        bytes.putDouble(0.0);
        bytes.putDouble(0.0);
        bytes.position(100);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(1);
        bytes.putInt(10);
        bytes.order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1);
        bytes.putDouble(x);
        bytes.putDouble(y);
        if (bytes.position() != bytes.capacity()) {
            throw new IOException("test shapefile fixture size mismatch");
        }
        return bytes.array();
    }

    private static final class RecordingSource implements FeatureSource {
        private final FeatureSourceMetadata metadata;
        private final FeatureSourceLimits limits;
        private final List<FeatureRecord> records;
        private final List<FeatureQuery> queries = new ArrayList<>();
        private Runnable afterFirstAdvance = () -> {};
        private FeatureRecord secondQueryRecord;
        private DiagnosticReport firstQueryDiagnostics = DiagnosticReport.empty();
        private DiagnosticReport secondQueryDiagnostics = DiagnosticReport.empty();
        private SourceException secondQueryFailure;
        private FeatureCursor liveCursor;
        private int closedCursors;
        private boolean closed;

        private RecordingSource(
                String id, List<FeatureRecord> records, FeatureSourceLimits limits) {
            this.records = List.copyOf(records);
            this.limits = limits;
            metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity(id, id),
                            Optional.of(
                                    new Envelope(
                                            -WebMercatorProjection.WORLD_LIMIT,
                                            -1.0,
                                            WebMercatorProjection.WORLD_LIMIT,
                                            1.0)),
                            OptionalLong.of(records.size()),
                            Optional.empty(),
                            Optional.of(
                                    CrsMetadata.recognized(
                                            CrsDefinitions.EPSG_3857,
                                            Optional.empty(),
                                            Optional.empty())));
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
            if (closed || liveCursor != null) {
                throw new IllegalStateException("source is closed or already has a cursor");
            }
            queries.add(query);
            int queryOrdinal = queries.size();
            List<FeatureRecord> cursorRecords =
                    queryOrdinal == 2 && secondQueryRecord != null
                            ? List.of(secondQueryRecord)
                            : records;
            FeatureCursor cursor =
                    new FeatureCursor() {
                        private int index;
                        private boolean cursorClosed;

                        @Override
                        public boolean advance() {
                            if (queryOrdinal == 2 && secondQueryFailure != null && index == 1) {
                                throw secondQueryFailure;
                            }
                            if (cursorClosed || index >= cursorRecords.size()) {
                                return false;
                            }
                            if (index++ == 0) {
                                afterFirstAdvance.run();
                            }
                            return true;
                        }

                        @Override
                        public FeatureRecord current() {
                            if (index == 0 || cursorClosed) {
                                throw new IllegalStateException("no current record");
                            }
                            return cursorRecords.get(index - 1);
                        }

                        @Override
                        public DiagnosticReport diagnostics() {
                            return queryOrdinal == 1
                                    ? firstQueryDiagnostics
                                    : secondQueryDiagnostics;
                        }

                        @Override
                        public boolean isClosed() {
                            return cursorClosed;
                        }

                        @Override
                        public void close() {
                            if (!cursorClosed) {
                                cursorClosed = true;
                                liveCursor = null;
                                closedCursors++;
                            }
                        }
                    };
            liveCursor = cursor;
            return cursor;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            if (liveCursor != null) {
                liveCursor.close();
            }
        }
    }
}
