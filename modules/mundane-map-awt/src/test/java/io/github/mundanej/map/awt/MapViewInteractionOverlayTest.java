package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapHoverEvent;
import io.github.mundanej.map.api.MapSelectionEvent;
import io.github.mundanej.map.api.MapSelectionListener;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewInteractionOverlayTest {
    private static final int SIZE = 100;

    @Test
    void hoverTransitionsUseCompleteKeysAndSilenceUnchangedMoves() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(
                                    List.of(
                                            layer("left", feature("shared", -20.0, 0.0, 14.0)),
                                            layer("right", feature("shared", 20.0, 0.0, 14.0))));
                    List<MapHoverEvent> events = new ArrayList<>();
                    view.addMapHoverListener(events::add);

                    move(view, 30, 50, 0);
                    move(view, 30, 50, 0);
                    move(view, 70, 50, 0);
                    move(view, 10, 10, 0);

                    assertEquals(3, events.size());
                    assertEquals(
                            Optional.of(new MapHit("left", "shared")), events.get(0).current());
                    assertEquals(
                            Optional.of(new MapHit("left", "shared")), events.get(1).previous());
                    assertEquals(
                            Optional.of(new MapHit("right", "shared")), events.get(1).current());
                    assertTrue(events.get(2).current().isEmpty());
                    assertTrue(view.hover().isEmpty());
                });
    }

    @Test
    void exitConsumedMoveLayerReplacementAndDisableClearHoverOnce() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    List<MapHoverEvent> events = new ArrayList<>();
                    view.addMapHoverListener(events::add);

                    move(view, 50, 50, 0);
                    exit(view, 50, 50);
                    exit(view, 50, 50);
                    assertEquals(2, events.size());

                    move(view, 50, 50, 0);
                    view.setActiveTool((event, context) -> MapToolResult.CONSUME);
                    move(view, 50, 50, 0);
                    assertTrue(view.hover().isEmpty());

                    view.clearActiveTool();
                    move(view, 50, 50, 0);
                    view.setLayers(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    assertTrue(view.hover().isEmpty());

                    move(view, 50, 50, 0);
                    view.setEnabled(false);
                    view.setEnabled(false);
                    assertTrue(view.hover().isEmpty());
                    assertEquals(8, events.size());
                });
    }

    @Test
    void selectionListenersObserveProgrammaticClickClearAndRemovalChanges() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    List<MapSelectionEvent> events = new ArrayList<>();
                    List<Optional<FeatureSelection>> pointerObservations = new ArrayList<>();
                    view.addMapSelectionListener(events::add);
                    view.addMapPointerListener(event -> pointerObservations.add(view.selection()));
                    FeatureSelection selected = new FeatureSelection("layer", "feature");

                    view.setSelection(selected);
                    view.setSelection(selected);
                    view.clearSelection();
                    click(view, 50, 50);
                    view.setLayers(List.of());

                    assertEquals(4, events.size());
                    assertEquals(Optional.of(selected), events.get(0).current());
                    assertTrue(events.get(1).current().isEmpty());
                    assertEquals(Optional.of(selected), events.get(2).current());
                    assertTrue(events.get(3).current().isEmpty());
                    assertEquals(Optional.of(selected), pointerObservations.getFirst());
                });
    }

    @Test
    void listenerDeliveryIsMutationSafeFifoReentrantAndAggregatesFailures() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    FeatureSelection selected = new FeatureSelection("layer", "feature");
                    List<String> calls = new ArrayList<>();
                    MapSelectionListener added = event -> calls.add("added:" + event.current());
                    MapSelectionListener reentrant =
                            event -> {
                                calls.add("reentrant:" + event.current());
                                view.addMapSelectionListener(added);
                                if (event.current().isPresent()) {
                                    view.clearSelection();
                                }
                            };
                    MapSelectionListener observer =
                            event -> calls.add("observer:" + event.current());
                    view.addMapSelectionListener(reentrant);
                    view.addMapSelectionListener(observer);

                    view.setSelection(selected);

                    assertEquals(5, calls.size());
                    assertTrue(calls.get(0).startsWith("reentrant:Optional["));
                    assertTrue(calls.get(1).startsWith("observer:Optional["));
                    assertEquals("reentrant:Optional.empty", calls.get(2));
                    assertEquals("observer:Optional.empty", calls.get(3));
                    assertEquals("added:Optional.empty", calls.get(4));

                    MapView failing =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    RuntimeException first = new RuntimeException("first");
                    RuntimeException second = new RuntimeException("second");
                    failing.addMapSelectionListener(
                            event -> {
                                throw first;
                            });
                    failing.addMapSelectionListener(
                            event -> {
                                throw second;
                            });
                    RuntimeException thrown =
                            assertThrows(
                                    RuntimeException.class, () -> failing.setSelection(selected));
                    assertSame(first, thrown);
                    assertEquals(List.of(second), List.of(thrown.getSuppressed()));
                    assertEquals(Optional.of(selected), failing.selection());
                });
    }

    @Test
    void duplicateRemovalUsesIdentityAndErrorAbortsRemainingNotificationDelivery()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    FeatureSelection selected = new FeatureSelection("layer", "feature");
                    int[] duplicateCalls = {0};
                    MapSelectionListener duplicate = event -> duplicateCalls[0]++;
                    view.addMapSelectionListener(duplicate);
                    view.addMapSelectionListener(duplicate);
                    view.removeMapSelectionListener(duplicate);

                    view.setSelection(selected);
                    assertEquals(1, duplicateCalls[0]);

                    view.removeMapSelectionListener(duplicate);
                    AssertionError fatal = new AssertionError("fatal");
                    int[] laterCalls = {0};
                    view.addMapSelectionListener(
                            event -> {
                                throw fatal;
                            });
                    view.addMapSelectionListener(event -> laterCalls[0]++);

                    AssertionError thrown =
                            assertThrows(AssertionError.class, view::clearSelection);
                    assertSame(fatal, thrown);
                    assertEquals(0, laterCalls[0]);
                    assertTrue(view.selection().isEmpty());
                });
    }

    @Test
    void sourceThenHoverThenSelectionOverlaysPreserveSourceCenterAndOuterPixels() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature source = feature("feature", 0.0, 0.0, 10.0);
                    MapView view = view(List.of(layer("layer", source)));
                    BufferedImage sourceOnly = paint(view);
                    VectorMarkerSymbol original = (VectorMarkerSymbol) source.symbol();

                    move(view, 50, 50, 0);
                    view.setSelection(new FeatureSelection("layer", "feature"));
                    BufferedImage overlaid = paint(view);

                    assertEquals(sourceOnly.getRGB(50, 50), overlaid.getRGB(50, 50));
                    assertEquals(sourceOnly.getRGB(15, 15), overlaid.getRGB(15, 15));
                    assertColorNear(new Rgba(0, 102, 204, 255), overlaid.getRGB(57, 50), 45);
                    assertColorNear(new Rgba(255, 170, 0, 176), overlaid.getRGB(59, 50), 80);
                    assertSame(original, source.symbol());
                });
    }

    @Test
    void emptyLogicalSourcePaintSuppressesSelectionAndHoverOverlays() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    VectorMarkerSymbol transparent =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.SQUARE, Rgba.rgb(20, 80, 140), 20.0, 0.0);
                    Feature feature =
                            new Feature(
                                    "empty",
                                    "label",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    Map.of(),
                                    transparent);
                    MapView view = view(List.of(layer("layer", feature)));
                    BufferedImage before = paint(view);
                    view.setSelection(new FeatureSelection("layer", "empty"));
                    move(view, 50, 50, 0);
                    BufferedImage after = paint(view);

                    assertEquals(imageHash(before), imageHash(after));
                    assertTrue(view.hover().isEmpty());
                    assertEquals(
                            Optional.of(new FeatureSelection("layer", "empty")), view.selection());
                });
    }

    @Test
    void realInteractionAndOverlayChangesInvalidateTheFullComponent() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    RepaintManager previous = RepaintManager.currentManager(view);
                    RecordingRepaintManager recording = new RecordingRepaintManager();
                    RepaintManager.setCurrentManager(recording);
                    try {
                        view.setSelection(new FeatureSelection("layer", "feature"));
                        recording.assertSingleFull(view);

                        recording.clear();
                        move(view, 50, 50, 0);
                        recording.assertSingleFull(view);

                        recording.clear();
                        view.setHoverOverlaySymbols(
                                io.github.mundanej.map.api.FeatureOverlaySymbols
                                        .defaultSelection());
                        recording.assertSingleFull(view);

                        recording.clear();
                        view.setLayers(List.of());
                        recording.assertSingleFull(view);

                        recording.clear();
                        view.setLayers(List.of());
                        recording.assertSingleFull(view);
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                });
    }

    @Test
    void sameIdentityPresenceChangeSchedulesOneFullFollowUpFromNarrowPaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MutableLayer layer =
                            new MutableLayer("layer", List.of(feature("feature", 0.0, 0.0, 20.0)));
                    MapView view = viewLayers(List.of(layer));
                    view.setSelection(new FeatureSelection("layer", "feature"));
                    paint(view);
                    RepaintManager previous = RepaintManager.currentManager(view);
                    RecordingRepaintManager recording = new RecordingRepaintManager();
                    RepaintManager.setCurrentManager(recording);
                    try {
                        VectorMarkerSymbol transparent =
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.SQUARE, Rgba.rgb(20, 80, 140), 20.0, 0.0);
                        layer.features =
                                List.of(
                                        new Feature(
                                                "feature",
                                                "",
                                                new PointGeometry(new Coordinate(0.0, 0.0)),
                                                Map.of(),
                                                transparent));
                        paintNarrow(view);
                        recording.assertSingleFull(view);
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                });
    }

    @Test
    void removalAndSeparatePresenceChangeCoalesceToOnePaintFollowUp() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MutableLayer layer =
                            new MutableLayer(
                                    "layer",
                                    List.of(
                                            feature("hovered", 0.0, 0.0, 20.0),
                                            feature("selected", 20.0, 0.0, 20.0)));
                    MapView view = viewLayers(List.of(layer));
                    view.setSelection(new FeatureSelection("layer", "selected"));
                    move(view, 50, 50, 0);
                    paint(view);
                    VectorMarkerSymbol transparent =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.SQUARE, Rgba.rgb(20, 80, 140), 20.0, 0.0);
                    layer.features =
                            List.of(
                                    new Feature(
                                            "selected",
                                            "",
                                            new PointGeometry(new Coordinate(20.0, 0.0)),
                                            Map.of(),
                                            transparent));
                    RepaintManager previous = RepaintManager.currentManager(view);
                    RecordingRepaintManager recording = new RecordingRepaintManager();
                    RepaintManager.setCurrentManager(recording);
                    try {
                        paintNarrow(view);
                        recording.assertSingleFull(view);
                        assertTrue(view.hover().isEmpty());
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                });
    }

    @Test
    void clickCommitsSelectionThenHoverBeforeCompatibilityWithOneRepaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    move(view, 50, 50, 0);
                    List<String> calls = new ArrayList<>();
                    view.addMapSelectionListener(event -> calls.add("selection"));
                    view.addMapHoverListener(event -> calls.add("hover"));
                    view.addMapPointerListener(
                            event ->
                                    calls.add(
                                            "compatibility:"
                                                    + view.selection().isPresent()
                                                    + ":"
                                                    + view.hover().isEmpty()));
                    RepaintManager previous = RepaintManager.currentManager(view);
                    RecordingRepaintManager recording = new RecordingRepaintManager();
                    RepaintManager.setCurrentManager(recording);
                    try {
                        click(view, 50, 50);
                        assertEquals(
                                List.of("selection", "hover", "compatibility:true:true"), calls);
                        recording.assertSingleFull(view);
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                });
    }

    @Test
    void focusLossCancelsToolWithoutClearingHover() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    move(view, 50, 50, 0);

                    for (java.awt.event.FocusListener listener : view.getFocusListeners()) {
                        listener.focusLost(new FocusEvent(view, FocusEvent.FOCUS_LOST));
                    }

                    assertTrue(view.hover().isPresent());
                });
    }

    @Test
    void passedMoveUsesOneHitTraversalAndReleaseClearsHover() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    int[] hitCalls = {0};
                    SymbolRendererKey key = new SymbolRendererKey("test.counting-marker");
                    MarkerSymbol marker = new TestMarker(key);
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(
                                            io.github.mundanej.map.api.SymbolRole.MARKER,
                                            key,
                                            new AwtSymbolRenderer() {
                                                @Override
                                                public boolean supports(Symbol value) {
                                                    return value == marker;
                                                }

                                                @Override
                                                public SymbolRenderResult render(
                                                        Symbol value,
                                                        AwtSymbolRenderContext context) {
                                                    return SymbolRenderResult.markerBounds(
                                                            new Envelope(45.0, 45.0, 55.0, 55.0),
                                                            AwtLogicalPaintPresence.PRESENT);
                                                }

                                                @Override
                                                public boolean hitTest(
                                                        Symbol value, AwtSymbolHitContext context) {
                                                    hitCalls[0]++;
                                                    return true;
                                                }
                                            })
                                    .build();
                    Feature feature =
                            new Feature(
                                    "feature",
                                    "",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    Map.of(),
                                    marker);
                    MapView view = TestMapViews.identity(registry);
                    configure(view, List.of(layer("layer", feature)));

                    move(view, 50, 50, 0);

                    assertEquals(1, hitCalls[0]);
                    assertTrue(view.hover().isPresent());
                    release(view, 50, 50);
                    assertTrue(view.hover().isEmpty());
                });
    }

    @Test
    void routeFailureRemainsPrimaryWhenHoverClearListenerAlsoFails() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(List.of(layer("layer", feature("feature", 0.0, 0.0, 20.0))));
                    move(view, 50, 50, 0);
                    RuntimeException routeFailure = new RuntimeException("route");
                    RuntimeException clearFailure = new RuntimeException("clear");
                    view.setActiveTool(
                            (event, context) -> {
                                if (event.type()
                                        == io.github.mundanej.map.api.MapToolEvent.Type.RELEASE) {
                                    throw routeFailure;
                                }
                                return MapToolResult.PASS;
                            });
                    view.addMapHoverListener(
                            event -> {
                                if (event.current().isEmpty()) {
                                    throw clearFailure;
                                }
                            });

                    RuntimeException thrown =
                            assertThrows(RuntimeException.class, () -> release(view, 50, 50));

                    assertSame(routeFailure, thrown);
                    assertEquals(List.of(clearFailure), List.of(thrown.getSuppressed()));
                    assertTrue(view.hover().isEmpty());
                });
    }

    @Test
    void clickHitFailureRemainsPrimaryAndStillClearsHover() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RuntimeException hitFailure = new RuntimeException("hit");
                    RuntimeException clearFailure = new RuntimeException("clear");
                    SymbolRendererKey key = new SymbolRendererKey("test.throwing-hit");
                    MarkerSymbol marker = new TestMarker(key);
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(
                                            io.github.mundanej.map.api.SymbolRole.MARKER,
                                            key,
                                            new AwtSymbolRenderer() {
                                                @Override
                                                public boolean supports(Symbol value) {
                                                    return value == marker;
                                                }

                                                @Override
                                                public SymbolRenderResult render(
                                                        Symbol value,
                                                        AwtSymbolRenderContext context) {
                                                    return SymbolRenderResult.markerBounds(
                                                            new Envelope(45, 45, 55, 55),
                                                            AwtLogicalPaintPresence.PRESENT);
                                                }

                                                @Override
                                                public boolean hitTest(
                                                        Symbol value, AwtSymbolHitContext context) {
                                                    throw hitFailure;
                                                }
                                            })
                                    .build();
                    MutableLayer layer =
                            new MutableLayer("layer", List.of(feature("feature", 0.0, 0.0, 20.0)));
                    MapView view = TestMapViews.identity(registry);
                    configure(view, List.of(layer));
                    move(view, 50, 50, 0);
                    layer.features =
                            List.of(
                                    new Feature(
                                            "feature",
                                            "",
                                            new PointGeometry(new Coordinate(0, 0)),
                                            Map.of(),
                                            marker));
                    view.addMapHoverListener(
                            event -> {
                                if (event.current().isEmpty()) {
                                    throw clearFailure;
                                }
                            });

                    RuntimeException thrown =
                            assertThrows(RuntimeException.class, () -> click(view, 50, 50));

                    assertSame(hitFailure, thrown);
                    assertEquals(List.of(clearFailure), List.of(thrown.getSuppressed()));
                    assertTrue(view.hover().isEmpty());
                });
    }

    private static MapView view(List<InMemoryLayer> layers) {
        MapView view = TestMapViews.identity();
        configure(view, List.copyOf(layers));
        return view;
    }

    private static MapView viewLayers(List<Layer> layers) {
        MapView view = TestMapViews.identity();
        configure(view, layers);
        return view;
    }

    private static void configure(MapView view, List<? extends Layer> layers) {
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0.0, 0.0, 1.0));
        view.setLayers(List.copyOf(layers));
    }

    private static InMemoryLayer layer(String id, Feature... features) {
        return new InMemoryLayer(id, id, List.of(features));
    }

    private static Feature feature(String id, double x, double y, double size) {
        Symbol symbol =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(40, 150, 80), size, 1.0);
        return new Feature(id, "", new PointGeometry(new Coordinate(x, y)), Map.of(), symbol);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void paintNarrow(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setClip(0, 0, 2, 2);
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void move(MapView view, int x, int y, int modifiers) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        modifiers,
                        x,
                        y,
                        0,
                        false,
                        MouseEvent.NOBUTTON));
    }

    private static void click(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        1,
                        false,
                        MouseEvent.BUTTON1));
    }

    private static void release(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        1,
                        false,
                        MouseEvent.BUTTON1));
    }

    private static void exit(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_EXITED,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        0,
                        false,
                        MouseEvent.NOBUTTON));
    }

    private static void assertColorNear(Rgba expected, int actualArgb, int tolerance) {
        Color actual = new Color(actualArgb, true);
        assertTrue(Math.abs(expected.red() - actual.getRed()) <= tolerance, actual::toString);
        assertTrue(Math.abs(expected.green() - actual.getGreen()) <= tolerance, actual::toString);
        assertTrue(Math.abs(expected.blue() - actual.getBlue()) <= tolerance, actual::toString);
    }

    private static long imageHash(BufferedImage image) {
        long hash = 1L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                hash = 31L * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }

    private static final class RecordingRepaintManager extends RepaintManager {
        private final List<int[]> dirtyRegions = new ArrayList<>();

        @Override
        public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            dirtyRegions.add(new int[] {x, y, width, height});
        }

        private void clear() {
            dirtyRegions.clear();
        }

        private void assertSingleFull(MapView view) {
            assertEquals(1, dirtyRegions.size());
            int[] last = dirtyRegions.getFirst();
            assertEquals(0, last[0]);
            assertEquals(0, last[1]);
            assertEquals(view.getWidth(), last[2]);
            assertEquals(view.getHeight(), last[3]);
        }
    }

    private static final class MutableLayer implements Layer {
        private final String id;
        private List<Feature> features;

        private MutableLayer(String id, List<Feature> features) {
            this.id = id;
            this.features = List.copyOf(features);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public List<Feature> features() {
            return features;
        }

        @Override
        public Optional<Envelope> envelope() {
            return Optional.empty();
        }
    }

    private record TestMarker(SymbolRendererKey rendererKey) implements MarkerSymbol {
        @Override
        public double opacity() {
            return 1.0;
        }
    }
}
