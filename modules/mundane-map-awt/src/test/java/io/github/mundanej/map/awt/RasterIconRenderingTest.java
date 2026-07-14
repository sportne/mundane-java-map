package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RasterIconRenderingTest {
    private static final int IMAGE_SIZE = 100;

    @Test
    void rendersNonSquareRowsAlphaAndClockwiseRotation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    int[] pixels = {
                        0xff0000ff, 0x00ff0080, 0x0000ffff, 0xffff00ff, 0xff00ffff, 0x00ffffff
                    };
                    RasterIconSymbol upright =
                            RasterIconSymbol.of(
                                    2,
                                    3,
                                    pixels,
                                    placement(
                                            20,
                                            30,
                                            SymbolAnchor.CENTER,
                                            0,
                                            0,
                                            0,
                                            SymbolUnit.SCREEN_PIXEL),
                                    RasterInterpolation.NEAREST,
                                    1);
                    BufferedImage image = render(point("upright", "", upright), 1);
                    assertColor(image, 45, 40, new Color(255, 0, 0));
                    assertColor(image, 45, 50, new Color(0, 0, 255));
                    assertColor(image, 45, 60, new Color(255, 0, 255));
                    Color translucentGreen = new Color(image.getRGB(55, 40), true);
                    assertEquals(127, translucentGreen.getRed(), 2);
                    assertEquals(255, translucentGreen.getGreen(), 1);
                    assertEquals(127, translucentGreen.getBlue(), 2);

                    RasterIconSymbol rotated =
                            RasterIconSymbol.of(
                                    2,
                                    3,
                                    pixels,
                                    placement(
                                            20,
                                            30,
                                            SymbolAnchor.CENTER,
                                            0,
                                            0,
                                            90,
                                            SymbolUnit.SCREEN_PIXEL),
                                    RasterInterpolation.NEAREST,
                                    1);
                    BufferedImage rotatedImage = render(point("rotated", "", rotated), 1);
                    assertColor(rotatedImage, 60, 45, new Color(255, 0, 0));
                    assertColor(rotatedImage, 40, 55, new Color(0, 255, 255));
                });
    }

    @Test
    void appliesEveryAnchorOffsetsSizeUnitsOpacityAndNominalLabelBounds() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    for (SymbolAnchor anchor : SymbolAnchor.values()) {
                        RasterIconSymbol icon =
                                RasterIconSymbol.of(
                                        1,
                                        1,
                                        new int[] {0x145ac8ff},
                                        placement(8, 6, anchor, 2, 3, 0, SymbolUnit.SCREEN_PIXEL),
                                        RasterInterpolation.NEAREST,
                                        1);
                        int[] bounds = paintedBounds(render(point(anchor.name(), "", icon), 1));
                        double[] fractions = anchorFractions(anchor);
                        assertEquals((int) (52 - fractions[0] * 8), bounds[0], 1, anchor::name);
                        assertEquals((int) (53 - fractions[1] * 6), bounds[1], 1, anchor::name);
                        assertEquals(8, bounds[2] - bounds[0] + 1, 1, anchor::name);
                        assertEquals(6, bounds[3] - bounds[1] + 1, 1, anchor::name);
                    }

                    MarkerPlacement screen =
                            placement(
                                    20, 10, SymbolAnchor.CENTER, 0, 0, 0, SymbolUnit.SCREEN_PIXEL);
                    MarkerPlacement map =
                            placement(20, 10, SymbolAnchor.CENTER, 0, 0, 0, SymbolUnit.MAP_UNIT);
                    assertEquals(
                            paintedWidth(render(point("screen", "", solidIcon(screen, 1)), 1)),
                            paintedWidth(render(point("screen", "", solidIcon(screen, 1)), 2)),
                            1);
                    assertTrue(
                            paintedWidth(render(point("map", "", solidIcon(map, 1)), 1))
                                    >= paintedWidth(render(point("map", "", solidIcon(map, 1)), 2))
                                                    * 2
                                            - 2);

                    RasterIconSymbol transparent = solidIcon(screen, 0);
                    int[] labelBounds =
                            paintedBounds(render(point("transparent", "Label", transparent), 1));
                    assertTrue(labelBounds[0] >= 62);
                    assertTrue(labelBounds[2] > 80);
                    assertTrue(labelBounds[3] <= 50);
                });
    }

    @Test
    void selectsInterpolationComposesInOrderAndPreservesCallerGraphicsState() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MarkerPlacement placement = MarkerPlacement.centeredScreen(20);
                    int[] split = {0xff0000ff, 0x0000ffff};
                    RasterIconSymbol nearest =
                            RasterIconSymbol.of(
                                    2, 1, split, placement, RasterInterpolation.NEAREST, 0.5);
                    RasterIconSymbol bilinear =
                            RasterIconSymbol.of(
                                    2, 1, split, placement, RasterInterpolation.BILINEAR, 1);
                    BufferedImage nearestImage = render(point("nearest", "", nearest), 1);
                    BufferedImage bilinearImage = render(point("bilinear", "", bilinear), 1);
                    assertNotEquals(nearestImage.getRGB(49, 50), bilinearImage.getRGB(49, 50));
                    Color fadedRed = new Color(nearestImage.getRGB(45, 50), true);
                    assertEquals(255, fadedRed.getRed(), 1);
                    assertEquals(127, fadedRed.getGreen(), 2);
                    assertEquals(127, fadedRed.getBlue(), 2);

                    VectorMarkerSymbol blue =
                            VectorMarkerSymbol.of(
                                    VectorPath.builder()
                                            .moveTo(0, 0)
                                            .lineTo(1, 0)
                                            .lineTo(1, 1)
                                            .lineTo(0, 1)
                                            .close()
                                            .build(),
                                    new Envelope(0, 0, 1, 1),
                                    Rgba.rgb(0, 0, 255),
                                    Optional.empty(),
                                    MarkerPlacement.centeredScreen(8),
                                    1);
                    Symbol redRaster = solidIcon(placement, 1);
                    assertColor(
                            render(
                                    point(
                                            "blue-top",
                                            "",
                                            CompositeSymbol.of(List.of(redRaster, blue), 1)),
                                    1),
                            50,
                            50,
                            Color.BLUE);
                    assertColor(
                            render(
                                    point(
                                            "red-top",
                                            "",
                                            CompositeSymbol.of(List.of(blue, redRaster), 1)),
                                    1),
                            50,
                            50,
                            new Color(20, 90, 200));

                    MapView view = configuredView(point("state", "", redRaster), 1);
                    BufferedImage target =
                            new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = target.createGraphics();
                    try {
                        AlphaComposite composite =
                                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f);
                        AffineTransform transform = AffineTransform.getTranslateInstance(1, 2);
                        graphics.setComposite(composite);
                        graphics.setTransform(transform);
                        graphics.setRenderingHint(
                                RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        view.paint(graphics);
                        assertEquals(composite, graphics.getComposite());
                        assertEquals(transform, graphics.getTransform());
                        assertEquals(
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                                graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION));
                    } finally {
                        graphics.dispose();
                    }
                });
    }

    private static RasterIconSymbol solidIcon(MarkerPlacement placement, double opacity) {
        return RasterIconSymbol.of(
                1, 1, new int[] {0x145ac8ff}, placement, RasterInterpolation.NEAREST, opacity);
    }

    private static MarkerPlacement placement(
            double width,
            double height,
            SymbolAnchor anchor,
            double offsetX,
            double offsetY,
            double rotation,
            SymbolUnit unit) {
        return new MarkerPlacement(
                new SymbolSize(width, height, unit),
                anchor,
                offsetX,
                offsetY,
                rotation,
                SymbolRotationMode.SCREEN_RELATIVE);
    }

    private static Feature point(String id, String name, Symbol symbol) {
        return new Feature(id, name, new PointGeometry(new Coordinate(0, 0)), Map.of(), symbol);
    }

    private static BufferedImage render(Feature feature, double worldUnitsPerPixel) {
        MapView view = configuredView(feature, worldUnitsPerPixel);
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static MapView configuredView(Feature feature, double worldUnitsPerPixel) {
        MapView view = TestMapViews.identity();
        view.setSize(IMAGE_SIZE, IMAGE_SIZE);
        view.setViewport(new MapViewport(IMAGE_SIZE, IMAGE_SIZE, 0, 0, worldUnitsPerPixel));
        view.setLayers(List.of(new InMemoryLayer("layer", "Layer", List.of(feature))));
        return view;
    }

    private static double[] anchorFractions(SymbolAnchor anchor) {
        return switch (anchor) {
            case NORTH_WEST -> new double[] {0, 0};
            case NORTH -> new double[] {0.5, 0};
            case NORTH_EAST -> new double[] {1, 0};
            case WEST -> new double[] {0, 0.5};
            case CENTER -> new double[] {0.5, 0.5};
            case EAST -> new double[] {1, 0.5};
            case SOUTH_WEST -> new double[] {0, 1};
            case SOUTH -> new double[] {0.5, 1};
            case SOUTH_EAST -> new double[] {1, 1};
        };
    }

    private static int[] paintedBounds(BufferedImage image) {
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() < 250 || color.getGreen() < 250 || color.getBlue() < 250) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        return new int[] {minimumX, minimumY, maximumX, maximumY};
    }

    private static int paintedWidth(BufferedImage image) {
        int[] bounds = paintedBounds(image);
        return bounds[2] - bounds[0] + 1;
    }

    private static void assertColor(BufferedImage image, int x, int y, Color expected) {
        Color actual = new Color(image.getRGB(x, y), true);
        assertEquals(expected.getRed(), actual.getRed(), 2);
        assertEquals(expected.getGreen(), actual.getGreen(), 2);
        assertEquals(expected.getBlue(), actual.getBlue(), 2);
        assertEquals(255, actual.getAlpha());
    }
}
