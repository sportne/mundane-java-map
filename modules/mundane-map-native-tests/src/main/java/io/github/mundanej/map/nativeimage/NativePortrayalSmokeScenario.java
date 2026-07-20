package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ScreenBox;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.GreedyPointLabelPlacement;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.PointLabelPlacementRequest;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resource-free portrayal and bounded point-label smoke used on the JVM and under Native Image. */
final class NativePortrayalSmokeScenario {
    private static final LabelTextStyle LABEL_STYLE =
            new LabelTextStyle(Rgba.rgb(25, 25, 25), LabelWeight.NORMAL, 12);

    private NativePortrayalSmokeScenario() {}

    static Result run() {
        int bluePixels = NativeShapefileSmokeScenario.onEdt(NativePortrayalSmokeScenario::render);
        int placedLabels = assertPlacementOrder();
        assertStableLimitDiagnostic();
        return new Result(bluePixels, placedLabels);
    }

    private static int render() {
        var blue = BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(30, 100, 220), 14, 1);
        var red = BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(210, 45, 45), 14, 1);
        CategoricalSymbolSelector selector =
                new CategoricalSymbolSelector(
                        "kind",
                        List.of(
                                new CategoricalSymbolRule(ThematicValue.text("blue"), blue),
                                new CategoricalSymbolRule(ThematicValue.text("red"), red)),
                        Optional.empty());
        Feature lower = feature("lower", "LOSER", "red");
        Feature upper = feature("upper", "WINNER", "blue");
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        BufferedImage image = new BufferedImage(180, 100, BufferedImage.TYPE_INT_ARGB);
        try {
            view.setSize(image.getWidth(), image.getHeight());
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.portrayedSnapshot(
                                    new InMemoryLayer("lower", "Lower", List.of(lower)),
                                    FeaturePortrayal.markers(selector)),
                            MapLayerBinding.portrayedSnapshot(
                                    new InMemoryLayer("upper", "Upper", List.of(upper)),
                                    FeaturePortrayal.markers(selector))));
            view.fitToData(20);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            view.close();
        }
        int bluePixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int packed = image.getRGB(x, y);
                int redChannel = packed >>> 16 & 0xff;
                int greenChannel = packed >>> 8 & 0xff;
                int blueChannel = packed & 0xff;
                if (blueChannel > redChannel + 50 && blueChannel > greenChannel + 40) {
                    bluePixels++;
                }
            }
        }
        if (bluePixels < 40) {
            throw new IllegalStateException("portrayal-render: expected selected marker ink");
        }
        return bluePixels;
    }

    private static Feature feature(String id, String name, String kind) {
        return new Feature(
                id,
                name,
                new PointGeometry(new Coordinate(0, 0)),
                Map.of("kind", kind),
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(120, 120, 120), 8, 1));
    }

    private static int assertPlacementOrder() {
        PointLabelProfile low = portrayalProfile(0);
        PointLabelProfile high = portrayalProfile(10);
        ScreenBox marker = new ScreenBox(40, 40, 50, 50);
        ScreenBox visual = new ScreenBox(0, -10, 42, 2);
        List<?> placed =
                GreedyPointLabelPlacement.place(
                        new ScreenBox(0, 0, 160, 100),
                        List.of(
                                request("lower", "LOSER", marker, visual, low, 0),
                                request("upper", "WINNER", marker, visual, high, 1)));
        if (placed.size() != 1
                || !((io.github.mundanej.map.api.PlacedPointLabel) placed.getFirst())
                        .featureId()
                        .equals("upper")) {
            throw new IllegalStateException("label-placement: priority winner changed");
        }
        return placed.size();
    }

    private static PointLabelProfile portrayalProfile(int priority) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                LABEL_STYLE,
                List.of(PointLabelPosition.NE),
                2,
                0,
                0,
                1,
                priority,
                ResolutionRange.ALL);
    }

    private static PointLabelPlacementRequest request(
            String id,
            String text,
            ScreenBox marker,
            ScreenBox visual,
            PointLabelProfile profile,
            int ordinal) {
        return new PointLabelPlacementRequest(
                "layer-" + id,
                id,
                text,
                LABEL_STYLE,
                marker,
                visual,
                42,
                profile,
                ordinal,
                0,
                ordinal);
    }

    private static void assertStableLimitDiagnostic() {
        List<PointLabelPlacementRequest> hostile =
                new AbstractList<>() {
                    @Override
                    public PointLabelPlacementRequest get(int index) {
                        throw new AssertionError("oversized input must not be traversed");
                    }

                    @Override
                    public int size() {
                        return GreedyPointLabelPlacement.MAXIMUM_REQUESTS + 1;
                    }
                };
        try {
            GreedyPointLabelPlacement.place(new ScreenBox(0, 0, 100, 100), hostile);
            throw new IllegalStateException("label-diagnostic: expected request limit failure");
        } catch (LabelPlacementException expected) {
            if (!expected.problem().code().equals("LABEL_REQUEST_LIMIT_EXCEEDED")
                    || !expected.problem()
                            .context()
                            .equals(Map.of("limit", "4096", "attempted", "4097"))) {
                throw new IllegalStateException(
                        "label-diagnostic: stable problem changed", expected);
            }
        }
    }

    record Result(int bluePixels, int placedLabels) {}
}
