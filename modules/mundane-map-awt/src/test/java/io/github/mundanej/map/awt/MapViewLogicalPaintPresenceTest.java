package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class MapViewLogicalPaintPresenceTest {
    private static final int SIZE = 100;

    @Test
    void builtInLineFillHatchRasterCompositeAndLegacyPresenceControlsOverlays() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol line = SolidLineSymbol.of(stroke(Rgba.rgb(30, 90, 180), 4), 1);
                    SolidLineSymbol endpointOnly =
                            SolidLineSymbol.of(
                                    stroke(Rgba.TRANSPARENT, 1),
                                    Optional.empty(),
                                    Optional.of(
                                            BuiltInMarkers.filledScreen(
                                                    io.github.mundanej.map.api.BuiltInMarker.ARROW,
                                                    Rgba.rgb(30, 90, 180),
                                                    12,
                                                    1)),
                                    1);
                    PolygonGeometry polygon =
                            new PolygonGeometry(
                                    CoordinateSequence.of(
                                            -20, -20, 20, -20, 20, 20, -20, 20, -20, -20));
                    SolidFillSymbol fill = SolidFillSymbol.of(Rgba.rgb(50, 150, 90), 1);
                    HatchFillSymbol hatch =
                            HatchFillSymbol.of(
                                    HatchPattern.CROSS_DIAGONAL,
                                    stroke(Rgba.rgb(160, 40, 120), 2),
                                    new SymbolLength(8, SymbolUnit.SCREEN_PIXEL),
                                    SymbolRotationMode.SCREEN_RELATIVE,
                                    Optional.empty(),
                                    1,
                                    128);
                    RasterIconSymbol transparentRaster =
                            RasterIconSymbol.of(
                                    1,
                                    1,
                                    new int[] {0x2266aaff},
                                    MarkerPlacement.centeredScreen(20),
                                    RasterInterpolation.NEAREST,
                                    0);

                    assertOverlayVisible(lineFeature("line", line));
                    assertOverlayVisible(lineFeature("endpoint", endpointOnly));
                    assertOverlaySuppressed(
                            feature(
                                    "coincident",
                                    new LineStringGeometry(CoordinateSequence.of(0, 0, 0, 0, 0, 0)),
                                    line));
                    assertOverlayVisible(feature("fill", polygon, fill));
                    assertOverlayVisible(feature("hatch", polygon, hatch));
                    assertOverlayVisible(
                            lineFeature("composite", CompositeSymbol.of(List.of(line), 1)));
                    assertOverlaySuppressed(point("raster", "", transparentRaster));
                    assertOverlaySuppressed(
                            point(
                                    "empty-composite",
                                    "",
                                    CompositeSymbol.of(List.of(transparentRaster), 1)));
                    assertOverlaySuppressed(
                            point(
                                    "legacy",
                                    "Visible label",
                                    new FeatureStyle(Rgba.TRANSPARENT, Rgba.TRANSPARENT, 0, 12)));
                });
    }

    @Test
    void customRendererUnknownSuppressesAndExplicitPresentPermitsOverlay() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    assertCustomPresence(AwtLogicalPaintPresence.UNKNOWN, false);
                    assertCustomPresence(AwtLogicalPaintPresence.PRESENT, true);
                });
    }

    private static void assertCustomPresence(
            AwtLogicalPaintPresence presence, boolean overlayExpected) {
        SymbolRendererKey key =
                new SymbolRendererKey("test.presence-" + presence.name().toLowerCase(Locale.ROOT));
        MarkerSymbol marker = new TestMarker(key);
        SymbolRendererRegistry registry =
                SymbolRendererRegistry.builderWithBuiltIns()
                        .register(
                                SymbolRole.MARKER,
                                key,
                                new AwtSymbolRenderer() {
                                    @Override
                                    public boolean supports(Symbol value) {
                                        return value == marker;
                                    }

                                    @Override
                                    public SymbolRenderResult render(
                                            Symbol value, AwtSymbolRenderContext context) {
                                        return SymbolRenderResult.markerBounds(
                                                new Envelope(45, 45, 55, 55), presence);
                                    }
                                })
                        .build();
        MapView view = configured(point("custom", "", marker), registry);
        long before = hash(paint(view));
        view.setSelection(new FeatureSelection("layer", "custom"));
        long after = hash(paint(view));
        if (overlayExpected) {
            assertNotEquals(before, after);
        } else {
            assertEquals(before, after);
        }
    }

    private static void assertOverlayVisible(Feature feature) {
        long[] hashes = selectedHashes(feature, SymbolRendererRegistry.builtIn());
        assertNotEquals(hashes[0], hashes[1], feature.id());
    }

    private static void assertOverlaySuppressed(Feature feature) {
        long[] hashes = selectedHashes(feature, SymbolRendererRegistry.builtIn());
        assertEquals(hashes[0], hashes[1], feature.id());
    }

    private static long[] selectedHashes(Feature feature, SymbolRendererRegistry registry) {
        MapView view = configured(feature, registry);
        long before = hash(paint(view));
        view.setSelection(new FeatureSelection("layer", feature.id()));
        return new long[] {before, hash(paint(view))};
    }

    private static MapView configured(Feature feature, SymbolRendererRegistry registry) {
        MapView view = TestMapViews.identity(registry);
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
        view.setLayers(List.of(new InMemoryLayer("layer", "layer", List.of(feature))));
        return view;
    }

    private static Feature lineFeature(String id, Symbol symbol) {
        return feature(id, new LineStringGeometry(CoordinateSequence.of(-20, 0, 20, 0)), symbol);
    }

    private static Feature point(String id, String name, Symbol symbol) {
        return new Feature(id, name, new PointGeometry(new Coordinate(0, 0)), Map.of(), symbol);
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, Symbol symbol) {
        return new Feature(id, "", geometry, Map.of(), symbol);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
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

    private static long hash(BufferedImage image) {
        long result = 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                result = 31 * result + image.getRGB(x, y);
            }
        }
        return result;
    }

    private record TestMarker(SymbolRendererKey rendererKey) implements MarkerSymbol {
        @Override
        public double opacity() {
            return 1;
        }
    }
}
