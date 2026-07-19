package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PointLabelRenderRegressionTest {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;
    private static final Rgba BLUE = Rgba.rgb(25, 95, 205);
    private static final Rgba RED = Rgba.rgb(195, 45, 45);
    private static final Rgba GREEN = Rgba.rgb(35, 145, 75);
    private static final MarkerSymbol BLUE_MARKER = marker(BuiltInMarker.CIRCLE, BLUE);
    private static final MarkerSymbol RED_MARKER = marker(BuiltInMarker.DIAMOND, RED);
    private static final MarkerSymbol GREEN_MARKER = marker(BuiltInMarker.SQUARE, GREEN);

    @Test
    void categoricalSelectionPriorityCollisionAndEdgeFallbackRemainVisible() throws Exception {
        BufferedImage[] rendered = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] = renderScenario());
        BufferedImage image = rendered[0];

        assertNear(BLUE, image.getRGB(30, 60));
        assertNear(RED, image.getRGB(60, 60));
        assertNear(GREEN, image.getRGB(45, 90));
        assertNear(RED, image.getRGB(100, 60));
        assertTrue(countNear(image, BLUE, 108, 32, 140, 56) >= 8);
        assertEquals(0, countNear(image, RED, 108, 32, 140, 56));
        assertTrue(countNear(image, GREEN, 105, 75, 139, 96) >= 8);
    }

    private static BufferedImage renderScenario() {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(WIDTH, HEIGHT);
        view.setBackground(Color.WHITE);
        view.setViewport(new MapViewport(WIDTH, HEIGHT, 0, 0, 1));

        CategoricalSymbolSelector categories =
                new CategoricalSymbolSelector(
                        "kind",
                        List.of(
                                new CategoricalSymbolRule(ThematicValue.text("blue"), BLUE_MARKER),
                                new CategoricalSymbolRule(ThematicValue.text("red"), RED_MARKER)),
                        Optional.of(GREEN_MARKER));
        InMemoryLayer categoryLayer =
                new InMemoryLayer(
                        "category",
                        "Category",
                        List.of(
                                feature("blue", "", -50, 0, "blue"),
                                feature("red", "", -20, 0, "red"),
                                feature("fallback", "", -35, -30, "other")));

        Feature shared = feature("shared", "WIN", 20, 0, "blue");
        Feature edge = feature("edge", "EDGE", 65, -30, "other");
        view.setLayerBindings(
                List.of(
                        MapLayerBinding.portrayedSnapshot(
                                categoryLayer, FeaturePortrayal.markers(categories)),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer("winner", "Winner", List.of(shared)),
                                FeaturePortrayal.markers(new FixedSymbolSelector(BLUE_MARKER))
                                        .withPointLabel(profile(BLUE, 10, PointLabelPosition.NE))),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer("loser", "Loser", List.of(shared)),
                                FeaturePortrayal.markers(new FixedSymbolSelector(RED_MARKER))
                                        .withPointLabel(profile(RED, 0, PointLabelPosition.NE))),
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer("edge", "Edge", List.of(edge)),
                                FeaturePortrayal.markers(new FixedSymbolSelector(GREEN_MARKER))
                                        .withPointLabel(
                                                profile(
                                                        GREEN,
                                                        0,
                                                        PointLabelPosition.E,
                                                        PointLabelPosition.W)))));

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
            view.close();
        }
        return image;
    }

    private static PointLabelProfile profile(
            Rgba color, int priority, PointLabelPosition... positions) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                new LabelTextStyle(color, LabelWeight.BOLD, 14),
                List.of(positions),
                4,
                0,
                0,
                1,
                priority,
                ResolutionRange.ALL);
    }

    private static Feature feature(String id, String name, double x, double y, String category) {
        return new Feature(
                id,
                name,
                new PointGeometry(new Coordinate(x, y)),
                Map.of("kind", category),
                BLUE_MARKER);
    }

    private static MarkerSymbol marker(BuiltInMarker shape, Rgba color) {
        return BuiltInMarkers.filledScreen(shape, color, 10, 1);
    }

    private static int countNear(
            BufferedImage image, Rgba expected, int minX, int minY, int maxX, int maxY) {
        int count = 0;
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                if (distance(expected, image.getRGB(x, y)) <= 48) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertNear(Rgba expected, int actualArgb) {
        assertTrue(distance(expected, actualArgb) <= 24);
    }

    private static int distance(Rgba expected, int actualArgb) {
        Color actual = new Color(actualArgb, true);
        return Math.max(
                Math.max(
                        Math.abs(expected.red() - actual.getRed()),
                        Math.abs(expected.green() - actual.getGreen())),
                Math.abs(expected.blue() - actual.getBlue()));
    }
}
