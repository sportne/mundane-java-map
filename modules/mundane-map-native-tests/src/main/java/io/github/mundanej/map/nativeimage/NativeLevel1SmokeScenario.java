package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.DistanceStrategy;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.AwtSymbolHitContext;
import io.github.mundanej.map.awt.AwtSymbolRenderContext;
import io.github.mundanej.map.awt.AwtSymbolRenderer;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.MeasurementTool;
import io.github.mundanej.map.awt.SymbolRenderResult;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Final Level 1 interaction and rendering scenario shared by JVM and Native Image. */
final class NativeLevel1SmokeScenario {
    static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.native-smoke.marker");

    private static final int WIDTH = 192;
    private static final int HEIGHT = 160;
    private static final Rgba LINE_COLOR = new Rgba(24, 112, 96, 255);
    private static final Rgba MARKER_FILL = new Rgba(145, 72, 196, 255);
    private static final Rgba MARKER_STROKE = new Rgba(32, 24, 48, 255);

    private NativeLevel1SmokeScenario() {}

    static Result run() {
        NativeLevel1SmokeAssertions.require(
                !SwingUtilities.isEventDispatchThread(),
                "level1-cleanup",
                "aggregate scenario started on EDT");
        verifyDuplicateRegistration();

        CrsRegistry registry = CrsRegistry.level1();
        CrsDefinition projected = registry.resolve("EPSG:3857");
        CrsDefinition geographic = registry.resolve("EPSG:4326");
        DistanceStrategy planar = DistanceStrategies.planarMetres(projected);
        DistanceResult planarResult =
                planar.distance(new Coordinate(0.0, 0.0), new Coordinate(3_000.0, 4_000.0));
        NativeLevel1SmokeAssertions.verifyPlanar(planar, projected, planarResult);
        DistanceStrategy greatCircle = DistanceStrategies.epsg4326GreatCircle(geographic);
        DistanceResult geographicResult =
                greatCircle.distance(new Coordinate(179.0, 0.0), new Coordinate(-179.0, 0.0));
        NativeLevel1SmokeAssertions.verifyGeographic(greatCircle, geographic, geographicResult);

        InteractionResult interaction =
                NativeShapefileSmokeScenario.onEdt(
                        () -> runInteractionAndRender(registry, projected, planar));
        return new Result(
                planarResult,
                geographicResult,
                interaction.preview(),
                interaction.complete(),
                interaction.renderSummary(),
                interaction.toolReusedAfterClose());
    }

    private static void verifyDuplicateRegistration() {
        SymbolRendererRegistry.Builder builder = SymbolRendererRegistry.builder();
        AwtSymbolRenderer renderer = new InertMarkerRenderer();
        builder.register(SymbolRole.MARKER, RENDERER_KEY, renderer);
        SymbolException failure = null;
        try {
            builder.register(SymbolRole.MARKER, RENDERER_KEY, renderer);
        } catch (SymbolException expected) {
            failure = expected;
        }
        NativeLevel1SmokeAssertions.require(
                failure != null,
                "registration-diagnostic",
                "duplicate renderer registration was accepted");
        NativeLevel1SmokeAssertions.verifyDuplicateRenderer(failure);
    }

