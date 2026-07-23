package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.se.SeFeatureStyle;
import io.github.mundanej.map.io.se.SeReadException;
import io.github.mundanej.map.io.se.SeReadOptions;
import io.github.mundanej.map.io.se.SeStyles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Shared secure SE parse, evaluation, catalog, and rendering smoke. */
final class NativeSeSmokeScenario {
    static final String STYLE_RESOURCE = "/io/github/mundanej/map/nativeimage/se/native-style.xml";

    private static final NamedSymbolCatalog CATALOG =
            NamedSymbolCatalog.of(
                    List.of(
                            new NamedSymbol(
                                    "native.primary",
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.DIAMOND,
                                            Rgba.rgb(35, 105, 215),
                                            28,
                                            1))));

    private NativeSeSmokeScenario() {}

    static Result run() {
        SeFeatureStyle style =
                SeStyles.read("native-style", loadStyle(), CATALOG, SeReadOptions.defaults());
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(style.portrayal());
        if (!resolver.requiredSymbolAttributes().equals(List.of("kind"))
                || resolver.resolveAll(
                                Map.of("kind", "primary"), PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .isEmpty()
                || resolver.resolveAll(
                                Map.of("kind", "secondary"), PortrayalEvaluationContext.UNSCALED)
                        .marker()
                        .isPresent()) {
            throw new IllegalStateException("se-native: rule evaluation changed");
        }
        int bluePixels = NativeShapefileSmokeScenario.onEdt(() -> render(style));
        if (bluePixels < 80) {
            throw new IllegalStateException("se-native: catalog marker did not render");
        }
        String securityCode = assertSecurityDiagnostic();
        return new Result(bluePixels, securityCode);
    }

    private static byte[] loadStyle() {
        try (InputStream input = NativeSeSmokeScenario.class.getResourceAsStream(STYLE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("se-native: declared style resource is missing");
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("se-native: unable to read style resource", failure);
        }
    }

    @SuppressWarnings("deprecation")
    private static int render(SeFeatureStyle style) {
        Feature feature =
                new Feature(
                        "native-se",
                        "Native SE",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of("kind", "primary"),
                        FeatureStyle.point(Rgba.rgb(120, 120, 120), 6));
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        try {
            view.setSize(image.getWidth(), image.getHeight());
            view.setViewport(new MapViewport(image.getWidth(), image.getHeight(), 0, 0, 1));
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.portrayedSnapshot(
                                    new InMemoryLayer("native-se", "Native SE", List.of(feature)),
                                    style.portrayal())));
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
        for (int y = 30; y < 90; y++) {
            for (int x = 30; x < 90; x++) {
                int packed = image.getRGB(x, y);
                int red = packed >>> 16 & 0xff;
                int green = packed >>> 8 & 0xff;
                int blue = packed & 0xff;
                if (blue > red + 60 && blue > green + 40) {
                    bluePixels++;
                }
            }
        }
        return bluePixels;
    }

    private static String assertSecurityDiagnostic() {
        byte[] hostile =
                ("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e \"x\">]>"
                                + "<se:FeatureTypeStyle xmlns:se=\"http://www.opengis.net/se\">"
                                + "&e;</se:FeatureTypeStyle>")
                        .getBytes(StandardCharsets.UTF_8);
        try {
            SeStyles.read("native-hostile", hostile, CATALOG, SeReadOptions.defaults());
            throw new IllegalStateException("se-native: hostile XML was accepted");
        } catch (SeReadException expected) {
            if (!expected.problem().code().equals("SE_XML_SECURITY")) {
                throw new IllegalStateException(
                        "se-native: XML security diagnostic changed", expected);
            }
            return expected.problem().code();
        }
    }

    record Result(int bluePixels, String securityCode) {}
}
