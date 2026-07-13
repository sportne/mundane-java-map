package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RasterIconCatalogTest {
    private static final int[] PIXELS = {0xff0000ff, 0x00ff0080, 0x0000ffff, 0xffff00ff};

    @Test
    void rasterIconOwnsPackedRowsPlacementAndValueSemantics() {
        int[] source = PIXELS.clone();
        RasterIconSymbol icon =
                RasterIconSymbol.nativeScreenSize(2, 2, source, RasterInterpolation.NEAREST, 0.75);
        source[0] = 0;
        assertEquals(0xff0000ff, icon.rgbaAt(0, 0));
        assertEquals(0x00ff0080, icon.rgbaAt(1, 0));
        assertEquals(0x0000ffff, icon.rgbaAt(0, 1));
        assertEquals(0xffff00ff, icon.rgbaAt(1, 1));
        int[] copy = icon.toRgbaArray();
        copy[0] = 0;
        assertEquals(0xff0000ff, icon.rgbaAt(0, 0));
        assertEquals(new SymbolSize(2.0, 2.0, SymbolUnit.SCREEN_PIXEL), icon.placement().size());
        assertEquals(
                icon,
                RasterIconSymbol.nativeScreenSize(2, 2, PIXELS, RasterInterpolation.NEAREST, 0.75));
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0, icon.rgbaAt(2, 0)));

        RasterIconSymbol proportional =
                RasterIconSymbol.screenWidth(2, 2, PIXELS, 12.0, RasterInterpolation.BILINEAR, 1.0);
        assertEquals(
                new SymbolSize(12.0, 12.0, SymbolUnit.SCREEN_PIXEL),
                proportional.placement().size());
        assertEquals(RasterInterpolation.BILINEAR, proportional.interpolation());
        assertEquals(icon.hashCode(), icon.hashCode());
        assertNotEquals(icon, proportional);
    }

    @Test
    void rasterLimitsRejectHostileDimensionsBeforePixelAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterIconSymbol.of(
                                4097,
                                1,
                                null,
                                MarkerPlacement.centeredScreen(1.0),
                                RasterInterpolation.NEAREST,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterIconSymbol.of(
                                4096,
                                4096,
                                null,
                                MarkerPlacement.centeredScreen(1.0),
                                RasterInterpolation.NEAREST,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterIconSymbol.nativeScreenSize(
                                2, 2, new int[3], RasterInterpolation.NEAREST, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterIconSymbol.screenWidth(
                                1,
                                2,
                                new int[2],
                                Double.MAX_VALUE,
                                RasterInterpolation.NEAREST,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterIconSymbol.nativeScreenSize(
                                2, 2, PIXELS, RasterInterpolation.NEAREST, -0.1));
        assertThrows(
                NullPointerException.class,
                () ->
                        RasterIconSymbol.nativeScreenSize(
                                1, 1, null, RasterInterpolation.NEAREST, 1.0));
    }

    @Test
    void namedCatalogPreservesOrderAndSeparatesMalformedMissingAndDuplicateNames() {
        RasterIconSymbol icon =
                RasterIconSymbol.nativeScreenSize(2, 2, PIXELS, RasterInterpolation.NEAREST, 1.0);
        VectorMarkerSymbol vector =
                VectorMarkerSymbol.filledScreen(
                        VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build(),
                        new Envelope(0, 0, 1, 1),
                        Rgba.rgb(1, 2, 3),
                        4,
                        1);
        ArrayList<NamedSymbol> source =
                new ArrayList<>(
                        List.of(
                                new NamedSymbol("raster icon", icon),
                                new NamedSymbol("Vector", vector)));
        NamedSymbolCatalog catalog = NamedSymbolCatalog.of(source);
        source.clear();
        assertEquals(
                List.of("raster icon", "Vector"),
                catalog.entries().stream().map(NamedSymbol::name).toList());
        assertEquals(icon, catalog.find("raster icon").orElseThrow());
        assertTrue(catalog.find("absent").isEmpty());
        SymbolException missing =
                assertThrows(SymbolException.class, () -> catalog.require("absent"));
        assertEquals(SymbolException.CATALOG_MISSING, missing.code());
        assertEquals(java.util.Map.of("name", "absent"), missing.context());
        assertThrows(IllegalArgumentException.class, () -> catalog.find(" absent"));
        assertThrows(IllegalArgumentException.class, () -> new NamedSymbol(" ", icon));

        SymbolException duplicate =
                assertThrows(
                        SymbolException.class,
                        () ->
                                NamedSymbolCatalog.of(
                                        List.of(
                                                new NamedSymbol("same", icon),
                                                new NamedSymbol("same", vector))));
        assertEquals(SymbolException.CATALOG_DUPLICATE, duplicate.code());
        assertEquals(
                List.of("name", "firstIndex", "duplicateIndex"),
                List.copyOf(duplicate.context().keySet()));
        assertEquals("0", duplicate.context().get("firstIndex"));
        assertEquals("1", duplicate.context().get("duplicateIndex"));
        assertEquals(List.of(), NamedSymbolCatalog.of(List.of()).entries());
        assertEquals(catalog, NamedSymbolCatalog.of(catalog.entries()));
        assertEquals(catalog.hashCode(), NamedSymbolCatalog.of(catalog.entries()).hashCode());
        assertEquals(
                catalog.entries(),
                java.util.stream.StreamSupport.stream(catalog.spliterator(), false).toList());

        CompositeSymbol composite = CompositeSymbol.of(List.of(icon, vector), 0.75);
        NamedSymbolCatalog compositeCatalog =
                NamedSymbolCatalog.of(List.of(new NamedSymbol("composite", composite)));
        assertEquals(composite, compositeCatalog.require("composite"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void namedSymbolsRejectLegacyAndMalformedRoleContracts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NamedSymbol("legacy", FeatureStyle.point(Rgba.rgb(0, 0, 0), 4)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NamedSymbol("multiple", new MultipleRoleSymbol()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NamedSymbol("wrong", new WrongRoleMarker()));
        assertThrows(NullPointerException.class, () -> new NamedSymbol("key", new NullKeyMarker()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NamedSymbol("opacity", new BadOpacityMarker()));
    }

    private record MultipleRoleSymbol() implements MarkerSymbol, LineSymbol {
        @Override
        public SymbolRole role() {
            return SymbolRole.MARKER;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.multiple");
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private record WrongRoleMarker() implements MarkerSymbol {
        @Override
        public SymbolRole role() {
            return SymbolRole.LINE;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.wrong");
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private record NullKeyMarker() implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return null;
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private record BadOpacityMarker() implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.opacity");
        }

        @Override
        public double opacity() {
            return Double.NaN;
        }
    }
}
