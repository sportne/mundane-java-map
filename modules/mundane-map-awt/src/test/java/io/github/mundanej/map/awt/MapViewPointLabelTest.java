package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeatureOverlaySymbols;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.TextAttribute;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewPointLabelTest {
    private static final MarkerSymbol RED = marker(Rgba.rgb(210, 30, 30), 10);
    private static final MarkerSymbol BLUE = marker(Rgba.rgb(30, 60, 210), 20);

    @Test
    void compatibilitySnapshotUsesGlobalNameLabelAndLabelBoxIsAnnotationOnly() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.snapshot(
                                            new InMemoryLayer(
                                                    "layer",
                                                    "layer",
                                                    List.of(feature("point", "Label", 0, RED))))));

                    BufferedImage image = paint(view);
                    assertTrue(hasDarkInk(image, 58, 25, 95, 50));
                    assertTrue(view.hitTest(70, 38, 0).hits().isEmpty());
                    assertEquals(
                            "point", view.hitTest(50, 50, 0).topmost().orElseThrow().featureId());
                    view.close();
                });
    }

    @Test
    void explicitNoLabelPortrayalOmitsTextAndAllLabelsPaintAfterAllGeometry() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    PointLabelProfile east = profile(FeatureName.INSTANCE, PointLabelPosition.E);
                    FeaturePortrayal labeled =
                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                    .withPointLabel(east);
                    FeaturePortrayal noLabel =
                            FeaturePortrayal.markers(new FixedSymbolSelector(BLUE));
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "labels",
                                                    "labels",
                                                    List.of(feature("label", "M", -15, RED))),
                                            labeled),
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "cover",
                                                    "cover",
                                                    List.of(feature("cover", "", -1, BLUE))),
                                            noLabel)));

                    BufferedImage image = paint(view);
                    assertTrue(hasDarkInk(image, 43, 41, 56, 55));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "plain",
                                                    "plain",
                                                    List.of(feature("plain", "M", 0, RED))),
                                            noLabel)));
                    BufferedImage plain = paint(view);
                    assertFalse(hasDarkInk(plain, 58, 25, 80, 50));
                    view.close();
                });
    }

    @Test
    void sourcePaintProjectsVisibleLabelAttributeButHitAndExcludedResolutionDoNot()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CapturingSource source =
                            new CapturingSource(
                                    new FeatureRecord(
                                            "source",
                                            "name",
                                            new PointGeometry(new Coordinate(-20, 0)),
                                            Map.of("label", "Attribute")),
                                    Optional.of(schema("label")));
                    PointLabelProfile visible =
                            new PointLabelProfile(
                                    new TextAttribute("label"),
                                    new LabelTextStyle(
                                            Rgba.rgb(32, 32, 32), LabelWeight.NORMAL, 12),
                                    List.of(PointLabelPosition.NE),
                                    4,
                                    0,
                                    0,
                                    1,
                                    0,
                                    new ResolutionRange(0.5, 2));
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "source",
                                            "source",
                                            source,
                                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                                    .withPointLabel(visible))));

                    BufferedImage image = paint(view);
                    assertEquals(
                            AttributeSelection.only(List.of("label")),
                            source.lastQuery.attributes());
                    assertTrue(hasDarkInk(image, 38, 25, 100, 50));
                    view.hitTest(30, 50, 0);
                    assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());

                    view.setViewport(new MapViewport(100, 100, 0, 0, 3));
                    paint(view);
                    assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());
                    view.close();
                    source.close();
                });
    }

    @Test
    void knownSchemaRequiresConfiguredLabelAttributeBeforeAttachment() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CapturingSource source =
                            new CapturingSource(
                                    new FeatureRecord(
                                            "source",
                                            "name",
                                            new PointGeometry(new Coordinate(0, 0)),
                                            Map.of()),
                                    Optional.of(schema("other")));
                    FeaturePortrayal portrayal =
                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                    .withPointLabel(
                                            profile(
                                                    new TextAttribute("label"),
                                                    PointLabelPosition.NE));
                    MapView view = view();

                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    MapLayerBinding.borrowedFeature(
                                                            "source", "source", source,
                                                            portrayal))));
                    assertTrue(view.layerBindings().isEmpty());
                    view.close();
                    source.close();
                });
    }

    @Test
    void compatibilitySourceAndEditableFactoriesPaintNameLabels() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CapturingSource source =
                            new CapturingSource(
                                    new FeatureRecord(
                                            "source",
                                            "S",
                                            new PointGeometry(new Coordinate(-20, 0)),
                                            Map.of()),
                                    Optional.empty());
                    FeatureEditSession edits =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(
                                            new FeatureRecord(
                                                    "editable",
                                                    "E",
                                                    new PointGeometry(new Coordinate(20, 0)),
                                                    Map.of())));
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "source",
                                            "source",
                                            source,
                                            RED,
                                            line(Rgba.rgb(20, 20, 20)),
                                            SolidFillSymbol.of(Rgba.rgb(20, 20, 20), 1)),
                                    MapLayerBinding.editableFeature(
                                            "editable",
                                            "editable",
                                            edits,
                                            RED,
                                            line(Rgba.rgb(20, 20, 20)),
                                            SolidFillSymbol.of(Rgba.rgb(20, 20, 20), 1))));

                    BufferedImage image = paint(view);
                    assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());
                    assertTrue(hasDarkInk(image, 38, 25, 68, 50));
                    assertTrue(hasDarkInk(image, 78, 25, 100, 50));
                    view.close();
                    source.close();
                });
    }

    @Test
    void collisionAdmissionIsGlobalAcrossSnapshotSourceAndEditableBindings() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Rgba snapshotColor = Rgba.rgb(35, 70, 190);
                    Rgba sourceColor = Rgba.rgb(25, 155, 65);
                    Rgba editableColor = Rgba.rgb(195, 35, 45);
                    Feature shared = feature("snapshot", "WIN", -20, RED);
                    CapturingSource source =
                            new CapturingSource(
                                    new FeatureRecord(
                                            "source",
                                            "WIN",
                                            new PointGeometry(new Coordinate(-20, 0)),
                                            Map.of()),
                                    Optional.empty());
                    FeatureEditSession edits =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(
                                            new FeatureRecord(
                                                    "editable",
                                                    "WIN",
                                                    new PointGeometry(new Coordinate(-20, 0)),
                                                    Map.of())));
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "snapshot", "snapshot", List.of(shared)),
                                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                                    .withPointLabel(profile(snapshotColor, 5))),
                                    MapLayerBinding.borrowedFeature(
                                            "source",
                                            "source",
                                            source,
                                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                                    .withPointLabel(profile(sourceColor, 10))),
                                    MapLayerBinding.editableFeature(
                                            "editable",
                                            "editable",
                                            edits,
                                            FeaturePortrayal.markers(new FixedSymbolSelector(RED))
                                                    .withPointLabel(profile(editableColor, 0)))));

                    BufferedImage image = paint(view);
                    assertTrue(hasInkNear(image, sourceColor, 38, 25, 70, 50));
                    assertFalse(hasInkNear(image, snapshotColor, 38, 25, 70, 50));
                    assertFalse(hasInkNear(image, editableColor, 38, 25, 70, 50));
                    view.close();
                    source.close();
                });
    }

    @Test
    void recordTextFailureWinsOverBatchBudgetBeforeAnyGeometryPaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<Feature> features =
                            List.of(
                                    feature("invalid", "x".repeat(262_145), -20, RED),
                                    feature("later", "Later", 20, BLUE));
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.snapshot(
                                            new InMemoryLayer("limits", "limits", features))));
                    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    LabelPlacementException failure;
                    try {
                        failure =
                                assertThrows(
                                        LabelPlacementException.class, () -> view.paint(graphics));
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals("LABEL_TEXT_LIMIT_EXCEEDED", failure.problem().code());
                    assertEquals("0", failure.problem().context().get("layerIndex"));
                    assertEquals("0", failure.problem().context().get("featureIndex"));
                    assertEquals(0, image.getRGB(70, 50));
                    view.close();
                });
    }

    @Test
    void requestAndTotalTextBudgetsUseStableBatchProblems() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView requestView = view();
                    requestView.setLayerBindings(
                            List.of(
                                    MapLayerBinding.snapshot(
                                            new InMemoryLayer(
                                                    "request-limit",
                                                    "request-limit",
                                                    java.util.stream.IntStream.range(0, 4_097)
                                                            .mapToObj(
                                                                    index ->
                                                                            feature(
                                                                                    "f" + index,
                                                                                    "x",
                                                                                    0,
                                                                                    RED))
                                                            .toList()))));
                    LabelPlacementException request = paintFailure(requestView);
                    assertEquals("LABEL_REQUEST_LIMIT_EXCEEDED", request.problem().code());
                    assertEquals("4096", request.problem().context().get("limit"));
                    assertEquals("4097", request.problem().context().get("attempted"));
                    requestView.close();

                    MapView budgetView = view();
                    budgetView.setLayerBindings(
                            List.of(
                                    MapLayerBinding.snapshot(
                                            new InMemoryLayer(
                                                    "text-budget",
                                                    "text-budget",
                                                    java.util.stream.IntStream.range(0, 1_025)
                                                            .mapToObj(
                                                                    index ->
                                                                            feature(
                                                                                    "f" + index,
                                                                                    "x".repeat(256),
                                                                                    0,
                                                                                    RED))
                                                            .toList()))));
                    LabelPlacementException budget = paintFailure(budgetView);
                    assertEquals("LABEL_TEXT_BUDGET_EXCEEDED", budget.problem().code());
                    assertEquals("262144", budget.problem().context().get("limit"));
                    assertEquals("262400", budget.problem().context().get("attempted"));
                    budgetView.close();
                });
    }

    @Test
    void labelsPaintBelowHoverSelectionAndMeasurementOverlays() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    PointLabelProfile overlapping =
                            new PointLabelProfile(
                                    FeatureName.INSTANCE,
                                    new LabelTextStyle(Rgba.rgb(220, 20, 20), LabelWeight.BOLD, 12),
                                    List.of(PointLabelPosition.E),
                                    0,
                                    -10,
                                    0,
                                    0,
                                    0,
                                    ResolutionRange.ALL);
                    MapView view = view();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "layer",
                                                    "layer",
                                                    List.of(feature("point", "M", 0, BLUE))),
                                            FeaturePortrayal.markers(new FixedSymbolSelector(BLUE))
                                                    .withPointLabel(overlapping))));
                    int[] labelPixel = findRedInk(paint(view), 40, 40, 60, 60);

                    view.setHoverOverlaySymbols(overlay(Rgba.rgb(20, 180, 40), 20));
                    move(view, 50, 50);
                    assertColor(
                            Rgba.rgb(20, 180, 40),
                            paint(view).getRGB(labelPixel[0], labelPixel[1]));
                    view.setSelectionOverlaySymbols(overlay(Rgba.rgb(30, 60, 210), 20));
                    view.setSelection(new FeatureSelection("layer", "point"));
                    assertColor(
                            Rgba.rgb(30, 60, 210),
                            paint(view).getRGB(labelPixel[0], labelPixel[1]));

                    view.setActiveTool(
                            new MeasurementTool(
                                    DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857)));
                    click(view, 35, 50);
                    click(view, 65, 50);
                    assertColor(new Rgba(190, 35, 55, 255), paint(view).getRGB(50, 50));
                    view.close();
                });
    }

    private static MapView view() {
        MapView view = TestMapViews.identity();
        view.setSize(100, 100);
        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
        return view;
    }

    private static Feature feature(
            String id, String name, double x, io.github.mundanej.map.api.Symbol symbol) {
        return new Feature(id, name, new PointGeometry(new Coordinate(x, 0)), Map.of(), symbol);
    }

    private static MarkerSymbol marker(Rgba color, double size) {
        return BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, color, size, 1);
    }

    private static SolidLineSymbol line(Rgba color) {
        return SolidLineSymbol.of(
                new SymbolStroke(color, new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)), 1);
    }

    private static FeatureOverlaySymbols overlay(Rgba color, double markerSize) {
        return new FeatureOverlaySymbols(
                marker(color, markerSize), line(color), SolidFillSymbol.of(color, 1));
    }

    private static PointLabelProfile profile(
            io.github.mundanej.map.api.LabelTextSource source, PointLabelPosition position) {
        return new PointLabelProfile(
                source,
                new LabelTextStyle(Rgba.rgb(32, 32, 32), LabelWeight.NORMAL, 12),
                List.of(position),
                4,
                0,
                0,
                1,
                0,
                ResolutionRange.ALL);
    }

    private static PointLabelProfile profile(Rgba color, int priority) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                new LabelTextStyle(color, LabelWeight.BOLD, 12),
                List.of(PointLabelPosition.NE),
                4,
                0,
                0,
                1,
                priority,
                ResolutionRange.ALL);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static LabelPlacementException paintFailure(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return assertThrows(LabelPlacementException.class, () -> view.paint(graphics));
        } finally {
            graphics.dispose();
        }
    }

    private static int[] findRedInk(BufferedImage image, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 100) {
                    return new int[] {x, y};
                }
            }
        }
        throw new AssertionError("expected red label ink");
    }

    private static void move(MapView view, int x, int y) {
        dispatch(view, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON);
    }

    private static void click(MapView view, int x, int y) {
        dispatch(view, MouseEvent.MOUSE_CLICKED, x, y, MouseEvent.BUTTON1);
    }

    private static void dispatch(MapView view, int type, int x, int y, int button) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        type,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        type == MouseEvent.MOUSE_CLICKED ? 1 : 0,
                        false,
                        button));
    }

    private static void assertColor(Rgba expected, int actualArgb) {
        Color actual = new Color(actualArgb, true);
        assertEquals(expected.red(), actual.getRed());
        assertEquals(expected.green(), actual.getGreen());
        assertEquals(expected.blue(), actual.getBlue());
        assertEquals(expected.alpha(), actual.getAlpha());
    }

    private static boolean hasDarkInk(BufferedImage image, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() > 0
                        && color.getRed() < 100
                        && color.getGreen() < 100
                        && color.getBlue() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInkNear(
            BufferedImage image, Rgba expected, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                int distance =
                        Math.max(
                                Math.max(
                                        Math.abs(expected.red() - actual.getRed()),
                                        Math.abs(expected.green() - actual.getGreen())),
                                Math.abs(expected.blue() - actual.getBlue()));
                if (distance <= 48) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AttributeSchema schema(String field) {
        return new AttributeSchema(List.of(new AttributeField(field, AttributeType.TEXT, true)));
    }

    private static final class CapturingSource implements FeatureSource {
        private final FeatureRecord record;
        private final FeatureSourceMetadata metadata;
        private FeatureQuery lastQuery;
        private boolean closed;

        private CapturingSource(FeatureRecord record, Optional<AttributeSchema> schema) {
            this.record = record;
            metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity("label-source", "label-source"),
                            Optional.of(record.geometry().envelope()),
                            OptionalLong.of(1),
                            schema,
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
            return FeatureSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (closed) {
                throw new IllegalStateException("source is closed");
            }
            lastQuery = query;
            return new FeatureCursor() {
                private boolean advanced;
                private boolean cursorClosed;

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
        }
    }
}
