package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureOverlaySymbols;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewCompleteRolePortrayalTest {
    private static final Symbol MARKER_LOW =
            BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(210, 40, 40), 18, 1);
    private static final Symbol MARKER_HIGH =
            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(30, 60, 210), 24, 1);
    private static final Symbol LINE_LOW = line(Rgba.rgb(30, 170, 60), 6);
    private static final Symbol LINE_HIGH = line(Rgba.rgb(180, 40, 180), 8);
    private static final Symbol FILL_LOW = SolidFillSymbol.of(Rgba.rgb(220, 150, 30), 1);
    private static final Symbol FILL_HIGH = SolidFillSymbol.of(Rgba.rgb(20, 170, 190), 1);
    private static final Rgba OVERLAY_COLOR = Rgba.rgb(0, 0, 0);
    private static final FeatureOverlaySymbols TEST_SELECTION_OVERLAY =
            new FeatureOverlaySymbols(
                    BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, OVERLAY_COLOR, 12, 1),
                    line(OVERLAY_COLOR, 4),
                    SolidFillSymbol.of(OVERLAY_COLOR, 1));

    @Test
    void sourcePaintHitHoverClickAndProjectionUseOneCompleteRolePortrayal() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<FeatureRecord> records =
                            List.of(
                                    record(
                                            "point",
                                            new PointGeometry(new Coordinate(-40, 0)),
                                            Map.of("shared", 15L)),
                                    record(
                                            "line",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(0, -12, 0, 12)),
                                            Map.of("shared", 5L)),
                                    record(
                                            "polygon",
                                            square(30, -8, 45, 8),
                                            Map.of("fillValue", 20L)));
                    MultiRecordSource source = new MultiRecordSource(records);
                    MapView view = TestMapViews.identity();
                    view.setSize(120, 120);
                    view.setViewport(new MapViewport(120, 120, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "portrayed",
                                            "portrayed",
                                            source,
                                            completePortrayal())));

                    BufferedImage image = paint(view);
                    assertEquals(
                            AttributeSelection.only(List.of("shared", "fillValue")),
                            source.lastQuery.attributes());
                    assertColor(Rgba.rgb(30, 60, 210), image.getRGB(20, 60));
                    assertColor(Rgba.rgb(30, 170, 60), image.getRGB(60, 60));
                    assertColor(Rgba.rgb(20, 170, 190), image.getRGB(97, 60));

                    assertInteraction(view, 20, 60, "point");
                    assertInteraction(view, 60, 60, "line");
                    assertInteraction(view, 97, 60, "polygon");
                    view.setSelectionOverlaySymbols(TEST_SELECTION_OVERLAY);
                    assertSelectionOverlay(view, "point", 20, 60);
                    assertSelectionOverlay(view, "line", 60, 60);
                    assertSelectionOverlay(view, "polygon", 97, 60);
                    view.close();
                    source.close();
                });
    }

    @Test
    void editableMultipartFeaturesResolveEveryRoleAndOmitMissingValues() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<FeatureRecord> records =
                            List.of(
                                    record(
                                            "points",
                                            new MultiPointGeometry(
                                                    CoordinateSequence.of(-40, 0, -35, 0)),
                                            Map.of("shared", 15L)),
                                    record(
                                            "lines",
                                            MultiLineStringGeometry.ofParts(
                                                    List.of(
                                                            CoordinateSequence.of(0, -12, 0, 12),
                                                            CoordinateSequence.of(5, -12, 5, 12))),
                                            Map.of("shared", 5L)),
                                    record(
                                            "polygons",
                                            MultiPolygonGeometry.ofPolygons(
                                                    List.of(
                                                            square(30, -8, 40, 8),
                                                            square(45, -8, 50, 8))),
                                            Map.of("fillValue", 20L)),
                                    record(
                                            "omitted",
                                            new MultiPointGeometry(
                                                    CoordinateSequence.of(-40, 20, -35, 20)),
                                            Map.of()));
                    FeatureEditSession edits =
                            FeatureEditSession.open(CrsDefinitions.EPSG_3857, records);
                    MapView view = TestMapViews.identity();
                    view.setSize(120, 120);
                    view.setViewport(new MapViewport(120, 120, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.editableFeature(
                                            "editable", "editable", edits, completePortrayal())));

                    BufferedImage image = paint(view);
                    assertColor(Rgba.rgb(30, 60, 210), image.getRGB(20, 60));
                    assertColor(Rgba.rgb(30, 170, 60), image.getRGB(60, 60));
                    assertColor(Rgba.rgb(20, 170, 190), image.getRGB(95, 60));
                    assertEquals(
                            "points", view.hitTest(20, 60, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "lines", view.hitTest(60, 60, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "polygons",
                            view.hitTest(95, 60, 0).topmost().orElseThrow().featureId());
                    assertTrue(view.hitTest(20, 40, 0).hits().isEmpty());
                    view.close();
                });
    }

    @Test
    void omissionPreservesSnapshotIdentityAndExtentForEveryRole() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<Feature> features =
                            List.of(
                                    feature(
                                            "point",
                                            new PointGeometry(new Coordinate(-40, 0)),
                                            MARKER_LOW),
                                    feature(
                                            "line",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(0, -12, 0, 12)),
                                            LINE_LOW),
                                    feature("polygon", square(30, -8, 45, 8), FILL_LOW));
                    FeaturePortrayal omitted =
                            new FeaturePortrayal(
                                    Optional.of(graduated("shared", MARKER_LOW, MARKER_HIGH)),
                                    Optional.of(graduated("shared", LINE_LOW, LINE_HIGH)),
                                    Optional.of(graduated("fillValue", FILL_LOW, FILL_HIGH)));
                    MapView view = TestMapViews.identity();
                    view.setSize(120, 120);
                    view.setViewport(new MapViewport(120, 120, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer("omitted", "omitted", features),
                                            omitted)));
                    view.setSelection(new FeatureSelection("omitted", "polygon"));
                    view.setSelectionOverlaySymbols(TEST_SELECTION_OVERLAY);

                    BufferedImage image = paint(view);
                    assertTrue(view.hitTest(60, 60, 100).hits().isEmpty());
                    assertEquals(image.getRGB(0, 0), image.getRGB(97, 60));
                    assertEquals("polygon", view.selection().orElseThrow().featureId());
                    view.fitToData(10);
                    assertTrue(view.viewport().worldUnitsPerPixel() < 1);
                    assertEquals("polygon", view.selection().orElseThrow().featureId());
                    view.close();
                });
    }

    private static FeaturePortrayal completePortrayal() {
        return new FeaturePortrayal(
                Optional.of(graduated("shared", MARKER_LOW, MARKER_HIGH)),
                Optional.of(graduated("shared", LINE_LOW, LINE_HIGH)),
                Optional.of(graduated("fillValue", FILL_LOW, FILL_HIGH)));
    }

    private static GraduatedSymbolSelector graduated(String attribute, Symbol low, Symbol high) {
        return new GraduatedSymbolSelector(
                attribute,
                List.of(
                        new GraduatedSymbolStep(BigDecimal.ZERO, low),
                        new GraduatedSymbolStep(BigDecimal.TEN, high)),
                Optional.empty());
    }

    private static SolidLineSymbol line(Rgba color, double width) {
        return SolidLineSymbol.of(
                new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL)), 1);
    }

    private static PolygonGeometry square(double minX, double minY, double maxX, double maxY) {
        return new PolygonGeometry(
                CoordinateSequence.of(
                        minX, minY,
                        maxX, minY,
                        maxX, maxY,
                        minX, maxY,
                        minX, minY));
    }

    private static FeatureRecord record(
            String id,
            io.github.mundanej.map.api.Geometry geometry,
            Map<String, Object> attributes) {
        return new FeatureRecord(id, id, geometry, attributes);
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, Symbol symbol) {
        return new Feature(id, id, geometry, Map.of(), symbol);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertInteraction(MapView view, int x, int y, String featureId) {
        assertEquals(featureId, view.hitTest(x, y, 0).topmost().orElseThrow().featureId());
        dispatch(view, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON);
        assertEquals(featureId, view.hover().orElseThrow().featureId());
        dispatch(view, MouseEvent.MOUSE_CLICKED, x, y, MouseEvent.BUTTON1);
        assertEquals(featureId, view.selection().orElseThrow().featureId());
    }

    private static void assertSelectionOverlay(MapView view, String featureId, int x, int y) {
        view.setSelection(new FeatureSelection("portrayed", featureId));
        assertColor(OVERLAY_COLOR, paint(view).getRGB(x, y));
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
    }

    private static final class MultiRecordSource implements FeatureSource {
        private final List<FeatureRecord> records;
        private final FeatureSourceMetadata metadata;
        private FeatureQuery lastQuery;
        private boolean closed;

        private MultiRecordSource(List<FeatureRecord> records) {
            this.records = List.copyOf(records);
            metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity("complete-role", "complete-role"),
                            Optional.of(new Envelope(-40, -12, 45, 12)),
                            OptionalLong.of(records.size()),
                            Optional.of(
                                    new AttributeSchema(
                                            List.of(
                                                    new AttributeField(
                                                            "shared", AttributeType.INTEGER, true),
                                                    new AttributeField(
                                                            "fillValue",
                                                            AttributeType.INTEGER,
                                                            true)))),
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
            lastQuery = query;
            return new FeatureCursor() {
                private int index = -1;
                private boolean cursorClosed;

                @Override
                public boolean advance() {
                    index++;
                    return index < records.size();
                }

                @Override
                public FeatureRecord current() {
                    if (index < 0 || index >= records.size()) {
                        throw new IllegalStateException("no current record");
                    }
                    return records.get(index);
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
