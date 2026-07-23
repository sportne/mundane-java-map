package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolFixtures;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbols;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MilitarySymbolRenderingTest {
    private static final int SIZE = 120;

    @Test
    void ordinaryCompositePaintAndHitTestingShareTheMilitarySymbolFootprint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Symbol symbol =
                            MilitarySymbols.resolveStrict(
                                    MilitarySymbolId.parse(
                                            MilitarySymbolFixtures.FRIEND_INFANTRY_PRESENT),
                                    MarkerPlacement.centeredScreen(54),
                                    MilitarySymbolPalette.lightBackground());
                    MapView view = TestMapViews.identity();
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
                    Feature feature =
                            new Feature(
                                    "infantry",
                                    "",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    symbol);
                    view.setLayers(
                            List.of(new InMemoryLayer("military", "Military", List.of(feature))));

                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(
                            "infantry",
                            view.hitTest(60, 60, 0).topmost().orElseThrow().featureId());
                    assertTrue(view.hitTest(15, 15, 0).topmost().isEmpty());
                    assertTrue(countNonWhite(image) > 700);
                });
    }

    private static int countNonWhite(BufferedImage image) {
        int result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    result++;
                }
            }
        }
        return result;
    }
}
