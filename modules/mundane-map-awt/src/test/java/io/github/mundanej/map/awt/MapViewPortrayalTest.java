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
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewPortrayalTest {
    private static final MarkerSymbol RED =
            BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(210, 30, 30), 10, 1);
    private static final MarkerSymbol BLUE =
            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(30, 60, 210), 30, 1);
    private static final MarkerSymbol FALLBACK =
            BuiltInMarkers.filledScreen(BuiltInMarker.TRIANGLE, Rgba.rgb(30, 170, 60), 16, 1);
    private static final MarkerSymbol FEATURE_OWNED =
            BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(20, 20, 20), 40, 1);

    @Test
    void categoricalMarkersAgreeAcrossSnapshotSourceEditableAndInteraction() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeaturePortrayal portrayal = portrayal(Optional.of(FALLBACK));
                    Feature snapshotFeature = feature("snapshot", -35, Map.of("kind", "red"));
                    CapturingSource source =
                            new CapturingSource(
                                    "source",
                                    record("source", 0, Map.of("kind", "blue")),
                                    Optional.of(schema("kind")));
                    FeatureEditSession edits =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(record("editable", 20, Map.of())));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "snapshot-layer",
                                                    "snapshot-layer",
                                                    List.of(snapshotFeature)),
                                            portrayal),
                                    MapLayerBinding.borrowedFeature(
                                            "source-layer", "source-layer", source, portrayal),
                                    MapLayerBinding.editableFeature(
                                            "edit-layer", "edit-layer", edits, portrayal)));

                    paint(view);
                    assertEquals(
                            AttributeSelection.only(List.of("kind")),
                            source.lastQuery.attributes());
                    assertEquals(
                            "snapshot",
                            view.hitTest(15, 50, 0).topmost().orElseThrow().featureId());
                    assertFalse(view.hitTest(30, 50, 0).topmost().isPresent());
                    assertEquals(
                            "source", view.hitTest(62, 50, 0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "editable",
                            view.hitTest(70, 50, 0).topmost().orElseThrow().featureId());

                    move(view, 50, 50);
                    assertEquals("source", view.hover().orElseThrow().featureId());
                    click(view, 50, 50);
                    assertEquals("source", view.selection().orElseThrow().featureId());
                    assertEquals("source-layer", view.selection().orElseThrow().layerId());
                    view.close();
                    source.close();
                });
    }

    @Test
    void omissionKnownSchemaAndRecursiveRendererPreflightAreTransactional() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature omitted = feature("omitted", 0, Map.of("kind", "unmatched"));
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer(
                                                    "omitted", "omitted", List.of(omitted)),
                                            portrayal(Optional.empty()))));
                    view.setSelection(new FeatureSelection("omitted", "omitted"));
                    paint(view);
                    assertTrue(view.hitTest(50, 50, 20).hits().isEmpty());
                    assertEquals("omitted", view.selection().orElseThrow().featureId());

                    CapturingSource missingSchema =
                            new CapturingSource(
                                    "missing",
                                    record("missing", 0, Map.of()),
                                    Optional.of(schema("other")));
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    MapLayerBinding.borrowedFeature(
                                                            "missing",
                                                            "missing",
                                                            missingSchema,
                                                            portrayal(Optional.empty())))));
                    assertEquals(1, view.layerBindings().size());

                    CapturingSource dynamic =
                            new CapturingSource(
                                    "dynamic",
                                    record("dynamic", 0, Map.of("kind", "red")),
                                    Optional.empty());
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "dynamic",
                                            "dynamic",
                                            dynamic,
                                            portrayal(Optional.empty()))));
                    paint(view);
                    assertEquals(
                            "dynamic", view.hitTest(50, 50, 0).topmost().orElseThrow().featureId());

                    CapturingSource fixedSource =
                            new CapturingSource(
                                    "fixed", record("fixed", 0, Map.of()), Optional.empty());
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "fixed",
                                            "fixed",
                                            fixedSource,
                                            FeaturePortrayal.markers(
                                                    new FixedSymbolSelector(RED)))));
                    paint(view);
                    assertEquals(AttributeSelection.NONE, fixedSource.lastQuery.attributes());

                    FeaturePortrayal unregisteredChild =
                            FeaturePortrayal.markers(
                                    new CategoricalSymbolSelector(
                                            "kind",
                                            List.of(
                                                    new CategoricalSymbolRule(
                                                            ThematicValue.text("red"), RED)),
                                            Optional.of(
                                                    CompositeSymbol.of(
                                                            List.of(RED, new UnregisteredMarker()),
                                                            1))));
                    assertThrows(
                            SymbolException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    MapLayerBinding.portrayedSnapshot(
                                                            new InMemoryLayer(
                                                                    "invalid",
                                                                    "invalid",
                                                                    List.of(omitted)),
                                                            unregisteredChild))));
                    assertEquals("fixed", view.layerBindings().getFirst().id());

                    FeaturePortrayal rejectedValue =
                            FeaturePortrayal.markers(
                                    new FixedSymbolSelector(new RendererRejectedMarker()));
                    assertThrows(
                            SymbolException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    MapLayerBinding.portrayedSnapshot(
                                                            new InMemoryLayer(
                                                                    "rejected",
                                                                    "rejected",
                                                                    List.of(omitted)),
                                                            rejectedValue))));
                    assertEquals("fixed", view.layerBindings().getFirst().id());
                    view.close();
                    missingSchema.close();
                    dynamic.close();
                    fixedSource.close();
                });
    }

    private static FeaturePortrayal portrayal(Optional<? extends MarkerSymbol> fallback) {
        return FeaturePortrayal.markers(
                new CategoricalSymbolSelector(
                        "kind",
                        List.of(
                                new CategoricalSymbolRule(ThematicValue.text("red"), RED),
                                new CategoricalSymbolRule(ThematicValue.text("blue"), BLUE)),
                        fallback));
    }

    private static Feature feature(String id, double x, Map<String, Object> attributes) {
        return new Feature(
                id, id, new PointGeometry(new Coordinate(x, 0)), attributes, FEATURE_OWNED);
    }

    private static FeatureRecord record(String id, double x, Map<String, Object> attributes) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, 0)), attributes);
    }

    private static AttributeSchema schema(String field) {
        return new AttributeSchema(List.of(new AttributeField(field, AttributeType.TEXT, true)));
    }

    private static void paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
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

    private record UnregisteredMarker() implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("test.unregistered-marker");
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private record RendererRejectedMarker() implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return VectorMarkerSymbol.RENDERER_KEY;
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private static final class CapturingSource implements FeatureSource {
        private final FeatureSourceMetadata metadata;
        private final FeatureRecord record;
        private FeatureQuery lastQuery;
        private boolean closed;

        private CapturingSource(String id, FeatureRecord record, Optional<AttributeSchema> schema) {
            this.record = record;
            metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity(id, id),
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
            lastQuery = query;
            return new FeatureCursor() {
                private int state;
                private boolean cursorClosed;

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
