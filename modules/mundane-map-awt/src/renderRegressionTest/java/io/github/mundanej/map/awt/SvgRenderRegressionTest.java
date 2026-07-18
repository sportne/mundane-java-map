package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.svg.SvgSymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SvgRenderRegressionTest {
    @Test
    void importedMarkerRendersWithTolerantBoundsAndColorEvidence() throws Exception {
        Symbol symbol =
                SvgSymbols.parse(
                        new SourceIdentity("render-svg", "Render SVG"),
                        """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                          <rect x="1" y="2" width="8" height="6" fill="#246ed2"/>
                        </svg>
                        """
                                .getBytes(StandardCharsets.UTF_8),
                        MarkerPlacement.centeredScreen(40));
        AtomicReference<BufferedImage> rendered = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> rendered.set(render(symbol)));
        BufferedImage image = rendered.get();
        int colored = 0;
        int minX = 96;
        int minY = 96;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 255;
                int green = (rgb >>> 8) & 255;
                int blue = rgb & 255;
                if (blue > red + 55 && blue > green + 35) {
                    colored++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(colored > 500, "expected a broad imported-blue region");
        assertTrue(minX >= 27 && minX <= 34, "unexpected minimum x: " + minX);
        assertTrue(maxX >= 61 && maxX <= 68, "unexpected maximum x: " + maxX);
        assertTrue(minY >= 34 && minY <= 39, "unexpected minimum y: " + minY);
        assertTrue(maxY >= 57 && maxY <= 63, "unexpected maximum y: " + maxY);
    }

    @Test
    void importedOrderedLayersMatchEquivalentHandBuiltSymbolsWithinTolerance() throws Exception {
        MarkerPlacement placement = MarkerPlacement.centeredScreen(40);
        Symbol imported =
                SvgSymbols.parse(
                        new SourceIdentity("render-svg-layers", "Render SVG layers"),
                        """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                          <rect x="1" y="1" width="8" height="8" fill="#c83232"/>
                          <rect x="4" y="2" width="5" height="6" fill="#3264dc"/>
                        </svg>
                        """
                                .getBytes(StandardCharsets.UTF_8),
                        placement);
        Symbol expected =
                CompositeSymbol.of(
                        List.of(
                                rectangle(1, 1, 8, 8, Rgba.rgb(200, 50, 50), placement),
                                rectangle(4, 2, 5, 6, Rgba.rgb(50, 100, 220), placement)),
                        1.0);
        AtomicReference<BufferedImage> importedImage = new AtomicReference<>();
        AtomicReference<BufferedImage> expectedImage = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    importedImage.set(render(imported));
                    expectedImage.set(render(expected));
                });

        int materiallyDifferent = 0;
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                int actual = importedImage.get().getRGB(x, y);
                int reference = expectedImage.get().getRGB(x, y);
                if (maximumChannelDifference(actual, reference) > 2) {
                    materiallyDifferent++;
                }
            }
        }
        assertTrue(
                materiallyDifferent <= 8,
                "imported and hand-built layers diverged at " + materiallyDifferent + " pixels");
        Color overlap = new Color(importedImage.get().getRGB(50, 48), true);
        assertTrue(overlap.getBlue() > overlap.getRed() + 80, "top blue layer must win overlap");
    }

    private static VectorMarkerSymbol rectangle(
            double x, double y, double width, double height, Rgba fill, MarkerPlacement placement) {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(x, y)
                        .lineTo(x + width, y)
                        .lineTo(x + width, y + height)
                        .lineTo(x, y + height)
                        .close()
                        .build();
        return VectorMarkerSymbol.of(
                path, new Envelope(0, 0, 10, 10), fill, Optional.empty(), placement, 1.0);
    }

    private static int maximumChannelDifference(int left, int right) {
        int maximum = 0;
        for (int shift : new int[] {0, 8, 16, 24}) {
            maximum =
                    Math.max(
                            maximum,
                            Math.abs(((left >>> shift) & 255) - ((right >>> shift) & 255)));
        }
        return maximum;
    }

    private static BufferedImage render(Symbol symbol) {
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
                                                "SVG",
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
            return image;
        } finally {
            graphics.dispose();
            view.close();
        }
    }
}
