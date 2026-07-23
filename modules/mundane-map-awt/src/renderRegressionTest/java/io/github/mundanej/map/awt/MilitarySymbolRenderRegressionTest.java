package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolFixtures;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MilitarySymbolRenderRegressionTest {
    private static final int SIZE = 140;

    @Test
    void friendInfantryUsesTolerantBoundsFillAndInkInTheRealRenderer() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    SymbolRendererRegistry.builderWithBuiltIns().build());
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "military",
                                            "Military",
                                            List.of(
                                                    new Feature(
                                                            "friend-infantry",
                                                            "",
                                                            new PointGeometry(new Coordinate(0, 0)),
                                                            Map.of(),
                                                            MilitarySymbols.resolveStrict(
                                                                    MilitarySymbolId.parse(
                                                                            MilitarySymbolFixtures
                                                                                    .FRIEND_INFANTRY_PRESENT),
                                                                    MarkerPlacement.centeredScreen(
                                                                            60),
                                                                    MilitarySymbolPalette
                                                                            .lightBackground()))))));
                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, SIZE, SIZE);
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    int[] bounds = nonWhiteBounds(image);
                    assertTrue(bounds[0] >= 42 && bounds[0] <= 48);
                    assertTrue(bounds[1] >= 50 && bounds[1] <= 60);
                    assertTrue(bounds[2] >= 92 && bounds[2] <= 98);
                    assertTrue(bounds[3] >= 80 && bounds[3] <= 90);
                    assertTrue(colorCount(image, new Color(0, 107, 140), 10) > 900);
                    assertTrue(colorCount(image, Color.BLACK, 18) > 100);
                });
    }

    private static int[] nonWhiteBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static int colorCount(BufferedImage image, Color expected, int tolerance) {
        int result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance) {
                    result++;
                }
            }
        }
        return result;
    }
}
