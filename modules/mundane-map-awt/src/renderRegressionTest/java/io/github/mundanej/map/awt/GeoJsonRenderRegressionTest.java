package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import io.github.mundanej.map.io.svg.SvgMapExports;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GeoJsonRenderRegressionTest {
    @Test
    void explicitWorldWrapPaintsBothSidesOfTheDatelineWithoutAnOutlineSeam() throws Exception {
        byte[] document =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"area","geometry":{"type":"Polygon","coordinates":[
                    [[170,-12],[-170,-12],[-170,12],[170,12],[170,-12]]]},"properties":{}},
                  {"type":"Feature","id":"line","geometry":{"type":"LineString",
                    "coordinates":[[170,16],[-170,16]]},"properties":{}}
                ]}
                """
                        .getBytes(StandardCharsets.UTF_8);
        FeatureSource source =
                GeoJsonFiles.open(
                        document,
                        new SourceIdentity("wrapped-geojson", "Wrapped GeoJSON"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<VectorExportSnapshot> exportReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(25, 90, 190),
                                            new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                                    Optional.of(
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.ARROW,
                                                    Rgba.rgb(25, 90, 190),
                                                    12,
                                                    1)),
                                    Optional.of(
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.ARROW,
                                                    Rgba.rgb(25, 90, 190),
                                                    12,
                                                    1)),
                                    1);
                    MapLayerBinding binding =
                            MapLayerBinding.ownedFeature(
                                    "wrapped",
                                    "Wrapped",
                                    source,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.DIAMOND, Rgba.rgb(25, 90, 190), 10, 1),
                                    line,
                                    SolidFillSymbol.of(
                                            new Rgba(25, 130, 80, 190), Optional.of(line), 0.5));
                    binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(400, 240);
                    view.setViewport(
                            new MapViewport(
                                    400, 240, WebMercatorProjection.WORLD_LIMIT, 0, 12_000));
                    view.setHorizontalWrap(HorizontalWrap.webMercator());
                    view.setLayerBindings(List.of(binding));
                    BufferedImage image = new BufferedImage(400, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    imageReference.set(image);
                    viewReference.set(view);
                    exportReference.set(view.captureVectorExportSnapshot());
                });

        BufferedImage image = imageReference.get();
        assertGreenEvidence(image, 145, 125, 175, 150, "west dateline fill");
        assertGreenEvidence(image, 225, 125, 255, 150, "east dateline fill");
        Color seam = new Color(image.getRGB(200, 135), true);
        assertTrue(
                seam.getGreen() > seam.getBlue() + 10,
                "artificial seam must retain fill rather than outline ink");
        byte[] firstSvg = SvgMapExports.encode(exportReference.get());
        byte[] secondSvg = SvgMapExports.encode(exportReference.get());
        assertArrayEquals(firstSvg, secondSvg);
        String svg = new String(firstSvg, StandardCharsets.UTF_8);
        assertTrue(svg.contains("fill=\"#198250\""), "wrapped fill must survive SVG export");
        assertTrue(svg.contains("stroke=\"#195abe\""), "wrapped line must survive SVG export");
        assertTrue(
                svg.contains("stroke-opacity=\"0.5\""),
                "polygon outline must inherit parent opacity in SVG export");
        SwingUtilities.invokeAndWait(viewReference.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void sourceBackedGeometryAndHoleHavePortableColorAndTopologyEvidence() throws Exception {
        byte[] document =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"area","geometry":{"type":"Polygon","coordinates":[
                    [[-2,-2],[2,-2],[2,2],[-2,2],[-2,-2]],
                    [[-0.7,-0.7],[0.7,-0.7],[0.7,0.7],[-0.7,0.7],[-0.7,-0.7]]]},"properties":{}},
                  {"type":"Feature","id":"line","geometry":{"type":"LineString",
                    "coordinates":[[-2.5,0],[2.5,0]]},"properties":{}},
                  {"type":"Feature","id":"point","geometry":{"type":"Point","coordinates":[-2.8,2.8]},"properties":{}}
                  ,{"type":"Feature","id":"multipoint","geometry":{"type":"MultiPoint",
                    "coordinates":[[-3,1],[-3,-1]]},"properties":{}}
                  ,{"type":"Feature","id":"multiline","geometry":{"type":"MultiLineString",
                    "coordinates":[[[3,-2],[4,-1]],[[3,2],[4,1]]]},"properties":{}}
                  ,{"type":"Feature","id":"multipolygon","geometry":{"type":"MultiPolygon",
                    "coordinates":[[[[3,-0.5],[4,-0.5],[4,0.5],[3,0.5],[3,-0.5]]]]},
                    "properties":{}}
                ]}
                """
                        .getBytes(StandardCharsets.UTF_8);
        FeatureSource source =
                GeoJsonFiles.open(
                        document,
                        new SourceIdentity("render-geojson", "Render GeoJSON"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_3857);
                    SolidLineSymbol line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(25, 90, 190),
                                            new SymbolLength(3, SymbolUnit.SCREEN_PIXEL)),
                                    1);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedFeature(
                                            "geojson",
                                            "GeoJSON",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.DIAMOND,
                                                    Rgba.rgb(25, 90, 190),
                                                    14,
                                                    1),
                                            line,
                                            SolidFillSymbol.of(
                                                    new Rgba(25, 90, 190, 100),
                                                    Optional.of(line),
                                                    1))));
                    view.setSize(320, 240);
                    view.fitToData(28);
                    BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    viewReference.set(view);
                    imageReference.set(image);
                });

        MapView view = viewReference.get();
        BufferedImage image = imageReference.get();
        Coordinate hole = view.mapToScreen(new Coordinate(0, 0.4)).orElseThrow();
        Coordinate fill = view.mapToScreen(new Coordinate(1.4, 1.2)).orElseThrow();
        Color holeColor = new Color(image.getRGB((int) hole.x(), (int) hole.y()), true);
        Color fillColor = new Color(image.getRGB((int) fill.x(), (int) fill.y()), true);
        assertTrue(holeColor.getRed() > 245 && holeColor.getGreen() > 245);
        assertTrue(fillColor.getBlue() > fillColor.getRed() + 15);
        assertBlueEvidence(image, view, new Coordinate(-2.8, 2.8), 8, "Point");
        assertBlueEvidence(image, view, new Coordinate(-3, 1), 8, "MultiPoint");
        assertBlueEvidence(image, view, new Coordinate(-2.3, 0), 6, "LineString");
        assertBlueEvidence(image, view, new Coordinate(3.5, -1.5), 6, "MultiLineString");
        assertBlueEvidence(image, view, new Coordinate(1.4, 1.2), 4, "Polygon");
        assertBlueEvidence(image, view, new Coordinate(3.5, 0), 4, "MultiPolygon");
        SwingUtilities.invokeAndWait(view::close);
        assertTrue(source.isClosed());
    }

    private static void assertBlueEvidence(
            BufferedImage image, MapView view, Coordinate map, int radius, String family) {
        Coordinate screen = view.mapToScreen(map).orElseThrow();
        int colored = 0;
        for (int y = Math.max(0, (int) screen.y() - radius);
                y <= Math.min(image.getHeight() - 1, (int) screen.y() + radius);
                y++) {
            for (int x = Math.max(0, (int) screen.x() - radius);
                    x <= Math.min(image.getWidth() - 1, (int) screen.x() + radius);
                    x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getBlue() > color.getRed() + 15) {
                    colored++;
                }
            }
        }
        assertTrue(colored > 2, family + " produced no tolerant blue evidence");
    }

    private static void assertGreenEvidence(
            BufferedImage image,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY,
            String family) {
        int colored = 0;
        for (int y = minimumY; y < maximumY; y++) {
            for (int x = minimumX; x < maximumX; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getGreen() > color.getRed() + 15
                        && color.getGreen() > color.getBlue() + 10) {
                    colored++;
                }
            }
        }
        assertTrue(colored > 20, family + " produced no tolerant green evidence");
    }
}