    private static InteractionResult runInteractionAndRender(
            CrsRegistry registry, CrsDefinition projected, DistanceStrategy planar) {
        NativeLevel1SmokeAssertions.require(
                SwingUtilities.isEventDispatchThread(),
                "level1-cleanup",
                "Swing scenario ran off EDT");
        MapView first = createView(registry, projected);
        MapView second = createView(registry, projected);
        MeasurementTool tool = new MeasurementTool(planar);
        boolean firstClosed = false;
        boolean secondClosed = false;
        try {
            VectorMarkerSymbol marker = marker();
            SolidLineSymbol line = line();
            first.setLayers(List.of(layer(line, marker)));
            first.setActiveTool(tool);

            mouse(first, MouseEvent.MOUSE_CLICKED, 96, 80, MouseEvent.BUTTON1, 1);
            mouse(first, MouseEvent.MOUSE_MOVED, 126, 40, MouseEvent.NOBUTTON, 0);
            MeasurementState preview = tool.state();
            NativeLevel1SmokeAssertions.verifyPreview(preview);
            mouse(first, MouseEvent.MOUSE_CLICKED, 126, 40, MouseEvent.BUTTON1, 1);
            mouse(first, MouseEvent.MOUSE_CLICKED, 156, 80, MouseEvent.BUTTON1, 1);
            mouse(first, MouseEvent.MOUSE_CLICKED, 156, 80, MouseEvent.BUTTON1, 2);
            MeasurementState complete = tool.state();
            NativeLevel1SmokeAssertions.verifyComplete(complete);

            int[] firstPixels = paint(first);
            int[] secondPixels = paint(first);
            NativeLevel1SmokeAssertions.RenderSummary renderSummary =
                    NativeLevel1SmokeAssertions.verifyRender(
                            firstPixels, secondPixels, WIDTH, HEIGHT);

            boolean rejected = false;
            try {
                second.setActiveTool(tool);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            NativeLevel1SmokeAssertions.require(
                    rejected
                            && second.activeTool().isEmpty()
                            && first.activeTool().orElseThrow() == tool
                            && tool.state().equals(complete),
                    "level1-cleanup",
                    "cross-view ownership changed state");

            first.close();
            firstClosed = true;
            NativeLevel1SmokeAssertions.require(
                    tool.state().phase() == MeasurementPhase.EMPTY,
                    "level1-cleanup",
                    "close did not clear the measurement");
            second.setActiveTool(tool);
            NativeLevel1SmokeAssertions.require(
                    second.activeTool().orElseThrow() == tool
                            && tool.state().phase() == MeasurementPhase.EMPTY,
                    "level1-cleanup",
                    "closed view did not release the tool claim");
            second.close();
            secondClosed = true;
            NativeLevel1SmokeAssertions.require(
                    tool.state().phase() == MeasurementPhase.EMPTY,
                    "level1-cleanup",
                    "reused tool did not clean up");
            return new InteractionResult(preview, complete, renderSummary, true);
        } finally {
            if (!firstClosed) {
                first.close();
            }
            if (!secondClosed) {
                second.close();
            }
        }
    }

    private static MapView createView(CrsRegistry registry, CrsDefinition projected) {
        MapView view =
                new MapView(
                        registry,
                        projected,
                        projected,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setSize(WIDTH, HEIGHT);
        view.setViewport(new MapViewport(WIDTH, HEIGHT, 0.0, 0.0, 100.0));
        return view;
    }

    private static InMemoryLayer layer(SolidLineSymbol line, VectorMarkerSymbol marker) {
        Feature lineFeature =
                new Feature(
                        "level1-line",
                        "",
                        new LineStringGeometry(
                                CoordinateSequence.of(
                                        -12_000, -5_000, -8_000, -5_000, -6_500, -3_500, -5_000,
                                        -5_000, -3_500, -3_500, -2_000, -5_000)),
                        Map.of(),
                        line);
        Feature markerFeature =
                new Feature(
                        "level1-marker",
                        "",
                        new PointGeometry(new Coordinate(-6_600, 1_500)),
                        Map.of(),
                        marker);
        return new InMemoryLayer(
                "level1-native", "Level 1 Native", List.of(lineFeature, markerFeature));
    }

    private static SolidLineSymbol line() {
        return SolidLineSymbol.of(
                new SymbolStroke(LINE_COLOR, new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL)), 1.0);
    }

    private static VectorMarkerSymbol marker() {
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(BuiltInMarker.STAR),
                BuiltInMarkers.viewBox(),
                MARKER_FILL,
                Optional.of(
                        new SymbolStroke(
                                MARKER_STROKE, new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL))),
                MarkerPlacement.centeredScreen(24.0),
                1.0);
    }

    private static int[] paint(MapView view) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        RepaintManager manager = RepaintManager.currentManager(view);
        boolean enabled = manager.isDoubleBufferingEnabled();
        manager.setDoubleBufferingEnabled(false);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, WIDTH, HEIGHT);
                Graphics2D child = (Graphics2D) graphics.create();
                try {
                    child.setComposite(AlphaComposite.SrcOver);
                    view.paint(child);
                } finally {
                    child.dispose();
                }
            } finally {
                graphics.dispose();
            }
        } finally {
            manager.setDoubleBufferingEnabled(enabled);
        }
        return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
    }

    private static void mouse(MapView view, int type, int x, int y, int button, int clickCount) {
        view.dispatchEvent(new MouseEvent(view, type, 1L, 0, x, y, clickCount, false, button));
    }

    record Result(
            DistanceResult planar,
            DistanceResult geographic,
            MeasurementState preview,
            MeasurementState complete,
            NativeLevel1SmokeAssertions.RenderSummary renderSummary,
            boolean toolReusedAfterClose) {}

    private record InteractionResult(
            MeasurementState preview,
            MeasurementState complete,
            NativeLevel1SmokeAssertions.RenderSummary renderSummary,
            boolean toolReusedAfterClose) {}

    private static final class InertMarkerRenderer implements AwtSymbolRenderer {
        @Override
        public boolean supports(Symbol value) {
            return false;
        }

        @Override
        public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
            throw new IllegalStateException("registration-diagnostic: inert renderer invoked");
        }

        @Override
        public boolean hitTest(Symbol value, AwtSymbolHitContext context) {
            return false;
        }
    }
}
