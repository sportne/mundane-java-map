package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.svg.SvgSymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

final class NativeSvgSmokeScenario {
    private static final SourceIdentity ID = new SourceIdentity("native-svg", "Native SVG");

    private NativeSvgSmokeScenario() {}

    static void run() {
        Symbol symbol =
                SvgSymbols.parse(
                        ID,
                        """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                          <path d="M1 9 L5 1 L9 9 Z" fill="#24905e" fill-rule="evenodd"/>
                        </svg>
                        """
                                .getBytes(StandardCharsets.UTF_8),
                        MarkerPlacement.centeredScreen(32));
        if (symbol.role() != SymbolRole.MARKER) {
            throw new IllegalStateException("svg-native: imported role changed");
        }
        assertDtdRejected();
        renderOnEdt(symbol);
    }

    private static void assertDtdRejected() {
        try {
            SvgSymbols.parse(
                    ID,
                    "<!DOCTYPE svg><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>"
                            .getBytes(StandardCharsets.UTF_8),
                    MarkerPlacement.centeredScreen(16));
            throw new IllegalStateException("svg-native: DTD was accepted");
        } catch (SourceException expected) {
            if (!expected.terminal().code().equals("SVG_XML_INVALID")
                    || !expected.terminal().context().equals(Map.of("reason", "doctype"))) {
                throw new IllegalStateException("svg-native: DTD diagnostic changed", expected);
            }
        }
    }

    private static void render(Symbol symbol) {
        MapView view = new MapView(new WebMercatorProjection(), SymbolRendererRegistry.builtIn());
        view.setDoubleBuffered(false);
        view.setSize(96, 96);
        view.setLayers(
                List.of(
                        new InMemoryLayer(
                                "svg",
                                "SVG",
                                List.of(
                                        new Feature(
                                                "svg",
                                                "",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                symbol)))));
        view.fitToData(24);
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 96, 96);
            view.paint(graphics);
        } finally {
            graphics.dispose();
            view.close();
        }
        int colored = 0;
        for (int y = 28; y < 68; y++) {
            for (int x = 28; x < 68; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 255;
                int green = (rgb >>> 8) & 255;
                int blue = rgb & 255;
                if (green > red + 30 && green > blue + 20) {
                    colored++;
                }
            }
        }
        if (colored < 80) {
            throw new IllegalStateException("svg-native: imported marker did not render");
        }
    }

    private static void renderOnEdt(Symbol symbol) {
        if (SwingUtilities.isEventDispatchThread()) {
            render(symbol);
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            render(symbol);
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    });
        } catch (Exception exception) {
            throw new IllegalStateException("svg-native: unable to render on EDT", exception);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("svg-native: rendering failed", failure.get());
        }
    }
}
