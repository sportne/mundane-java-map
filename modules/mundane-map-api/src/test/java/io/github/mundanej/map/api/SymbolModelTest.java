package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SymbolModelTest {
    @Test
    void permitsOpenVectorMarkerPathOnlyForStrokeOnlySymbols() {
        VectorPath open = VectorPath.builder().moveTo(0, 0).lineTo(1, 1).build();
        Envelope viewBox = new Envelope(0, 0, 1, 1);
        SymbolStroke stroke =
                new SymbolStroke(Rgba.rgb(0, 0, 0), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));

        VectorMarkerSymbol symbol =
                VectorMarkerSymbol.of(
                        open,
                        viewBox,
                        Rgba.TRANSPARENT,
                        Optional.of(stroke),
                        MarkerPlacement.centeredScreen(10),
                        1);
        assertEquals(open, symbol.path());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.of(
                                open,
                                viewBox,
                                Rgba.rgb(1, 2, 3),
                                Optional.of(stroke),
                                MarkerPlacement.centeredScreen(10),
                                1));
    }

    private static final Envelope VIEW_BOX = new Envelope(-1.0, -1.0, 1.0, 1.0);

    @Test
    void rendererKeysRequireExactNamespacedAsciiSegments() {
        SymbolRendererKey key = new SymbolRendererKey("example.marker-one");

        assertEquals("example.marker-one", key.value());
        assertEquals("example.marker-one", key.toString());
        for (String invalid :
                new String[] {
                    "marker",
                    "Example.marker",
                    "example.Marker",
                    "example..marker",
                    ".example.marker",
                    "example.marker ",
                    "example._marker"
                }) {
            assertThrows(IllegalArgumentException.class, () -> new SymbolRendererKey(invalid));
        }
        assertThrows(NullPointerException.class, () -> new SymbolRendererKey(null));
    }

    @Test
    void vectorMarkerIsAnImmutableValueWithValidatedBoundsAndOpacity() {
        VectorPath path = closedTriangle();
        VectorMarkerSymbol marker =
                VectorMarkerSymbol.filledScreen(
                        path, VIEW_BOX, new Rgba(10, 20, 30, 128), 24.0, 0.5);
        VectorMarkerSymbol same =
                VectorMarkerSymbol.filledScreen(
                        closedTriangle(), VIEW_BOX, new Rgba(10, 20, 30, 128), 24.0, 0.5);

        assertEquals(SymbolRole.MARKER, marker.role());
        assertEquals(VectorMarkerSymbol.RENDERER_KEY, marker.rendererKey());
        assertEquals(path, marker.path());
        assertEquals(VIEW_BOX, marker.viewBox());
        assertEquals(new Rgba(10, 20, 30, 128), marker.fill());
        assertEquals(MarkerPlacement.centeredScreen(24.0), marker.placement());
        assertTrue(marker.stroke().isEmpty());
        assertEquals(0.5, marker.opacity());
        assertEquals(marker, same);
        assertEquals(marker.hashCode(), same.hashCode());
        assertNotEquals(
                marker,
                VectorMarkerSymbol.filledScreen(path, VIEW_BOX, Rgba.rgb(10, 20, 31), 24.0, 0.5));
        assertTrue(marker.toString().contains("placement=MarkerPlacement"));
    }

    @Test
    void vectorMarkerRejectsOpenOutsideDegenerateOrInvalidMeasurements() {
        VectorPath open = VectorPath.builder().moveTo(0.0, 0.0).lineTo(1.0, 0.0).build();
        VectorPath outside = VectorPath.builder().moveTo(0.0, 0.0).lineTo(2.0, 0.0).close().build();

        assertThrows(
                IllegalArgumentException.class,
                () -> VectorMarkerSymbol.filledScreen(open, VIEW_BOX, Rgba.TRANSPARENT, 10.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.filledScreen(
                                outside, VIEW_BOX, Rgba.TRANSPARENT, 10.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.filledScreen(
                                closedTriangle(),
                                new Envelope(0.0, 0.0, 0.0, 1.0),
                                Rgba.TRANSPARENT,
                                10.0,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.filledScreen(
                                closedTriangle(),
                                new Envelope(-Double.MAX_VALUE, -1.0, Double.MAX_VALUE, 1.0),
                                Rgba.TRANSPARENT,
                                10.0,
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.filledScreen(
                                closedTriangle(), VIEW_BOX, Rgba.TRANSPARENT, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorMarkerSymbol.filledScreen(
                                closedTriangle(), VIEW_BOX, Rgba.TRANSPARENT, 10.0, 1.1));
        assertThrows(
                NullPointerException.class,
                () -> VectorMarkerSymbol.filledScreen(null, VIEW_BOX, Rgba.TRANSPARENT, 10.0, 1.0));
    }

    @Test
    void featureAcceptsExactlyOneMatchingRoleAndRetainsLegacyCompatibility() {
        PointGeometry point = new PointGeometry(new Coordinate(0.0, 0.0));
        MarkerSymbol marker = customMarker("example.marker");
        Feature feature = new Feature("point", "", point, Map.of(), marker);

        assertEquals(marker, feature.symbol());

        @SuppressWarnings("deprecation")
        Feature legacy =
                new Feature(
                        "legacy", "", point, Map.of(), FeatureStyle.point(Rgba.rgb(1, 2, 3), 6.0));
        assertEquals(SymbolRole.LEGACY_GEOMETRY, legacy.symbol().role());

        SymbolException wrongGeometry =
                assertThrows(
                        SymbolException.class,
                        () ->
                                new Feature(
                                        "wrong",
                                        "",
                                        new LineStringGeometry(
                                                CoordinateSequence.of(0.0, 0.0, 1.0, 1.0)),
                                        Map.of(),
                                        marker));
        assertEquals(SymbolException.ROLE_MISMATCH, wrongGeometry.code());
        assertEquals(
                Map.of(
                        "featureId", "wrong",
                        "geometryKind", "LINE_STRING",
                        "symbolRole", "MARKER"),
                wrongGeometry.context());

        assertRoleMismatch(point, new DirectSymbol());
        assertRoleMismatch(point, new WrongRoleMarker());
        assertThrows(
                NullPointerException.class,
                () -> new Feature("null-key", "", point, Map.of(), new NullKeyMarker()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Feature("opacity", "", point, Map.of(), new InvalidOpacityMarker()));
    }

    @Test
    void symbolExceptionDefensivelyCopiesOrderedContext() {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("role", "MARKER");
        context.put("key", "example.marker");
        SymbolException failure = new SymbolException("SYMBOL_TEST", "test failure", context);
        context.clear();

        assertEquals("SYMBOL_TEST", failure.code());
        assertEquals(List.of("role", "key"), failure.context().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class, () -> failure.context().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    SymbolException unexpected = new SymbolException(" ", "message", Map.of());
                    assertEquals("unreachable", unexpected.code());
                });
        LinkedHashMap<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("key", null);
        assertThrows(
                NullPointerException.class,
                () -> {
                    SymbolException unexpected =
                            new SymbolException("SYMBOL_TEST", "message", nullValue);
                    assertEquals("unreachable", unexpected.code());
                });
    }

    private static void assertRoleMismatch(Geometry geometry, Symbol symbol) {
        SymbolException failure =
                assertThrows(
                        SymbolException.class,
                        () -> new Feature("bad", "", geometry, Map.of(), symbol));
        assertEquals(SymbolException.ROLE_MISMATCH, failure.code());
    }

    private static MarkerSymbol customMarker(String key) {
        return new MarkerSymbol() {
            @Override
            public SymbolRendererKey rendererKey() {
                return new SymbolRendererKey(key);
            }

            @Override
            public double opacity() {
                return 1.0;
            }
        };
    }

    private static VectorPath closedTriangle() {
        return VectorPath.builder()
                .moveTo(-1.0, 1.0)
                .lineTo(0.0, -1.0)
                .lineTo(1.0, 1.0)
                .close()
                .build();
    }

    private static class DirectSymbol implements Symbol {
        @Override
        public SymbolRole role() {
            return SymbolRole.MARKER;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.direct");
        }

        @Override
        public double opacity() {
            return 1.0;
        }
    }

    private static final class WrongRoleMarker extends DirectSymbol implements MarkerSymbol {
        @Override
        public SymbolRole role() {
            return SymbolRole.LINE;
        }
    }

    private static final class NullKeyMarker extends DirectSymbol implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return null;
        }
    }

    private static final class InvalidOpacityMarker extends DirectSymbol implements MarkerSymbol {
        @Override
        public double opacity() {
            return Double.NaN;
        }
    }
}
