package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SymbolPlacementTest {
    @Test
    void placementValuesValidateAndCanonicalizeTheirMeasurements() {
        SymbolSize size = new SymbolSize(12.0, 18.0, SymbolUnit.MAP_UNIT);
        SymbolLength length = new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL);
        MarkerPlacement placement =
                new MarkerPlacement(
                        size,
                        SymbolAnchor.SOUTH_EAST,
                        -0.0,
                        3.0,
                        -450.0,
                        SymbolRotationMode.MAP_RELATIVE);

        assertEquals(12.0, size.width());
        assertEquals(18.0, size.height());
        assertEquals(SymbolUnit.MAP_UNIT, size.unit());
        assertEquals(2.0, length.value());
        assertEquals(0.0, placement.offsetX());
        assertEquals(3.0, placement.offsetY());
        assertEquals(270.0, placement.rotationDegrees());
        assertEquals(SymbolAnchor.SOUTH_EAST, placement.anchor());
        assertEquals(SymbolRotationMode.MAP_RELATIVE, placement.rotationMode());
        assertEquals(
                MarkerPlacement.centeredScreen(8.0),
                new MarkerPlacement(
                        SymbolSize.square(8.0, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0.0,
                        0.0,
                        360.0,
                        SymbolRotationMode.SCREEN_RELATIVE));
        assertEquals(9, SymbolAnchor.values().length);
        assertEquals(2, SymbolUnit.values().length);
        assertEquals(2, SymbolRotationMode.values().length);
    }

    @Test
    void measurementsAndPlacementRejectInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SymbolSize(0.0, 1.0, SymbolUnit.SCREEN_PIXEL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SymbolSize(1.0, Double.POSITIVE_INFINITY, SymbolUnit.SCREEN_PIXEL));
        assertThrows(NullPointerException.class, () -> new SymbolSize(1.0, 1.0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SymbolLength(Double.NaN, SymbolUnit.MAP_UNIT));
        assertThrows(NullPointerException.class, () -> new SymbolLength(1.0, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MarkerPlacement(
                                SymbolSize.square(1.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                Double.NaN,
                                0.0,
                                0.0,
                                SymbolRotationMode.SCREEN_RELATIVE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MarkerPlacement(
                                SymbolSize.square(1.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0.0,
                                0.0,
                                Double.NEGATIVE_INFINITY,
                                SymbolRotationMode.SCREEN_RELATIVE));
        assertThrows(NullPointerException.class, () -> new SymbolStroke(null, validLength()));
        assertThrows(NullPointerException.class, () -> new SymbolStroke(Rgba.TRANSPARENT, null));
    }

    @Test
    void vectorMarkerCanonicalFactoryRetainsStrokePlacementAndValueEquality() {
        VectorPath path = triangle();
        SymbolStroke stroke =
                new SymbolStroke(Rgba.rgb(1, 2, 3), new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL));
        MarkerPlacement placement =
                new MarkerPlacement(
                        new SymbolSize(20.0, 10.0, SymbolUnit.MAP_UNIT),
                        SymbolAnchor.NORTH_WEST,
                        2.0,
                        -3.0,
                        45.0,
                        SymbolRotationMode.MAP_RELATIVE);

        VectorMarkerSymbol marker =
                VectorMarkerSymbol.of(
                        path,
                        new Envelope(-1.0, -1.0, 1.0, 1.0),
                        Rgba.rgb(10, 20, 30),
                        Optional.of(stroke),
                        placement,
                        -0.0);
        VectorMarkerSymbol same =
                VectorMarkerSymbol.of(
                        path,
                        new Envelope(-1.0, -1.0, 1.0, 1.0),
                        Rgba.rgb(10, 20, 30),
                        Optional.of(stroke),
                        placement,
                        0.0);

        assertEquals(Optional.of(stroke), marker.stroke());
        assertEquals(placement, marker.placement());
        assertEquals(0.0, marker.opacity());
        assertEquals(marker, same);
        assertEquals(marker.hashCode(), same.hashCode());
        assertNotEquals(
                marker,
                VectorMarkerSymbol.of(
                        path,
                        new Envelope(-1.0, -1.0, 1.0, 1.0),
                        Rgba.rgb(10, 20, 30),
                        Optional.empty(),
                        placement,
                        0.0));
        assertThrows(
                NullPointerException.class,
                () ->
                        VectorMarkerSymbol.of(
                                path,
                                new Envelope(-1.0, -1.0, 1.0, 1.0),
                                Rgba.TRANSPARENT,
                                null,
                                placement,
                                1.0));
    }

    @Test
    void compositesCopyOrderPreserveNestingAndInferAllThreeRoles() {
        Symbol red = marker(Rgba.rgb(200, 20, 20), 12.0, 1.0);
        Symbol blue = marker(Rgba.rgb(20, 20, 200), 8.0, 0.5);
        List<Symbol> mutable = new ArrayList<>(List.of(red, blue));
        CompositeSymbol markerComposite = CompositeSymbol.of(mutable, 0.75);
        mutable.clear();
        CompositeSymbol nested = CompositeSymbol.of(List.of(markerComposite, red), 0.5);
        CompositeSymbol same =
                CompositeSymbol.of(List.of(CompositeSymbol.of(List.of(red, blue), 0.75), red), 0.5);

        assertEquals(List.of(red, blue), markerComposite.children());
        assertEquals(SymbolRole.MARKER, markerComposite.role());
        assertEquals(CompositeSymbol.RENDERER_KEY, markerComposite.rendererKey());
        assertEquals(0.75, markerComposite.opacity());
        assertThrows(UnsupportedOperationException.class, () -> markerComposite.children().clear());
        assertEquals(nested, same);
        assertEquals(nested.hashCode(), same.hashCode());
        assertTrue(nested.toString().contains("children="));

        CompositeSymbol lines = CompositeSymbol.of(List.of(new TestLine()), 1.0);
        CompositeSymbol fills = CompositeSymbol.of(List.of(new TestFill()), 1.0);
        assertEquals(SymbolRole.LINE, lines.role());
        assertEquals(SymbolRole.FILL, fills.role());

        Feature lineFeature =
                new Feature(
                        "line",
                        "",
                        new LineStringGeometry(CoordinateSequence.of(0.0, 0.0, 1.0, 1.0)),
                        Map.of(),
                        lines);
        assertEquals(lines, lineFeature.symbol());
    }

    @Test
    @SuppressWarnings("deprecation")
    void compositesRejectEmptyNullMixedLegacyAndDishonestChildren() {
        Symbol marker = marker(Rgba.rgb(1, 2, 3), 8.0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> CompositeSymbol.of(List.of(), 1.0));
        assertThrows(NullPointerException.class, () -> CompositeSymbol.of(null, 1.0));
        List<Symbol> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> CompositeSymbol.of(withNull, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(marker), Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(marker, new TestLine()), 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(FeatureStyle.point(Rgba.rgb(1, 2, 3), 8.0)), 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(new DirectSymbol()), 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(new MultiRole()), 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CompositeSymbol.of(List.of(new WrongRoleMarker()), 1.0));
    }

    private static SymbolLength validLength() {
        return new SymbolLength(1.0, SymbolUnit.SCREEN_PIXEL);
    }

    private static VectorMarkerSymbol marker(Rgba fill, double size, double opacity) {
        return VectorMarkerSymbol.filledScreen(
                triangle(), new Envelope(-1.0, -1.0, 1.0, 1.0), fill, size, opacity);
    }

    private static VectorPath triangle() {
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
            return new SymbolRendererKey("example.direct-placement");
        }

        @Override
        public double opacity() {
            return 1.0;
        }
    }

    private static final class MultiRole extends DirectSymbol implements MarkerSymbol, LineSymbol {}

    private static final class WrongRoleMarker extends DirectSymbol implements MarkerSymbol {
        @Override
        public SymbolRole role() {
            return SymbolRole.LINE;
        }
    }

    private record TestLine() implements LineSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.line-placement");
        }

        @Override
        public double opacity() {
            return 1.0;
        }
    }

    private record TestFill() implements FillSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return new SymbolRendererKey("example.fill-placement");
        }

        @Override
        public double opacity() {
            return 1.0;
        }
    }
}
