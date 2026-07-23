package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolException;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolFixtures;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assertions;
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

    @Test
    void completeEntityInventoryAndIdentityStatusPaletteMatrixRemainVisiblyRenderable()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    for (MilitarySymbolCatalogEntry entry : MilitarySymbolCatalog.entries()) {
                        String sidc = sidc(3, entry.symbolSet(), 0, entry.entityCode(), 0, 0);
                        BufferedImage image =
                                render(
                                        MilitarySymbols.resolveStrict(
                                                MilitarySymbolId.parse(sidc),
                                                MarkerPlacement.centeredScreen(60),
                                                MilitarySymbolPalette.lightBackground()),
                                        Color.WHITE);
                        BufferedImage frameOnly =
                                render(
                                        MilitarySymbols.resolveDegraded(
                                                        MilitarySymbolId.parse(
                                                                sidc(
                                                                        3,
                                                                        entry.symbolSet(),
                                                                        0,
                                                                        0x999999,
                                                                        0,
                                                                        0)),
                                                        MarkerPlacement.centeredScreen(60),
                                                        MilitarySymbolPalette.lightBackground())
                                                .symbol(),
                                        Color.WHITE);
                        assertTrue(nonBackgroundCount(image, Color.WHITE) > 700, entry.name());
                        assertTrue(
                                differenceCount(image, frameOnly, 20) > 35,
                                entry.name() + " icon must alter the frame-only rendering");
                        int[] iconBounds = differenceBounds(image, frameOnly, 20);
                        assertTrue(
                                iconBounds[0] >= 32
                                        && iconBounds[1] >= 32
                                        && iconBounds[2] <= 108
                                        && iconBounds[3] <= 108,
                                entry.name());
                        assertTolerantBounds(image, Color.WHITE, entry.name());
                    }
                    for (MilitarySymbolPalette palette :
                            List.of(
                                    MilitarySymbolPalette.lightBackground(),
                                    MilitarySymbolPalette.darkBackground())) {
                        Color background =
                                palette == MilitarySymbolPalette.lightBackground()
                                        ? Color.WHITE
                                        : new Color(28, 32, 38);
                        for (int identity = 0; identity <= 6; identity++) {
                            for (int status = 0; status <= 1; status++) {
                                String sidc = sidc(identity, 0x10, status, 0x121100, 0x25, 0x02);
                                BufferedImage image =
                                        render(
                                                MilitarySymbols.resolveStrict(
                                                        MilitarySymbolId.parse(sidc),
                                                        MarkerPlacement.centeredScreen(60),
                                                        palette),
                                                background);
                                assertTrue(colorCount(image, color(palette.ink()), 20) > 80, sidc);
                                assertTolerantBounds(image, background, sidc);
                            }
                        }
                    }
                });
    }

    @Test
    void everyModifierAndFallbackHasASeparateTolerantRenderOracle() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    int[][] modifiers = {
                        {0x10, 0x121100, 0x25, 0x00},
                        {0x10, 0x121100, 0x77, 0x00},
                        {0x10, 0x121100, 0x00, 0x02},
                        {0x15, 0x120200, 0x13, 0x00},
                        {0x15, 0x120200, 0x00, 0x06},
                        {0x40, 0x140000, 0x17, 0x00},
                        {0x40, 0x140000, 0x00, 0x04}
                    };
                    for (int[] variation : modifiers) {
                        String base = sidc(3, variation[0], 0, variation[1], 0, 0);
                        String modified =
                                sidc(3, variation[0], 0, variation[1], variation[2], variation[3]);
                        BufferedImage baseImage =
                                render(
                                        MilitarySymbols.resolveStrict(
                                                MilitarySymbolId.parse(base),
                                                MarkerPlacement.centeredScreen(60),
                                                MilitarySymbolPalette.lightBackground()),
                                        Color.WHITE);
                        BufferedImage modifiedImage =
                                render(
                                        MilitarySymbols.resolveStrict(
                                                MilitarySymbolId.parse(modified),
                                                MarkerPlacement.centeredScreen(60),
                                                MilitarySymbolPalette.lightBackground()),
                                        Color.WHITE);
                        int[] bounds = differenceBounds(modifiedImage, baseImage, 20);
                        assertTrue(differenceCount(modifiedImage, baseImage, 20) > 8, modified);
                        assertTrue(bounds[0] >= 0 && bounds[1] >= 0, modified);
                        assertTrue(bounds[2] < SIZE && bounds[3] < SIZE, modified);
                        assertTrue(bounds[2] - bounds[0] >= 2, modified);
                        assertTrue(bounds[3] - bounds[1] >= 2, modified);
                    }

                    String supported = sidc(3, 0x10, 0, 0x121100, 0, 0);
                    String degradedEntity = sidc(3, 0x10, 0, 0x999999, 0, 0);
                    BufferedImage supportedImage =
                            render(
                                    MilitarySymbols.resolveStrict(
                                            MilitarySymbolId.parse(supported),
                                            MarkerPlacement.centeredScreen(60),
                                            MilitarySymbolPalette.lightBackground()),
                                    Color.WHITE);
                    BufferedImage frameOnlyImage =
                            render(
                                    MilitarySymbols.resolveDegraded(
                                                    MilitarySymbolId.parse(degradedEntity),
                                                    MarkerPlacement.centeredScreen(60),
                                                    MilitarySymbolPalette.lightBackground())
                                            .symbol(),
                                    Color.WHITE);
                    assertTrue(differenceCount(supportedImage, frameOnlyImage, 20) > 35);
                    assertTolerantBounds(frameOnlyImage, Color.WHITE, degradedEntity);

                    String degradedModifier = sidc(3, 0x10, 0, 0x121100, 0xff, 0);
                    BufferedImage omittedModifierImage =
                            render(
                                    MilitarySymbols.resolveDegraded(
                                                    MilitarySymbolId.parse(degradedModifier),
                                                    MarkerPlacement.centeredScreen(60),
                                                    MilitarySymbolPalette.lightBackground())
                                            .symbol(),
                                    Color.WHITE);
                    Assertions.assertEquals(
                            0, differenceCount(supportedImage, omittedModifierImage, 0));

                    String unsupported = supported.substring(0, 2) + "F" + supported.substring(3);
                    MilitarySymbolException failure =
                            Assertions.assertThrows(
                                    MilitarySymbolException.class,
                                    () ->
                                            MilitarySymbols.resolveDegraded(
                                                    MilitarySymbolId.parse(unsupported),
                                                    MarkerPlacement.centeredScreen(60),
                                                    MilitarySymbolPalette.lightBackground()));
                    Assertions.assertEquals(
                            "MIL2525_CONTEXT_UNSUPPORTED", failure.problem().code());
                });
    }

    private static BufferedImage render(Symbol symbol, Color background) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setSize(SIZE, SIZE);
        view.setOpaque(false);
        view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
        view.setLayers(
                List.of(
                        new InMemoryLayer(
                                "military",
                                "Military",
                                List.of(
                                        new Feature(
                                                "reference",
                                                "",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                symbol)))));
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, SIZE, SIZE);
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertTolerantBounds(
            BufferedImage image, Color background, String description) {
        int[] bounds = nonBackgroundBounds(image, background);
        assertTrue(bounds[0] >= 0 && bounds[0] <= 60, description);
        assertTrue(bounds[1] >= 0 && bounds[1] <= 65, description);
        assertTrue(bounds[2] >= 80 && bounds[2] < SIZE, description);
        assertTrue(bounds[3] >= 75 && bounds[3] < SIZE, description);
        assertTrue(bounds[0] < SIZE / 2 && bounds[2] > SIZE / 2, description);
        assertTrue(bounds[1] < SIZE / 2 && bounds[3] > SIZE / 2, description);
    }

    private static int nonBackgroundCount(BufferedImage image, Color background) {
        int count = 0;
        int expected = background.getRGB() & 0x00ffffff;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != expected) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int differenceCount(
            BufferedImage first, BufferedImage second, int channelTolerance) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (different(first.getRGB(x, y), second.getRGB(x, y), channelTolerance)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int[] differenceBounds(
            BufferedImage first, BufferedImage second, int channelTolerance) {
        int minX = first.getWidth();
        int minY = first.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (different(first.getRGB(x, y), second.getRGB(x, y), channelTolerance)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static boolean different(int first, int second, int channelTolerance) {
        Color firstColor = new Color(first, true);
        Color secondColor = new Color(second, true);
        return Math.abs(firstColor.getRed() - secondColor.getRed()) > channelTolerance
                || Math.abs(firstColor.getGreen() - secondColor.getGreen()) > channelTolerance
                || Math.abs(firstColor.getBlue() - secondColor.getBlue()) > channelTolerance
                || Math.abs(firstColor.getAlpha() - secondColor.getAlpha()) > channelTolerance;
    }

    private static int[] nonBackgroundBounds(BufferedImage image, Color background) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int expected = background.getRGB() & 0x00ffffff;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != expected) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static Color color(Rgba color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    private static String sidc(
            int identity, int symbolSet, int status, int entity, int sectorOne, int sectorTwo) {
        int frame =
                switch (symbolSet) {
                    case 0x10 -> 3;
                    case 0x15 -> 4;
                    case 0x40 -> 8;
                    default -> throw new AssertionError("unsupported test symbol set");
                };
        return String.format(
                Locale.ROOT,
                "150%1X%02X%1X000%06X%02X%02X00%1X0000000",
                identity,
                symbolSet,
                status,
                entity,
                sectorOne,
                sectorTwo,
                frame);
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
