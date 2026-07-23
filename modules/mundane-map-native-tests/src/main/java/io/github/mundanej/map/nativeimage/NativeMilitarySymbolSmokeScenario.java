package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolException;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolResolution;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

final class NativeMilitarySymbolSmokeScenario {
    static final String SUPPORTED = "150310000012110025020030000000";
    static final String DEGRADED_ENTITY = "150310000099999900000030000000";
    static final String UNSUPPORTED_CONTEXT = "15F310000012110000000030000000";
    static final String MALFORMED = "15031000001211000000003000000Z";

    private NativeMilitarySymbolSmokeScenario() {}

    static Result run() {
        MarkerPlacement placement = MarkerPlacement.centeredScreen(54);
        MilitarySymbolId id = MilitarySymbolId.parse(SUPPORTED);
        Symbol direct =
                MilitarySymbols.resolveStrict(
                        id, placement, MilitarySymbolPalette.lightBackground());
        FeaturePortrayal portrayal =
                MilitarySymbolCatalog.portrayal(
                        "sidc", placement, MilitarySymbolPalette.lightBackground(), 1);
        Feature feature =
                new Feature(
                        "native-military",
                        "",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of("sidc", SUPPORTED),
                        direct);
        RenderEvidence supported = render(feature, portrayal);
        if (supported.coloredPixels() < 700) {
            throw new IllegalStateException("milstd2525-native: portrayed symbol did not render");
        }

        MilitarySymbolResolution degraded =
                MilitarySymbols.resolveDegraded(
                        MilitarySymbolId.parse(DEGRADED_ENTITY),
                        placement,
                        MilitarySymbolPalette.lightBackground());
        String degradedCode = degraded.problem().orElseThrow().code();
        if (!"MIL2525_ENTITY_UNSUPPORTED".equals(degradedCode)) {
            throw new IllegalStateException("milstd2525-native: degraded diagnostic changed");
        }
        RenderEvidence fallback = render(degraded.symbol());
        if (fallback.coloredPixels() < 700 || fallback.digest() == supported.digest()) {
            throw new IllegalStateException(
                    "milstd2525-native: degraded frame fallback did not render distinctly");
        }
        String malformedCode = failureCode(() -> MilitarySymbolId.parse(MALFORMED));
        String unsupportedCode =
                failureCode(
                        () ->
                                MilitarySymbols.resolveStrict(
                                        MilitarySymbolId.parse(UNSUPPORTED_CONTEXT),
                                        placement,
                                        MilitarySymbolPalette.lightBackground()));
        if (!"MIL2525_SIDC_CHARACTER".equals(malformedCode)
                || !"MIL2525_CONTEXT_UNSUPPORTED".equals(unsupportedCode)) {
            throw new IllegalStateException("milstd2525-native: failure diagnostics changed");
        }
        return new Result(
                id.canonical(),
                supported.coloredPixels(),
                fallback.coloredPixels(),
                degradedCode,
                malformedCode,
                unsupportedCode);
    }

    private static RenderEvidence render(Feature feature, FeaturePortrayal portrayal) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setSize(120, 120);
        view.setViewport(new MapViewport(120, 120, 0, 0, 1));
        view.setLayerBindings(
                List.of(
                        MapLayerBinding.portrayedSnapshot(
                                new InMemoryLayer(
                                        "native-military", "Native military", List.of(feature)),
                                portrayal)));
        return paint(view);
    }

    private static RenderEvidence render(Symbol symbol) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setSize(120, 120);
        view.setViewport(new MapViewport(120, 120, 0, 0, 1));
        view.setLayers(
                List.of(
                        new InMemoryLayer(
                                "native-military-fallback",
                                "Native military fallback",
                                List.of(
                                        new Feature(
                                                "native-military-fallback",
                                                "",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                symbol)))));
        return paint(view);
    }

    private static RenderEvidence paint(MapView view) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            view.paint(graphics);
        } finally {
            graphics.dispose();
            view.close();
        }
        int colored = 0;
        int digest = 1;
        for (int y = 20; y < 100; y++) {
            for (int x = 20; x < 100; x++) {
                int pixel = image.getRGB(x, y);
                digest = 31 * digest + pixel;
                if ((pixel & 0x00ffffff) != 0x00ffffff) {
                    colored++;
                }
            }
        }
        return new RenderEvidence(colored, digest);
    }

    private static String failureCode(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("milstd2525-native: expected failure was accepted");
        } catch (MilitarySymbolException expected) {
            return expected.problem().code();
        }
    }

    record Result(
            String canonical,
            int coloredPixels,
            int degradedColoredPixels,
            String degradedCode,
            String malformedCode,
            String unsupportedCode) {}

    private record RenderEvidence(int coloredPixels, int digest) {}
}
