package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.PackedElevationGrid;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class RenderRegressionTest {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;
    private static final Rgba BACKGROUND = Rgba.rgb(255, 255, 255);
    private static final Rgba BLUE = Rgba.rgb(35, 105, 205);
    private static final Rgba RED = Rgba.rgb(185, 45, 45);
    private static final Rgba GREEN = Rgba.rgb(45, 145, 80);
    private static final Rgba YELLOW = Rgba.rgb(235, 175, 35);
    private static final Rgba DARK = Rgba.rgb(45, 50, 60);
    private static final Rgba RED_RGB = Rgba.rgb(255, 0, 0);
    private static final Rgba GREEN_RGB = Rgba.rgb(0, 255, 0);
    private static final Rgba BLUE_RGB = Rgba.rgb(0, 0, 255);
    private static final Rgba YELLOW_RGB = Rgba.rgb(255, 255, 0);
    private static final Rgba ALPHA_MAGENTA_OVER_WHITE = Rgba.rgb(255, 127, 255);
    private static final Rgba RED_OVER_WHITE = Rgba.rgb(220, 150, 150);
    private static final RenderTolerance TOLERANCE =
            new RenderTolerance(18, 24, 2, 0.70, 0.02, 0.08, 0.65);

    @Test
    void environmentAndToleranceProfileArePortableAndUnambiguous() {
        assertTrue(GraphicsEnvironment.isHeadless(), "environment: expected headless AWT");
        List<Rgba> palette =
                List.of(
                        BACKGROUND,
                        BLUE,
                        RED,
                        GREEN,
                        YELLOW,
                        DARK,
                        RED_RGB,
                        GREEN_RGB,
                        BLUE_RGB,
                        YELLOW_RGB,
                        ALPHA_MAGENTA_OVER_WHITE,
                        RED_OVER_WHITE);
        int minimumDistance = Integer.MAX_VALUE;
        for (int first = 0; first < palette.size(); first++) {
            for (int second = first + 1; second < palette.size(); second++) {
                minimumDistance =
                        Math.min(
                                minimumDistance,
                                maximumChannelDistance(palette.get(first), palette.get(second)));
            }
        }
        assertTrue(
                minimumDistance >= 64,
                "tolerance-profile: every classified fixture color must be at least 64 apart");
        assertTrue(
                2 * TOLERANCE.colorTolerance() < minimumDistance,
                "tolerance-profile: expected disjoint palette classifications");
    }

    @TestFactory
    Stream<DynamicTest> committedRenderingFixturesHaveIndependentEvidence() {
        return scenarios()
                .map(
                        scenario ->
                                DynamicTest.dynamicTest(
                                        scenario.id() + " — ordered rendering invariants",
                                        () -> run(scenario)));
    }

    private static Stream<RenderScenario> scenarios() {
        return Stream.of(
                        markerScenarios(),
                        placementScenarios(),
                        compositionAndUnitScenarios(),
                        rasterScenarios(),
                        rasterLayerScenarios(),
                        elevationScenarios(),
                        lineScenarios(),
                        polygonAndHatchScenarios())
                .flatMap(stream -> stream);
    }

    private static Stream<RenderScenario> elevationScenarios() {
        return Stream.of(
                new RenderScenario(
                        "elevation-ramp-domain-orientation",
                        WIDTH,
                        HEIGHT,
                        () -> {
                            ElevationSourceMetadata metadata =
                                    new ElevationSourceMetadata(
                                            new SourceIdentity("render-elevation", "Elevation"),
                                            3,
                                            3,
                                            new Envelope(0, 0, 2, 2),
                                            CrsMetadata.recognized(
                                                    CrsDefinitions.EPSG_3857,
                                                    Optional.of("EPSG:3857"),
                                                    Optional.empty()),
                                            ElevationUnit.METRE);
                            PackedElevationGrid source =
                                    PackedElevationGrid.copyOf(
                                            metadata,
                                            new double[] {0, 0, 0, 4, 4, 4, 8, 8, 8},
                                            new BitSet());
                            ElevationRasterStyle style =
                                    ElevationRasterStyle.of(
                                            new ElevationColorRamp(
                                                    ElevationUnit.METRE,
                                                    List.of(
                                                            new ElevationColorStop(0, RED_RGB),
                                                            new ElevationColorStop(8, BLUE_RGB))));
                            MapView view = view(List.of());
                            view.setLayerBindings(
                                    List.of(
                                            MapLayerBinding.borrowedElevation(
                                                    "terrain", "Terrain", source, style)));
                            return view;
                        },
                        view -> view.setViewport(new MapViewport(WIDTH, HEIGHT, 1, 1, 0.025)),
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "north-is-low-elevation-red",
                                                image,
                                                new Region(48, 24, 12, 12),
                                                RED_RGB),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "south-is-high-elevation-blue",
                                                image,
                                                new Region(100, 84, 12, 12),
                                                BLUE_RGB),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "outside-sample-domain-west",
                                                image,
                                                new Region(18, 48, 10, 20),
                                                BACKGROUND),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "outside-sample-domain-east",
                                                image,
                                                new Region(132, 48, 10, 20),
                                                BACKGROUND))),
                new RenderScenario(
                        "elevation-hillshade-direction-opacity",
                        WIDTH,
                        HEIGHT,
                        () -> {
                            ElevationSourceMetadata metadata =
                                    new ElevationSourceMetadata(
                                            new SourceIdentity("render-hillshade", "Hillshade"),
                                            5,
                                            3,
                                            new Envelope(0, 0, 4, 2),
                                            CrsMetadata.recognized(
                                                    CrsDefinitions.EPSG_3857,
                                                    Optional.of("EPSG:3857"),
                                                    Optional.empty()),
                                            ElevationUnit.METRE);
                            PackedElevationGrid source =
                                    PackedElevationGrid.copyOf(
                                            metadata,
                                            new double[] {
                                                0, 10, 20, 10, 0,
                                                0, 10, 20, 10, 0,
                                                0, 10, 20, 10, 0
                                            },
                                            new BitSet());
                            ElevationRasterStyle style =
                                    ElevationRasterStyle.of(
                                                    new ElevationColorRamp(
                                                            ElevationUnit.METRE,
                                                            List.of(
                                                                    new ElevationColorStop(
                                                                            0,
                                                                            Rgba.rgb(
                                                                                    200, 200, 200)),
                                                                    new ElevationColorStop(
                                                                            20,
                                                                            Rgba.rgb(
                                                                                    200, 200,
                                                                                    200)))))
                                            .withHillshade(new ElevationHillshade(270, 45, 1));
                            MapView view = view(List.of());
                            view.setLayerBindings(
                                    List.of(
                                            MapLayerBinding.borrowedElevation(
                                                    "terrain", "Terrain", source, style)));
                            view.setRasterRenderOptions(
                                    "terrain",
                                    new RasterRenderOptions(RasterInterpolation.NEAREST, 0.5));
                            return view;
                        },
                        view -> view.setViewport(new MapViewport(WIDTH, HEIGHT, 2, 1, 0.04)),
                        Optional.empty(),
                        List.of(
                                (id, image) -> {
                                    int west = averageBrightness(image, new Region(44, 48, 12, 20));
                                    int east =
                                            averageBrightness(image, new Region(104, 48, 12, 20));
                                    assertTrue(
                                            west >= east + 20,
                                            id
                                                    + ": west-facing slope should be visibly lighter; "
                                                    + west
                                                    + " versus "
                                                    + east);
                                },
                                (id, image) ->
                                        requireBetween(
                                                id,
                                                "half-opacity-dark-slope",
                                                averageBrightness(
                                                        image, new Region(104, 48, 12, 20)),
                                                120,
                                                245))));
    }

    private static Stream<RenderScenario> markerScenarios() {
        return Stream.of(BuiltInMarker.values())
                .map(
                        marker -> {
                            String id = "vector-" + marker.name().toLowerCase(Locale.ROOT);
                            return scenario(
                                    id,
                                    List.of(
                                            point(
                                                    id,
                                                    BuiltInMarkers.filledScreen(
                                                            marker, BLUE, 40, 1),
                                                    new Coordinate(0, 0))),
                                    1,
                                    Optional.empty(),
                                    List.of(
                                            (scenario, image) -> {
                                                int[] bounds = paintedBounds(image);
                                                requireBetween(
                                                        scenario,
                                                        "width",
                                                        bounds[2] - bounds[0] + 1,
                                                        30,
                                                        44);
                                                requireBetween(
                                                        scenario,
                                                        "height",
                                                        bounds[3] - bounds[1] + 1,
                                                        30,
                                                        44);
                                            },
                                            (scenario, image) ->
                                                    requireMatching(
                                                            scenario,
                                                            "center",
                                                            image,
                                                            new Region(77, 57, 7, 7),
                                                            BLUE),
                                            (scenario, image) ->
                                                    verifyMarkerSilhouette(
                                                            marker, scenario, image)));
                        });
    }

    private static Stream<RenderScenario> placementScenarios() {
        Stream.Builder<RenderScenario> scenarios = Stream.builder();
        for (SymbolAnchor anchor : SymbolAnchor.values()) {
            double[] fractions = anchorFractions(anchor);
            int minimumX = (int) StrictMath.round(80 - fractions[0] * 30);
            int minimumY = (int) StrictMath.round(60 - fractions[1] * 18);
            MarkerPlacement placement =
                    new MarkerPlacement(
                            new SymbolSize(30, 18, SymbolUnit.SCREEN_PIXEL),
                            anchor,
                            0,
                            0,
                            0,
                            SymbolRotationMode.SCREEN_RELATIVE);
            scenarios.add(
                    placementScenario(
                            "placement-anchor-"
                                    + anchor.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                            placement,
                            minimumX,
                            minimumY,
                            minimumX + 30,
                            minimumY + 18));
        }
        scenarios.add(
                placementScenario(
                        "placement-offset-positive",
                        new MarkerPlacement(
                                new SymbolSize(30, 18, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                12,
                                8,
                                0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        77,
                        59,
                        107,
                        77));
        scenarios.add(
                placementScenario(
                        "placement-offset-negative",
                        new MarkerPlacement(
                                new SymbolSize(30, 18, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                -12,
                                -8,
                                0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        53,
                        43,
                        83,
                        61));
        scenarios.add(
                placementScenario(
                        "placement-rotation-screen-relative",
                        rotatedPlacement(SymbolRotationMode.SCREEN_RELATIVE),
                        71,
                        45,
                        89,
                        75));
        scenarios.add(
                placementScenario(
                        "placement-rotation-map-relative",
                        rotatedPlacement(SymbolRotationMode.MAP_RELATIVE),
                        71,
                        45,
                        89,
                        75));
        return scenarios.build();
    }

    private static MarkerPlacement rotatedPlacement(SymbolRotationMode mode) {
        return new MarkerPlacement(
                new SymbolSize(30, 18, SymbolUnit.SCREEN_PIXEL),
                SymbolAnchor.CENTER,
                0,
                0,
                90,
                mode);
    }

    private static RenderScenario placementScenario(
            String id,
            MarkerPlacement placement,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
        return scenario(
                id,
                List.of(point(id, square(BLUE, placement, 1), new Coordinate(0, 0))),
                1,
                Optional.empty(),
                List.of(
                        (scenario, image) ->
                                requireBoundsNear(
                                        scenario, image, minimumX, minimumY, maximumX, maximumY)));
    }

    private static Stream<RenderScenario> compositionAndUnitScenarios() {
        MarkerPlacement northWest =
                new MarkerPlacement(
                        new SymbolSize(30, 18, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.NORTH_WEST,
                        12,
                        8,
                        90,
                        SymbolRotationMode.SCREEN_RELATIVE);
        Symbol composite =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, RED, 42, 0.5),
                                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, YELLOW, 22, 1)),
                        1);
        RenderScenario composition =
                scenario(
                        "placement-composition-opacity",
                        List.of(
                                point("placed", square(BLUE, northWest, 1), new Coordinate(-35, 0)),
                                point("composite", composite, new Coordinate(35, 0))),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "offset-rotation",
                                                image,
                                                new Region(48, 68, 9, 9),
                                                BLUE),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "top-child",
                                                image,
                                                new Region(111, 56, 9, 9),
                                                YELLOW),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "bottom-child-opacity",
                                                image,
                                                new Region(125, 70, 7, 7),
                                                RED_OVER_WHITE)));

        Feature screen =
                point(
                        "screen-unit",
                        square(BLUE, MarkerPlacement.centeredScreen(30), 1),
                        new Coordinate(0, 0));
        Feature map =
                point(
                        "map-unit",
                        square(
                                GREEN,
                                new MarkerPlacement(
                                        SymbolSize.square(30, SymbolUnit.MAP_UNIT),
                                        SymbolAnchor.CENTER,
                                        0,
                                        0,
                                        0,
                                        SymbolRotationMode.MAP_RELATIVE),
                                1),
                        new Coordinate(0, 0));
        return Stream.of(
                composition,
                widthScenario("screen-unit-scale-one", screen, 1, 30),
                widthScenario("screen-unit-scale-two", screen, 2, 30),
                widthScenario("map-unit-scale-one", map, 1, 30),
                widthScenario("map-unit-scale-two", map, 2, 15));
    }

    private static RenderScenario widthScenario(
            String id, Feature feature, double worldUnitsPerPixel, int expectedWidth) {
        return scenario(
                id,
                List.of(feature),
                worldUnitsPerPixel,
                Optional.empty(),
                List.of(
                        (scenario, image) ->
                                requireBetween(
                                        scenario,
                                        "painted-width",
                                        paintedWidth(image),
                                        expectedWidth - TOLERANCE.boundsMargin(),
                                        expectedWidth + TOLERANCE.boundsMargin())));
    }

    private static Stream<RenderScenario> rasterScenarios() {
        int[] pixels = {
            0xff0000ff, 0x00ff00ff, 0x0000ffff, 0xffffff00,
            0xffff00ff, 0x00ffffff, 0xff00ff80, 0x000000ff
        };
        MarkerPlacement placement =
                new MarkerPlacement(
                        new SymbolSize(64, 32, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        RasterIconSymbol nearest =
                RasterIconSymbol.of(4, 2, pixels, placement, RasterInterpolation.NEAREST, 1);
        RasterIconSymbol bilinear =
                RasterIconSymbol.of(4, 2, pixels, placement, RasterInterpolation.BILINEAR, 1);
        RenderScenario nearestScenario =
                scenario(
                        "raster-nearest",
                        List.of(point("nearest", nearest, new Coordinate(0, 0))),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "top-red",
                                                image,
                                                new Region(52, 48, 9, 9),
                                                RED_RGB),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "bottom-yellow",
                                                image,
                                                new Region(52, 64, 9, 9),
                                                YELLOW_RGB),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "transparent-white",
                                                image,
                                                new Region(100, 48, 9, 9),
                                                BACKGROUND),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "alpha-magenta",
                                                image,
                                                new Region(84, 64, 9, 9),
                                                ALPHA_MAGENTA_OVER_WHITE)));
        RenderScenario bilinearScenario =
                scenario(
                        "raster-bilinear",
                        List.of(point("bilinear", bilinear, new Coordinate(0, 0))),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) -> {
                                    int blended = 0;
                                    for (int y = 48; y < 72; y++) {
                                        for (int x = 60; x < 100; x++) {
                                            Rgba value = rgba(image.getRGB(x, y));
                                            if (!matches(value, RED_RGB)
                                                    && !matches(value, GREEN_RGB)
                                                    && !matches(value, BLUE_RGB)
                                                    && !matches(value, YELLOW_RGB)
                                                    && !matches(value, BACKGROUND)) {
                                                blended++;
                                            }
                                        }
                                    }
                                    if (blended <= 80) {
                                        throw new AssertionError(
                                                id
                                                        + ": blended-transition expected more than "
                                                        + "80 blended pixels, actual="
                                                        + blended);
                                    }
                                }));
        return Stream.of(nearestScenario, bilinearScenario);
    }

    private static Stream<RenderScenario> rasterLayerScenarios() {
        RenderScenario opacity =
                new RenderScenario(
                        "raster-layer-bilinear-opacity",
                        WIDTH,
                        HEIGHT,
                        () -> {
                            MapView view = view(List.of());
                            RasterSource source = new SolidRegressionRasterSource();
                            view.setLayerBindings(
                                    List.of(
                                            MapLayerBinding.borrowedRaster(
                                                    "raster",
                                                    "raster",
                                                    source,
                                                    new RasterRenderOptions(
                                                            RasterInterpolation.BILINEAR, 0.5))));
                            return view;
                        },
                        view -> view.setViewport(viewport(1)),
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "single-src-over",
                                                image,
                                                new Region(65, 45, 30, 30),
                                                Rgba.rgb(255, 127, 127)),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "outside-bounds",
                                                image,
                                                new Region(10, 10, 15, 15),
                                                BACKGROUND)));
        RasterAffineTransform transform = RasterAffineTransform.of(2, 0.5, 1, -2, 0, 0);
        Coordinate affineCenter = transform.gridToMap(20, 20);
        RenderScenario affine =
                new RenderScenario(
                        "raster-layer-affine-density-final-nearest",
                        80,
                        60,
                        () -> {
                            MapView view = view(List.of());
                            view.setLayerBindings(
                                    List.of(
                                            MapLayerBinding.borrowedRaster(
                                                    "affine",
                                                    "affine",
                                                    new AffineRegressionRasterSource(transform),
                                                    new RasterRenderOptions(
                                                            RasterInterpolation.BILINEAR, 1))));
                            return view;
                        },
                        view ->
                                view.setViewport(
                                        new MapViewport(
                                                80, 60, affineCenter.x(), affineCenter.y(), 3)),
                        Optional.empty(),
                        List.of(
                                (id, image) -> {
                                    int red = 0;
                                    int blue = 0;
                                    int white = 0;
                                    for (int row = 0; row < image.getHeight(); row++) {
                                        for (int column = 0; column < image.getWidth(); column++) {
                                            int argb = image.getRGB(column, row);
                                            if (argb == Color.RED.getRGB()) {
                                                red++;
                                            } else if (argb == Color.BLUE.getRGB()) {
                                                blue++;
                                            } else if (argb == Color.WHITE.getRGB()) {
                                                white++;
                                            } else {
                                                throw new AssertionError(
                                                        id
                                                                + ": final draw filtered pixel="
                                                                + Integer.toHexString(argb));
                                            }
                                        }
                                    }
                                    requireBetween(id, "red", red, 300, 3_000);
                                    requireBetween(id, "blue", blue, 300, 3_000);
                                    requireBetween(id, "background", white, 100, 4_000);
                                }));
        double period = 2 * WebMercatorProjection.WORLD_LIMIT;
        RenderScenario wrapped =
                new RenderScenario(
                        "raster-layer-wrapped-seam-and-copies",
                        240,
                        80,
                        () -> {
                            MapView view = view(List.of());
                            view.setHorizontalWrap(HorizontalWrap.webMercator());
                            MapLayerBinding binding =
                                    MapLayerBinding.borrowedRaster(
                                            "wrapped-raster",
                                            "wrapped-raster",
                                            new WrappedRegressionRasterSource(),
                                            new RasterRenderOptions(
                                                    RasterInterpolation.BILINEAR, 0.5));
                            binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
                            view.setLayerBindings(List.of(binding));
                            return view;
                        },
                        view ->
                                view.setViewport(
                                        new MapViewport(
                                                240,
                                                80,
                                                WebMercatorProjection.WORLD_LIMIT,
                                                0,
                                                period * 1.5 / 240)),
                        Optional.empty(),
                        List.of(
                                (id, image) -> {
                                    int painted = 0;
                                    for (int column = 0; column < image.getWidth(); column++) {
                                        int rgb = image.getRGB(column, image.getHeight() / 2);
                                        if (rgb != Color.WHITE.getRGB()) {
                                            painted++;
                                        }
                                    }
                                    requireBetween(id, "continuous-center-row", painted, 238, 240);
                                    requireMatching(
                                            id,
                                            "first-green-copy",
                                            image,
                                            new Region(10, 20, 20, 40),
                                            Rgba.rgb(127, 255, 127));
                                    requireMatching(
                                            id,
                                            "repeated-green-copy",
                                            image,
                                            new Region(170, 20, 20, 40),
                                            Rgba.rgb(127, 255, 127));
                                    requireMatching(
                                            id,
                                            "seam-west-copy",
                                            image,
                                            new Region(125, 20, 20, 40),
                                            Rgba.rgb(255, 127, 127));
                                }));
        return Stream.of(opacity, affine, wrapped);
    }

    private static Stream<RenderScenario> lineScenarios() {
        VectorMarkerSymbol start = BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, BLUE, 16, 1);
        Symbol line =
                CompositeSymbol.of(
                        List.of(
                                SolidLineSymbol.of(stroke(DARK, 10), 1),
                                SolidLineSymbol.of(
                                        stroke(YELLOW, 6),
                                        Optional.of(start),
                                        Optional.of(endpointArrow()),
                                        1)),
                        1);
        RenderScenario horizontal =
                scenario(
                        "line-horizontal-endpoints",
                        List.of(line("horizontal", line, -65, 30, -15, 30)),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "casing-center",
                                                image,
                                                new Region(32, 27, 9, 7),
                                                YELLOW),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "start-marker",
                                                image,
                                                new Region(11, 27, 7, 7),
                                                BLUE),
                                (id, image) ->
                                        requirePainted(
                                                id,
                                                "end-arrow-attachment",
                                                image,
                                                new Region(61, 23, 13, 15)),
                                (id, image) ->
                                        requireDirectionalColor(
                                                id,
                                                "end-arrow-direction",
                                                image,
                                                new Region(53, 27, 7, 7),
                                                new Region(68, 27, 7, 7),
                                                RED)));
        RenderScenario rising =
                lineDirectionScenario(
                        "line-rising-endpoint",
                        line("rising", line, -25, -20, 25, 10),
                        new Region(97, 43, 13, 13),
                        new Region(92, 54, 7, 7),
                        new Region(108, 40, 7, 7));
        RenderScenario falling =
                lineDirectionScenario(
                        "line-falling-endpoint",
                        line("falling", line, 15, 30, 65, 0),
                        new Region(140, 52, 13, 13),
                        new Region(132, 48, 7, 7),
                        new Region(149, 64, 7, 7));
        RenderScenario clippedAndSimplified =
                scenario(
                        "line-clipped-simplified",
                        List.of(
                                new Feature(
                                        "clipped",
                                        "",
                                        new LineStringGeometry(
                                                CoordinateSequence.of(
                                                        -200, 0, -60, 0.1, 0, -0.1, 60, 0.1, 200,
                                                        0)),
                                        Map.of(),
                                        SolidLineSymbol.of(stroke(GREEN, 6), 1))),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "visible-centerline",
                                                image,
                                                new Region(4, 57, 152, 7),
                                                GREEN),
                                (id, image) ->
                                        requireExcluded(
                                                id,
                                                "unrelated-background",
                                                image,
                                                new Region(20, 20, 120, 20),
                                                GREEN)));
        return Stream.of(horizontal, rising, falling, clippedAndSimplified);
    }

    private static RenderScenario lineDirectionScenario(
            String id,
            Feature feature,
            Region attachment,
            Region expectedBody,
            Region oppositeDirection) {
        return scenario(
                id,
                List.of(feature),
                1,
                Optional.empty(),
                List.of(
                        (scenario, image) ->
                                requirePainted(scenario, "end-arrow-attachment", image, attachment),
                        (scenario, image) ->
                                requireDirectionalColor(
                                        scenario,
                                        "end-arrow-direction",
                                        image,
                                        expectedBody,
                                        oppositeDirection,
                                        RED)));
    }

    private static Stream<RenderScenario> polygonAndHatchScenarios() {
        PolygonGeometry polygon = polygonWithHole();
        Symbol outline = SolidLineSymbol.of(stroke(DARK, 6), 1);
        Symbol fill = SolidFillSymbol.of(BLUE, Optional.of(outline), 1);
        RenderScenario solid =
                scenario(
                        "polygon-hole-outline",
                        List.of(new Feature("polygon", "", polygon, Map.of(), fill)),
                        1,
                        Optional.empty(),
                        List.of(
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "exterior",
                                                image,
                                                new Region(39, 29, 9, 9),
                                                BLUE),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "hole",
                                                image,
                                                new Region(76, 56, 9, 9),
                                                BACKGROUND),
                                (id, image) ->
                                        requireMatching(
                                                id,
                                                "outline",
                                                image,
                                                new Region(23, 56, 7, 9),
                                                DARK)));
        Stream<RenderScenario> hatches =
                Stream.of(HatchPattern.values())
                        .map(
                                pattern -> {
                                    String id = "hatch-" + pattern.name().toLowerCase(Locale.ROOT);
                                    HatchFillSymbol hatch =
                                            HatchFillSymbol.of(
                                                    pattern,
                                                    stroke(GREEN, 2),
                                                    new SymbolLength(9, SymbolUnit.SCREEN_PIXEL),
                                                    SymbolRotationMode.SCREEN_RELATIVE,
                                                    1);
                                    return scenario(
                                            id,
                                            List.of(new Feature(id, "", polygon, Map.of(), hatch)),
                                            1,
                                            Optional.of(new RenderClip(30, 25, 88, 70)),
                                            List.of(
                                                    (scenario, image) ->
                                                            requireOccupancy(
                                                                    scenario,
                                                                    "exterior-hatch",
                                                                    image,
                                                                    new Region(38, 30, 24, 18)),
                                                    (scenario, image) ->
                                                            requireExcluded(
                                                                    scenario,
                                                                    "hole-clear",
                                                                    image,
                                                                    new Region(74, 54, 13, 13),
                                                                    GREEN),
                                                    (scenario, image) ->
                                                            requireMatching(
                                                                    scenario,
                                                                    "outside-inherited-clip",
                                                                    image,
                                                                    new Region(18, 50, 7, 15),
                                                                    BACKGROUND)));
                                });
        return Stream.concat(Stream.of(solid), hatches);
    }

    private static RenderScenario scenario(
            String id,
            List<Feature> features,
            double worldUnitsPerPixel,
            Optional<RenderClip> clip,
            List<RenderInvariant> invariants) {
        List<Feature> immutableFeatures = List.copyOf(features);
        return new RenderScenario(
                id,
                WIDTH,
                HEIGHT,
                () -> view(immutableFeatures),
                view -> view.setViewport(viewport(worldUnitsPerPixel)),
                clip,
                invariants);
    }

    private static MapView view(List<Feature> features) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setOpaque(true);
        view.setBackground(new Color(BACKGROUND.red(), BACKGROUND.green(), BACKGROUND.blue()));
        view.setLayers(
                List.of(new InMemoryLayer("regression", "Regression", List.copyOf(features))));
        return view;
    }

    private static void run(RenderScenario scenario) {
        BufferedImage image = render(scenario);
        assertEquals(scenario.width(), image.getWidth(), scenario.id() + ": width");
        assertEquals(scenario.height(), image.getHeight(), scenario.id() + ": height");
        verify(
                scenario.id(),
                image,
                () ->
                        scenario.invariants()
                                .forEach(invariant -> invariant.verify(scenario.id(), image)));
    }

    @Test
    void assertionHelpersRejectRepresentativeBrokenImages() {
        BufferedImage blank = blankImage(40, 40);
        BufferedImage painted = blankImage(40, 40);
        fill(painted, new Region(10, 10, 14, 14), BLUE);
        requirePainted("helper", "positive-paint", painted, new Region(10, 10, 14, 14));
        requireMatching("helper", "positive-color", painted, new Region(10, 10, 14, 14), BLUE);
        assertThrows(
                AssertionError.class,
                () -> requirePainted("helper", "missing-paint", blank, new Region(10, 10, 14, 14)));
        assertThrows(
                AssertionError.class,
                () ->
                        requireMatching(
                                "helper",
                                "swapped-order",
                                painted,
                                new Region(10, 10, 14, 14),
                                RED));
        assertThrows(
                AssertionError.class,
                () ->
                        requireExcluded(
                                "helper",
                                "filled-hole",
                                painted,
                                new Region(10, 10, 14, 14),
                                BLUE));
        assertThrows(
                AssertionError.class,
                () ->
                        requireOccupancy(
                                "helper", "missing-hatch", blank, new Region(10, 10, 14, 14)));

        BufferedImage rasterRows = blankImage(40, 40);
        fill(rasterRows, new Region(10, 10, 14, 7), RED_RGB);
        fill(rasterRows, new Region(10, 17, 14, 7), YELLOW_RGB);
        requireMatching(
                "helper", "positive-raster-row", rasterRows, new Region(10, 10, 14, 7), RED_RGB);
        BufferedImage reversedRows = blankImage(40, 40);
        fill(reversedRows, new Region(10, 10, 14, 7), YELLOW_RGB);
        fill(reversedRows, new Region(10, 17, 14, 7), RED_RGB);
        assertThrows(
                AssertionError.class,
                () ->
                        requireMatching(
                                "helper",
                                "reversed-raster-rows",
                                reversedRows,
                                new Region(10, 10, 14, 7),
                                RED_RGB));
    }

    private static BufferedImage render(RenderScenario scenario) {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            MapView view = scenario.viewFactory().get();
                            view.setSize(scenario.width(), scenario.height());
                            scenario.postSizeSetup().accept(view);
                            BufferedImage image = blankImage(scenario.width(), scenario.height());
                            Graphics2D graphics = image.createGraphics();
                            try {
                                graphics.setComposite(AlphaComposite.SrcOver);
                                graphics.setClip(
                                        scenario.inheritedClip()
                                                .map(
                                                        clip ->
                                                                new Rectangle(
                                                                        clip.x(),
                                                                        clip.y(),
                                                                        clip.width(),
                                                                        clip.height()))
                                                .orElseGet(
                                                        () ->
                                                                new Rectangle(
                                                                        0,
                                                                        0,
                                                                        scenario.width(),
                                                                        scenario.height())));
                                view.paint(graphics);
                            } finally {
                                graphics.dispose();
                            }
                            result.set(image);
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    });
        } catch (Exception exception) {
            throw new IllegalStateException(scenario.id() + ": EDT rendering failed", exception);
        }
        if (failure.get() != null) {
            throw new IllegalStateException(scenario.id() + ": rendering failed", failure.get());
        }
        return result.get();
    }

    private static void verify(String id, BufferedImage image, Runnable invariants) {
        try {
            invariants.run();
        } catch (AssertionError failure) {
            Path directory = Path.of("build", "render-regression", "diagnostics");
            Path path = directory.resolve(id + ".png");
            AssertionError report =
                    new AssertionError(
                            failure.getMessage() + "; diagnostic attempted at " + path, failure);
            try {
                Files.createDirectories(directory);
                if (!ImageIO.write(image, "png", path.toFile())) {
                    report.addSuppressed(new IOException("no PNG writer available"));
                }
            } catch (IOException exception) {
                report.addSuppressed(exception);
            }
            throw report;
        }
    }

    private static void verifyMarkerSilhouette(
            BuiltInMarker marker, String id, BufferedImage image) {
        switch (marker) {
            case SQUARE ->
                    requireMatching(id, "square-corner", image, new Region(61, 41, 7, 7), BLUE);
            case CIRCLE -> {
                requireExcluded(id, "circle-corner-clear", image, new Region(59, 39, 7, 7), BLUE);
                requireMatching(id, "circle-north-arc", image, new Region(77, 40, 7, 7), BLUE);
                requireMatching(id, "circle-west-arc", image, new Region(60, 58, 7, 7), BLUE);
                requirePainted(id, "circle-north-west-arc", image, new Region(63, 43, 7, 7));
            }
            case TRIANGLE -> {
                requirePainted(id, "triangle-north", image, new Region(77, 40, 7, 7));
                requireExcluded(id, "triangle-west-clear", image, new Region(60, 58, 7, 7), BLUE);
                requireExcluded(id, "triangle-left-clear", image, new Region(61, 51, 7, 7), BLUE);
                requireMatching(
                        id, "triangle-south-west-interior", image, new Region(66, 69, 7, 7), BLUE);
            }
            case DIAMOND -> {
                requirePainted(id, "diamond-north", image, new Region(77, 40, 7, 7));
                requirePainted(id, "diamond-west", image, new Region(60, 58, 7, 7));
                requirePainted(id, "diamond-south-west", image, new Region(66, 69, 7, 7));
                requireExcluded(
                        id, "diamond-north-west-clear", image, new Region(63, 43, 7, 7), BLUE);
            }
            case CROSS -> {
                requireMatching(id, "cross-north-arm", image, new Region(77, 40, 7, 7), BLUE);
                requireMatching(id, "cross-west-arm", image, new Region(60, 58, 7, 7), BLUE);
                requireExcluded(
                        id, "cross-north-west-clear", image, new Region(63, 43, 7, 7), BLUE);
                requireExcluded(
                        id, "cross-south-west-clear", image, new Region(66, 69, 7, 7), BLUE);
            }
            case X -> {
                requireExcluded(id, "x-north-clear", image, new Region(77, 40, 7, 7), BLUE);
                requireMatching(id, "x-north-west-arm", image, new Region(63, 43, 7, 7), BLUE);
            }
            case STAR -> {
                requirePainted(id, "star-north-point", image, new Region(77, 40, 7, 7));
                requirePainted(id, "star-left-point", image, new Region(61, 51, 7, 7));
                requireExcluded(id, "star-west-clear", image, new Region(60, 58, 7, 7), BLUE);
            }
            case ARROW -> {
                requireExcluded(id, "arrow-north-clear", image, new Region(77, 40, 7, 7), BLUE);
                requireExcluded(
                        id, "arrow-north-west-clear", image, new Region(63, 43, 7, 7), BLUE);
                requireMatching(id, "arrow-west-shaft", image, new Region(60, 58, 7, 7), BLUE);
            }
        }
    }

    private static double[] anchorFractions(SymbolAnchor anchor) {
        return switch (anchor) {
            case NORTH_WEST -> new double[] {0, 0};
            case NORTH -> new double[] {0.5, 0};
            case NORTH_EAST -> new double[] {1, 0};
            case WEST -> new double[] {0, 0.5};
            case CENTER -> new double[] {0.5, 0.5};
            case EAST -> new double[] {1, 0.5};
            case SOUTH_WEST -> new double[] {0, 1};
            case SOUTH -> new double[] {0.5, 1};
            case SOUTH_EAST -> new double[] {1, 1};
        };
    }

    private static Feature point(String id, Symbol symbol, Coordinate coordinate) {
        return new Feature(id, "", new PointGeometry(coordinate), Map.of(), symbol);
    }

    private static Feature line(
            String id, Symbol symbol, double x1, double y1, double x2, double y2) {
        return new Feature(
                id,
                "",
                new LineStringGeometry(CoordinateSequence.of(x1, y1, x2, y2)),
                Map.of(),
                symbol);
    }

    private static PolygonGeometry polygonWithHole() {
        return new PolygonGeometry(
                CoordinateSequence.of(-55, -38, 55, -38, 55, 38, -55, 38, -55, -38),
                List.of(CoordinateSequence.of(-16, -14, 16, -14, 16, 14, -16, 14, -16, -14)));
    }

    private static VectorMarkerSymbol square(
            Rgba color, MarkerPlacement placement, double opacity) {
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(BuiltInMarker.SQUARE),
                BuiltInMarkers.viewBox(),
                color,
                Optional.empty(),
                placement,
                opacity);
    }

    private static VectorMarkerSymbol endpointArrow() {
        MarkerPlacement placement =
                new MarkerPlacement(
                        SymbolSize.square(18, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.EAST,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(BuiltInMarker.ARROW),
                BuiltInMarkers.viewBox(),
                RED,
                Optional.empty(),
                placement,
                1);
    }

    private static final class SolidRegressionRasterSource implements RasterSource {
        private final RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        new SourceIdentity("render-raster", "render-raster"),
                        2,
                        2,
                        Optional.of(new io.github.mundanej.map.api.Envelope(-40, -20, 40, 20)),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())));

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return RasterSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            if (request.interpolation() != RasterInterpolation.BILINEAR) {
                throw new AssertionError("raster regression expected bilinear request");
            }
            if (cancellation.isCancellationRequested()) {
                throw new AssertionError("raster regression was unexpectedly cancelled");
            }
            RgbaPixelBuffer.Builder pixels =
                    RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
            for (int row = 0; row < request.outputHeight(); row++) {
                for (int column = 0; column < request.outputWidth(); column++) {
                    pixels.setRgba(column, row, 0xff0000ff);
                }
            }
            return new RasterRead(request.sourceWindow(), pixels.build(), DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class WrappedRegressionRasterSource implements RasterSource {
        private static final int[] COLORS = {0xff00_00ff, 0x00ff_00ff, 0x0000_ffff, 0xffff_00ff};
        private final RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        new SourceIdentity("render-wrapped-raster", "render-wrapped-raster"),
                        4,
                        2,
                        Optional.of(
                                new Envelope(
                                        -WebMercatorProjection.WORLD_LIMIT,
                                        -WebMercatorProjection.WORLD_LIMIT / 2,
                                        WebMercatorProjection.WORLD_LIMIT,
                                        WebMercatorProjection.WORLD_LIMIT / 2)),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())));

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return RasterSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            if (request.interpolation() != RasterInterpolation.BILINEAR) {
                throw new AssertionError("wrapped raster regression expected bilinear request");
            }
            if (cancellation.isCancellationRequested()) {
                throw new AssertionError("wrapped raster regression was unexpectedly cancelled");
            }
            RgbaPixelBuffer.Builder pixels =
                    RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
            for (int row = 0; row < request.outputHeight(); row++) {
                for (int column = 0; column < request.outputWidth(); column++) {
                    int sourceColumn =
                            request.sourceWindow().column()
                                    + Math.min(
                                            request.sourceWindow().width() - 1,
                                            column
                                                    * request.sourceWindow().width()
                                                    / request.outputWidth());
                    pixels.setRgba(column, row, COLORS[sourceColumn]);
                }
            }
            return new RasterRead(request.sourceWindow(), pixels.build(), DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class AffineRegressionRasterSource implements RasterSource {
        private final RasterSourceMetadata metadata;

        private AffineRegressionRasterSource(RasterAffineTransform transform) {
            metadata =
                    RasterSourceMetadata.withPlacement(
                            new SourceIdentity("render-affine", "render-affine"),
                            200,
                            160,
                            RasterGridPlacement.affine(transform),
                            Optional.of(
                                    CrsMetadata.recognized(
                                            CrsDefinitions.EPSG_3857,
                                            Optional.of("EPSG:3857"),
                                            Optional.empty())));
        }

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return RasterSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            if (request.interpolation() != RasterInterpolation.BILINEAR
                    || request.sourceWindow().width() >= metadata.width()
                    || request.sourceWindow().height() >= metadata.height()
                    || request.outputWidth() >= request.sourceWindow().width()
                    || request.outputHeight() >= request.sourceWindow().height()) {
                throw new AssertionError(
                        "affine density request did not remain partial and bounded");
            }
            RgbaPixelBuffer.Builder pixels =
                    RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
            for (int row = 0; row < request.outputHeight(); row++) {
                for (int column = 0; column < request.outputWidth(); column++) {
                    pixels.setRgba(
                            column,
                            row,
                            column < request.outputWidth() / 2 ? 0xff0000ff : 0x0000ffff);
                }
            }
            return new RasterRead(request.sourceWindow(), pixels.build(), DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static MapViewport viewport(double worldUnitsPerPixel) {
        return new MapViewport(WIDTH, HEIGHT, 0, 0, worldUnitsPerPixel);
    }

    private static void requireMatching(
            String id, String invariant, BufferedImage image, Region region, Rgba expected) {
        double fraction = matchingFraction(image, region, expected);
        if (fraction < TOLERANCE.minimumMatchingFraction()) {
            throw new AssertionError(
                    id + ": " + invariant + " expected matching fraction, actual=" + fraction);
        }
    }

    private static void requireExcluded(
            String id, String invariant, BufferedImage image, Region region, Rgba excluded) {
        double fraction = matchingFraction(image, region, excluded);
        if (fraction > TOLERANCE.maximumExclusionFraction()) {
            throw new AssertionError(
                    id + ": " + invariant + " expected exclusion, actual=" + fraction);
        }
    }

    private static void requirePainted(
            String id, String invariant, BufferedImage image, Region region) {
        double occupancy = occupancy(image, region);
        if (occupancy < TOLERANCE.minimumHatchOccupancy()) {
            throw new AssertionError(
                    id + ": " + invariant + " expected paint, actual=" + occupancy);
        }
    }

    private static void requireOccupancy(
            String id, String invariant, BufferedImage image, Region region) {
        double occupancy = occupancy(image, region);
        if (occupancy < TOLERANCE.minimumHatchOccupancy()
                || occupancy > TOLERANCE.maximumHatchOccupancy()) {
            throw new AssertionError(
                    id + ": " + invariant + " occupancy outside profile, actual=" + occupancy);
        }
    }

    private static void requireDirectionalColor(
            String id,
            String invariant,
            BufferedImage image,
            Region expectedBody,
            Region oppositeDirection,
            Rgba color) {
        double expectedFraction = matchingFraction(image, expectedBody, color);
        double oppositeFraction = matchingFraction(image, oppositeDirection, color);
        if (expectedFraction < oppositeFraction + TOLERANCE.minimumHatchOccupancy()) {
            throw new AssertionError(
                    id
                            + ": "
                            + invariant
                            + " expected directional color separation, actual="
                            + expectedFraction
                            + " versus "
                            + oppositeFraction);
        }
    }

    private static void requireBetween(
            String id, String invariant, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new AssertionError(
                    id
                            + ": "
                            + invariant
                            + " expected ["
                            + minimum
                            + ", "
                            + maximum
                            + "], actual="
                            + value);
        }
    }

    private static void requireBoundsNear(
            String id,
            BufferedImage image,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY) {
        int[] actual = paintedBounds(image);
        int margin = TOLERANCE.boundsMargin();
        if (StrictMath.abs(actual[0] - minimumX) > margin
                || StrictMath.abs(actual[1] - minimumY) > margin
                || StrictMath.abs(actual[2] - maximumX) > margin
                || StrictMath.abs(actual[3] - maximumY) > margin) {
            throw new AssertionError(
                    id
                            + ": painted-bounds expected near ["
                            + minimumX
                            + ", "
                            + minimumY
                            + ", "
                            + maximumX
                            + ", "
                            + maximumY
                            + "], actual="
                            + java.util.Arrays.toString(actual));
        }
    }

    private static double matchingFraction(BufferedImage image, Region region, Rgba expected) {
        int matching = 0;
        for (int y = region.y(); y < region.y() + region.height(); y++) {
            for (int x = region.x(); x < region.x() + region.width(); x++) {
                if (matches(rgba(image.getRGB(x, y)), expected)) {
                    matching++;
                }
            }
        }
        return (double) matching / region.pixelCount();
    }

    private static double occupancy(BufferedImage image, Region region) {
        int painted = 0;
        for (int y = region.y(); y < region.y() + region.height(); y++) {
            for (int x = region.x(); x < region.x() + region.width(); x++) {
                if (maximumChannelDistance(rgba(image.getRGB(x, y)), BACKGROUND)
                        > TOLERANCE.backgroundDelta()) {
                    painted++;
                }
            }
        }
        return (double) painted / region.pixelCount();
    }

    private static boolean matches(Rgba actual, Rgba expected) {
        return maximumChannelDistance(actual, expected) <= TOLERANCE.colorTolerance();
    }

    private static int maximumChannelDistance(Rgba first, Rgba second) {
        return Math.max(
                Math.max(
                        Math.abs(first.red() - second.red()),
                        Math.abs(first.green() - second.green())),
                Math.max(
                        Math.abs(first.blue() - second.blue()),
                        Math.abs(first.alpha() - second.alpha())));
    }

    private static int averageBrightness(BufferedImage image, Region region) {
        long total = 0;
        for (int y = region.y(); y < region.y() + region.height(); y++) {
            for (int x = region.x(); x < region.x() + region.width(); x++) {
                Rgba color = rgba(image.getRGB(x, y));
                total += color.red() + color.green() + color.blue();
            }
        }
        return Math.toIntExact(total / (3L * region.pixelCount()));
    }

    private static int[] paintedBounds(BufferedImage image) {
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (maximumChannelDistance(rgba(image.getRGB(x, y)), BACKGROUND)
                        > TOLERANCE.backgroundDelta()) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        return new int[] {minimumX, minimumY, maximumX, maximumY};
    }

    private static int paintedWidth(BufferedImage image) {
        int[] bounds = paintedBounds(image);
        return bounds[2] - bounds[0] + 1;
    }

    private static BufferedImage blankImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(255, 255, 255, 255));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void fill(BufferedImage image, Region region, Rgba color) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(color.red(), color.green(), color.blue(), color.alpha()));
            graphics.fillRect(region.x(), region.y(), region.width(), region.height());
        } finally {
            graphics.dispose();
        }
    }

    private static Rgba rgba(int argb) {
        Color color = new Color(argb, true);
        return new Rgba(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private record Region(int x, int y, int width, int height) {
        private Region {
            if (x < 0 || y < 0 || width < 7 || height < 7) {
                throw new IllegalArgumentException(
                        "region must be at least 7x7 at positive coordinates");
            }
        }

        int pixelCount() {
            return width * height;
        }
    }

    private record RenderTolerance(
            int colorTolerance,
            int backgroundDelta,
            int boundsMargin,
            double minimumMatchingFraction,
            double maximumExclusionFraction,
            double minimumHatchOccupancy,
            double maximumHatchOccupancy) {}
}
