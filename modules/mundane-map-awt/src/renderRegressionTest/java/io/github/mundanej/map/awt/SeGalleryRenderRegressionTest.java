package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.se.SeFeatureStyle;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SeGalleryRenderRegressionTest {
    private static final String RESOURCE = "/io/github/mundanej/map/example/se/gallery-style.xml";

    @Test
    @SuppressWarnings("deprecation")
    void galleryProvesRulesScaleCatalogAndAllVectorRolesWithoutPixelIdentity() throws Exception {
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol(
                                        "gallery.airport",
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.DIAMOND,
                                                Rgba.rgb(170, 60, 150),
                                                34,
                                                1))));
        SeFeatureStyle style =
                SeStyles.read("gallery-regression", resource(), catalog, SeReadOptions.defaults());
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> inRangeReference = new AtomicReference<>();
        AtomicReference<BufferedImage> outOfRangeReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureStyle point = FeatureStyle.point(Rgba.rgb(100, 100, 100), 6);
                    FeatureStyle line = FeatureStyle.line(Rgba.rgb(100, 100, 100), 1);
                    FeatureStyle fill =
                            FeatureStyle.polygon(
                                    Rgba.rgb(100, 100, 100), Rgba.rgb(100, 100, 100), 1);
                    List<Feature> features =
                            List.of(
                                    new Feature(
                                            "ordered",
                                            "ordered",
                                            new PointGeometry(new Coordinate(-70, 70)),
                                            Map.of("kind", "ordered"),
                                            point),
                                    new Feature(
                                            "catalog",
                                            "catalog",
                                            new PointGeometry(new Coordinate(0, 70)),
                                            Map.of("kind", "catalog"),
                                            point),
                                    new Feature(
                                            "scale",
                                            "scale",
                                            new PointGeometry(new Coordinate(70, 70)),
                                            Map.of("kind", "scale"),
                                            point),
                                    new Feature(
                                            "line",
                                            "line",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(-110, 15, 110, 15)),
                                            Map.of("kind", "line"),
                                            line),
                                    new Feature(
                                            "area",
                                            "area",
                                            new PolygonGeometry(
                                                    CoordinateSequence.of(
                                                            -80, -85, 80, -85, 80, -25, -80, -25,
                                                            -80, -85),
                                                    List.of(
                                                            CoordinateSequence.of(
                                                                    -20, -65, 20, -65, 20, -45, -20,
                                                                    -45, -20, -65))),
                                            Map.of("kind", "area"),
                                            fill));
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(300, 240);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.portrayedSnapshot(
                                            new InMemoryLayer("gallery", "gallery", features),
                                            style.portrayal())));
                    view.setViewport(new MapViewport(300, 240, 0, 0, 1));
                    inRangeReference.set(paint(view));
                    view.setViewport(new MapViewport(300, 240, 0, 0, 2));
                    outOfRangeReference.set(paint(view));
                    viewReference.set(view);
                });

        BufferedImage inRange = inRangeReference.get();
        assertFamily(inRange, 80, 50, Family.BLUE, "ordered point");
        assertNearWhite(inRange, 80, 50, "top point cross");
        assertFamily(inRange, 150, 50, Family.PURPLE, "catalog marker");
        assertFamily(inRange, 220, 50, Family.ORANGE, "scale-filtered marker");
        assertFamily(inRange, 150, 105, Family.DARK_BLUE, "top ordered line");
        assertFamily(inRange, 150, 109, Family.LIGHT_BLUE, "underlying ordered line");
        assertFamily(inRange, 90, 165, Family.GREEN, "polygon fill");
        assertNearWhite(inRange, 70, 165, "top polygon outline");
        assertFamily(inRange, 73, 165, Family.DARK_GREEN, "underlying polygon outline");
        Color hole = new Color(inRange.getRGB(150, 175), true);
        assertTrue(hole.getRed() > 245 && hole.getGreen() > 245 && hole.getBlue() > 245);

        BufferedImage outOfRange = outOfRangeReference.get();
        assertNoFamily(outOfRange, 185, 85, Family.ORANGE, "scale-filtered marker");
        SwingUtilities.invokeAndWait(viewReference.get()::close);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(300, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertFamily(
            BufferedImage image, int centerX, int centerY, Family family, String label) {
        assertTrue(count(image, centerX, centerY, family) > 4, label + " has no tolerant evidence");
    }

    private static void assertNoFamily(
            BufferedImage image, int centerX, int centerY, Family family, String label) {
        assertTrue(count(image, centerX, centerY, family) == 0, label + " remained out of range");
    }

    private static void assertNearWhite(
            BufferedImage image, int centerX, int centerY, String label) {
        Color color = new Color(image.getRGB(centerX, centerY), true);
        assertTrue(
                color.getRed() > 235 && color.getGreen() > 235 && color.getBlue() > 235,
                label + " did not retain top-layer white paint");
    }

    private static int count(BufferedImage image, int centerX, int centerY, Family family) {
        int result = 0;
        for (int y = centerY - 20; y <= centerY + 20; y++) {
            for (int x = centerX - 20; x <= centerX + 20; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (family.matches(color)) {
                    result++;
                }
            }
        }
        return result;
    }

    private static byte[] resource() throws IOException {
        try (InputStream input =
                SeGalleryRenderRegressionTest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("missing gallery resource");
            }
            return input.readAllBytes();
        }
    }

    private enum Family {
        BLUE {
            @Override
            boolean matches(Color color) {
                return color.getBlue() > color.getRed() + 35;
            }
        },
        DARK_BLUE {
            @Override
            boolean matches(Color color) {
                return color.getBlue() > 90
                        && color.getBlue() < 175
                        && color.getGreen() < 120
                        && color.getRed() < 80;
            }
        },
        LIGHT_BLUE {
            @Override
            boolean matches(Color color) {
                return color.getBlue() > 190 && color.getGreen() > 150 && color.getRed() > 115;
            }
        },
        PURPLE {
            @Override
            boolean matches(Color color) {
                return color.getRed() > color.getGreen() + 45
                        && color.getBlue() > color.getGreen() + 45;
            }
        },
        ORANGE {
            @Override
            boolean matches(Color color) {
                return color.getRed() > color.getGreen() + 35
                        && color.getGreen() > color.getBlue() + 25;
            }
        },
        GREEN {
            @Override
            boolean matches(Color color) {
                return color.getGreen() > color.getRed() + 25;
            }
        },
        DARK_GREEN {
            @Override
            boolean matches(Color color) {
                return color.getGreen() > color.getRed() + 20
                        && color.getGreen() < 145
                        && color.getRed() < 100;
            }
        };

        abstract boolean matches(Color color);
    }
}
