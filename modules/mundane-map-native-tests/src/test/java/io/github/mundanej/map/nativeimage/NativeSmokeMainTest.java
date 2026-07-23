package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.BuiltInMarkers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NativeSmokeMainTest {
    private static final String RESOURCE_CONFIG =
            "/META-INF/native-image/io.github.mundanej/mundane-map-native-tests/resource-config.json";

    @Test
    void rendersTheSharedResourceScenarioOnTheJvm() {
        NativeSmokeMain.runSmoke();
    }

    @Test
    void rendersWhenAlreadyOnTheEventDispatchThread() throws Exception {
        SwingUtilities.invokeAndWait(
                () ->
                        NativeSmokeMain.runScenario(
                                NativeSymbolSmokeScenario.standard(
                                        NativeSmokeMain.loadRasterPixels())));
    }

    @Test
    void readsAndPacksTheDeclaredRawResource() {
        assertArrayEquals(
                new int[] {
                    0xff0000ff,
                    0x00ff00ff,
                    0x0000ffff,
                    0xffffff00,
                    0xffff00ff,
                    0x00ffffff,
                    0xff00ff80,
                    0x000000ff
                },
                NativeSmokeMain.loadRasterPixels());
    }

    @Test
    void rejectsTruncatedAndOverlongRawResources() {
        IllegalStateException truncated =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeSmokeMain.decodeRasterPixels(new byte[31]));
        IllegalStateException overlong =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeSmokeMain.decodeRasterPixels(new byte[33]));
        assertTrue(truncated.getMessage().startsWith("raster-resource:"));
        assertTrue(overlong.getMessage().startsWith("raster-resource:"));
    }

    @Test
    void exactResourceMetadataDeclaresOnlyTheFixedInventory() throws IOException {
        String expected =
                """
                {
                  "resources": {
                    "includes": [
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/symbol-smoke-4x2.rgba\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/polygon-smoke.shp\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/polygon-smoke.shx\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/polygon-smoke.dbf\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/polygon-smoke.cpg\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/polygon-smoke.prj\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/shapefile/malformed-record.shp\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/raster/png-affine-smoke.png\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/raster/png-affine-smoke.pgw\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/raster/jpeg-affine-smoke.jpg\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/raster/jpeg-affine-smoke.jgw\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/raster/malformed-idat-crc.png\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/dted/zone-v-l0-smoke.dt0\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/geotiff/gdal-rgb-strip-none-4326.tif\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/geotiff/gdal-gray-tile-\
                deflate-3857.tif\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/geotiff/gdal-int16-strip-\
                packbits-4326.tif\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/geotiff/gdal-float32-tile-\
                deflate-3857.tif\\\\E"
                      },
                      {
                        "condition": {
                          "typeReachable": "io.github.mundanej.map.nativeimage.NativeSmokeMain"
                        },
                        "pattern": "\\\\Qio/github/mundanej/map/nativeimage/se/native-style.xml\\\\E"
                      }
                    ]
                  }
                }
                """;
        try (InputStream input = NativeSmokeMain.class.getResourceAsStream(RESOURCE_CONFIG)) {
            if (input == null) {
                throw new AssertionError("resource-config.json is missing");
            }
            String actual = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(normalize(expected), normalize(actual));
        }
    }

    @Test
    void sharedAssertionsRejectStraightenedCurves() {
        NativeSymbolSmokeScenario standard =
                NativeSymbolSmokeScenario.standard(NativeSmokeMain.loadRasterPixels());
        VectorPath straightQuadratic =
                VectorPath.builder()
                        .moveTo(-0.5, 0.5)
                        .lineTo(-0.5, 0.0)
                        .lineTo(0.0, -0.5)
                        .cubicTo(0.3, -0.5, 0.5, -0.3, 0.5, 0.0)
                        .lineTo(0.5, 0.5)
                        .close()
                        .build();
        VectorPath straightCubic =
                VectorPath.builder()
                        .moveTo(-0.5, 0.5)
                        .lineTo(-0.5, 0.0)
                        .quadraticTo(-0.5, -0.5, 0.0, -0.5)
                        .lineTo(0.5, 0.0)
                        .lineTo(0.5, 0.5)
                        .close()
                        .build();

        assertScenarioFailure(standard.withVectorPath(straightQuadratic), "vector-quadratic");
        assertScenarioFailure(standard.withVectorPath(straightCubic), "vector-cubic");
    }

    @Test
    void sharedAssertionsRejectReversedCompositeAndRasterRows() {
        NativeSymbolSmokeScenario standard =
                NativeSymbolSmokeScenario.standard(NativeSmokeMain.loadRasterPixels());
        List<Symbol> reversedChildren = new ArrayList<>(standard.composite().children());
        Collections.reverse(reversedChildren);
        CompositeSymbol reversed = CompositeSymbol.of(reversedChildren, 1.0);

        int[] reversedRows = standard.rasterPixels();
        for (int column = 0; column < 4; column++) {
            int first = reversedRows[column];
            reversedRows[column] = reversedRows[column + 4];
            reversedRows[column + 4] = first;
        }

        assertScenarioFailure(standard.withComposite(reversed), "composite-order");
        assertScenarioFailure(standard.withRasterPixels(reversedRows), "raster-rows");
    }

    @Test
    void sharedAssertionsRejectCorruptedRasterColorAndAlpha() {
        NativeSymbolSmokeScenario standard =
                NativeSymbolSmokeScenario.standard(NativeSmokeMain.loadRasterPixels());
        int[] wrongColor = standard.rasterPixels();
        wrongColor[0] = 0x00ff00ff;
        int[] wrongAlpha = standard.rasterPixels();
        wrongAlpha[6] = 0xff00ffff;

        assertScenarioFailure(standard.withRasterPixels(wrongColor), "raster-resource");
        assertScenarioFailure(standard.withRasterPixels(wrongAlpha), "raster-alpha");
    }

    @Test
    void sharedBoundsRejectAnOversizedCompositeChild() {
        NativeSymbolSmokeScenario standard =
                NativeSymbolSmokeScenario.standard(NativeSmokeMain.loadRasterPixels());
        CompositeSymbol oversized =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.SQUARE,
                                        NativeSymbolSmokeScenario.COMPOSITE_BLUE,
                                        60.0,
                                        1.0),
                                standard.composite().children().get(1)),
                        1.0);

        assertScenarioFailure(standard.withComposite(oversized), "composite-order");
    }

    @Test
    void scenarioCopiesRasterPixelsDefensively() {
        int[] pixels = NativeSmokeMain.loadRasterPixels();
        NativeSymbolSmokeScenario scenario = NativeSymbolSmokeScenario.standard(pixels);
        pixels[0] = 0;
        int[] firstCopy = scenario.rasterPixels();
        firstCopy[0] = 0;
        assertEquals(0xff0000ff, scenario.rasterPixels()[0]);
    }

    private static void assertScenarioFailure(
            NativeSymbolSmokeScenario scenario, String invariantName) {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class, () -> NativeSmokeMain.runScenario(scenario));
        assertTrue(failure.getMessage().startsWith(invariantName + ":"), failure::getMessage);
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
