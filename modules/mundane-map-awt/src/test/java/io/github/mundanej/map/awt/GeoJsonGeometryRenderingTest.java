package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GeoJsonGeometryRenderingTest {
    @Test
    void rendersLineMultipartAndPolygonHoleThroughTheSourceStack() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureSource source = openGeometryCollection();
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    view.setSize(160, 120);
                    view.setViewport(new MapViewport(160, 120, 0, 0, 1));
                    SolidLineSymbol line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 70, 210),
                                            new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                                    1);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedFeature(
                                            "geojson",
                                            "GeoJSON",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.CIRCLE,
                                                    Rgba.rgb(20, 70, 210),
                                                    7,
                                                    1),
                                            line,
                                            SolidFillSymbol.of(Rgba.rgb(30, 170, 70), 1))));

                    BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(Color.WHITE.getRGB(), image.getRGB(80, 60));
                    assertTrue(isGreen(image.getRGB(90, 60)));
                    assertTrue(hasBlueNear(image, 20, 25, 5));
                    assertTrue(hasBlueNear(image, 140, 25, 5));
                    assertTrue(hasBlueNear(image, 140, 35, 5));
                    assertTrue(countNonWhite(image) > 1_000);
                    assertTrue(countBlue(image) > 150);
                    view.close();
                    assertTrue(source.isClosed());
                });
    }

    private static FeatureSource openGeometryCollection() {
        String document =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","geometry":{
                    "type":"Point","coordinates":[-60,35]},"properties":null},
                  {"type":"Feature","geometry":{
                    "type":"MultiPoint","coordinates":[[60,35],[60,25]]},"properties":null},
                  {"type":"Feature","geometry":{
                    "type":"LineString","coordinates":[[-30,25],[30,25]]},"properties":null},
                  {"type":"Feature","geometry":{
                    "type":"MultiLineString","coordinates":[
                      [[-30,-25],[-5,-25]],[[5,-25],[30,-25]]]},"properties":null},
                  {"type":"Feature","geometry":{"type":"Polygon","coordinates":[
                    [[-20,-20],[20,-20],[20,20],[-20,20],[-20,-20]],
                    [[-5,-5],[-5,5],[5,5],[5,-5],[-5,-5]]]},"properties":null},
                  {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[
                    [[[-55,-12],[-35,-12],[-35,8],[-55,8],[-55,-12]]],
                    [[[35,-12],[55,-12],[55,8],[35,8],[35,-12]]]]},"properties":null}
                ]}
                """;
        return GeoJsonFiles.open(
                document.getBytes(StandardCharsets.UTF_8),
                new SourceIdentity("geojson-render", "GeoJSON render"),
                GeoJsonOpenOptions.defaults(),
                CancellationToken.none());
    }

    private static int countNonWhite(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasBlueNear(BufferedImage image, int centerX, int centerY, int radius) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (isBlue(image.getRGB(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countBlue(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (isBlue(rgb)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isBlue(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        return blue > red + 80 && blue > green + 50;
    }

    private static boolean isGreen(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        return green > red + 70 && green > blue + 50;
    }
}
