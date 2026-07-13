package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SymbolTransformsTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Envelope VIEW_BOX = new Envelope(-1.0, -1.0, 1.0, 1.0);
    private static final Coordinate FEATURE = new Coordinate(100.0, 200.0);
    private static final MapScreenBasis BASIS =
            MapScreenBasis.of(new Coordinate(2.0, 0.0), new Coordinate(0.0, -2.0));

    @Test
    void basisExposesValidatedSimilarityMeasurements() {
        assertEquals(new Coordinate(2.0, 0.0), BASIS.xUnitScreenDelta());
        assertEquals(new Coordinate(0.0, -2.0), BASIS.yUnitScreenDelta());
        assertEquals(-4.0, BASIS.determinant());
        assertEquals(2.0, BASIS.uniformScale());
        assertEquals(0.0, BASIS.xAxisScreenBearingDegrees());

        MapScreenBasis rotated =
                MapScreenBasis.of(new Coordinate(0.0, 3.0), new Coordinate(3.0, 0.0));
        assertEquals(90.0, rotated.xAxisScreenBearingDegrees(), TOLERANCE);
        assertEquals(3.0, rotated.uniformScale(), TOLERANCE);
    }

    @Test
    void basisRejectsZeroMirroredAnisotropicShearedAndOverflowedVectors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MapScreenBasis.of(new Coordinate(0.0, 0.0), new Coordinate(0.0, -1.0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MapScreenBasis.of(new Coordinate(1.0, 0.0), new Coordinate(0.0, 1.0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MapScreenBasis.of(new Coordinate(1.0, 0.0), new Coordinate(0.0, -2.0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MapScreenBasis.of(new Coordinate(1.0, 0.0), new Coordinate(1.0, -1.0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MapScreenBasis.of(
                                new Coordinate(Double.MAX_VALUE, 0.0),
                                new Coordinate(0.0, -Double.MAX_VALUE)));
    }

    @Test
    void everyAnchorPlacesItsExactNominalRectanglePointAtTheFeature() {
        EnumMap<SymbolAnchor, Envelope> expected = new EnumMap<>(SymbolAnchor.class);
        expected.put(SymbolAnchor.NORTH_WEST, new Envelope(100.0, 200.0, 120.0, 210.0));
        expected.put(SymbolAnchor.NORTH, new Envelope(90.0, 200.0, 110.0, 210.0));
        expected.put(SymbolAnchor.NORTH_EAST, new Envelope(80.0, 200.0, 100.0, 210.0));
        expected.put(SymbolAnchor.WEST, new Envelope(100.0, 195.0, 120.0, 205.0));
        expected.put(SymbolAnchor.CENTER, new Envelope(90.0, 195.0, 110.0, 205.0));
        expected.put(SymbolAnchor.EAST, new Envelope(80.0, 195.0, 100.0, 205.0));
        expected.put(SymbolAnchor.SOUTH_WEST, new Envelope(100.0, 190.0, 120.0, 200.0));
        expected.put(SymbolAnchor.SOUTH, new Envelope(90.0, 190.0, 110.0, 200.0));
        expected.put(SymbolAnchor.SOUTH_EAST, new Envelope(80.0, 190.0, 100.0, 200.0));

        for (Map.Entry<SymbolAnchor, Envelope> entry : expected.entrySet()) {
            MarkerTransform transform =
                    SymbolTransforms.marker(
                            VIEW_BOX,
                            placement(
                                    new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                    entry.getKey(),
                                    0.0,
                                    0.0,
                                    0.0,
                                    SymbolRotationMode.SCREEN_RELATIVE),
                            FEATURE,
                            BASIS);
            assertEquals(entry.getValue(), transform.nominalScreenBounds(), entry.getKey()::name);
        }
    }

    @Test
    void unitsOffsetsAndRotationProduceExactCoefficientsAndBounds() {
        MarkerTransform mapUnits =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        placement(
                                new SymbolSize(10.0, 5.0, SymbolUnit.MAP_UNIT),
                                SymbolAnchor.CENTER,
                                3.0,
                                4.0,
                                0.0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        FEATURE,
                        BASIS);
        assertEquals(new Envelope(96.0, 187.0, 116.0, 197.0), mapUnits.nominalScreenBounds());
        assertEquals(10.0, mapUnits.m00());
        assertEquals(0.0, mapUnits.m10());
        assertEquals(0.0, mapUnits.m01());
        assertEquals(5.0, mapUnits.m11());
        assertEquals(106.0, mapUnits.m02());
        assertEquals(192.0, mapUnits.m12());

        MarkerTransform rotated =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        placement(
                                new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0.0,
                                0.0,
                                90.0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        FEATURE,
                        BASIS);
        assertEquals(0.0, rotated.m00(), TOLERANCE);
        assertEquals(10.0, rotated.m10(), TOLERANCE);
        assertEquals(-5.0, rotated.m01(), TOLERANCE);
        assertEquals(0.0, rotated.m11(), TOLERANCE);
        assertEnvelope(new Envelope(95.0, 190.0, 105.0, 210.0), rotated.nominalScreenBounds());

        MapScreenBasis rotatedBasis =
                MapScreenBasis.of(new Coordinate(0.0, 2.0), new Coordinate(2.0, 0.0));
        MarkerTransform screenRelative =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        placement(
                                new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0.0,
                                0.0,
                                0.0,
                                SymbolRotationMode.SCREEN_RELATIVE),
                        FEATURE,
                        rotatedBasis);
        MarkerTransform mapRelative =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        placement(
                                new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0.0,
                                0.0,
                                0.0,
                                SymbolRotationMode.MAP_RELATIVE),
                        FEATURE,
                        rotatedBasis);
        assertEquals(10.0, screenRelative.m00(), TOLERANCE);
        assertEquals(0.0, mapRelative.m00(), TOLERANCE);
        assertEquals(10.0, mapRelative.m10(), TOLERANCE);
    }

    @Test
    void nonSquareViewBoxAndZoomProduceExactCoefficientsAndBounds() {
        Envelope nonSquareViewBox = new Envelope(2.0, 4.0, 6.0, 14.0);
        MarkerPlacement screenPlacement =
                placement(
                        new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0.0,
                        0.0,
                        0.0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        MarkerTransform nonSquare =
                SymbolTransforms.marker(nonSquareViewBox, screenPlacement, FEATURE, BASIS);
        assertEquals(5.0, nonSquare.m00());
        assertEquals(0.0, nonSquare.m10());
        assertEquals(0.0, nonSquare.m01());
        assertEquals(1.0, nonSquare.m11());
        assertEquals(80.0, nonSquare.m02());
        assertEquals(191.0, nonSquare.m12());
        assertEquals(new Envelope(90.0, 195.0, 110.0, 205.0), nonSquare.nominalScreenBounds());

        MarkerPlacement mapPlacement =
                placement(
                        new SymbolSize(20.0, 10.0, SymbolUnit.MAP_UNIT),
                        SymbolAnchor.CENTER,
                        0.0,
                        0.0,
                        0.0,
                        SymbolRotationMode.MAP_RELATIVE);
        MarkerTransform zoomedOut =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        mapPlacement,
                        FEATURE,
                        MapScreenBasis.of(new Coordinate(0.5, 0.0), new Coordinate(0.0, -0.5)));
        MarkerTransform zoomedIn =
                SymbolTransforms.marker(
                        VIEW_BOX,
                        mapPlacement,
                        FEATURE,
                        MapScreenBasis.of(new Coordinate(2.0, 0.0), new Coordinate(0.0, -2.0)));
        assertEquals(new Envelope(95.0, 197.5, 105.0, 202.5), zoomedOut.nominalScreenBounds());
        assertEquals(new Envelope(80.0, 190.0, 120.0, 210.0), zoomedIn.nominalScreenBounds());
        assertEquals(4.0, zoomedIn.m00() / zoomedOut.m00(), TOLERANCE);
        assertEquals(4.0, zoomedIn.m11() / zoomedOut.m11(), TOLERANCE);

        MarkerTransform screenAtDifferentZoom =
                SymbolTransforms.marker(
                        nonSquareViewBox,
                        screenPlacement,
                        FEATURE,
                        MapScreenBasis.of(new Coordinate(0.5, 0.0), new Coordinate(0.0, -0.5)));
        assertEquals(nonSquare.nominalScreenBounds(), screenAtDifferentZoom.nominalScreenBounds());
    }

    @Test
    void lengthConversionAndDerivedOverflowUseStableSymbolFailures() {
        assertEquals(
                3.0,
                SymbolTransforms.screenLength(
                        new SymbolLength(3.0, SymbolUnit.SCREEN_PIXEL), BASIS));
        assertEquals(
                6.0,
                SymbolTransforms.screenLength(new SymbolLength(3.0, SymbolUnit.MAP_UNIT), BASIS));

        SymbolException lengthOverflow =
                assertThrows(
                        SymbolException.class,
                        () ->
                                SymbolTransforms.screenLength(
                                        new SymbolLength(Double.MAX_VALUE, SymbolUnit.MAP_UNIT),
                                        BASIS));
        assertEquals(SymbolException.TRANSFORM_NON_FINITE, lengthOverflow.code());
        assertEquals("symbol-screen-length", lengthOverflow.context().get("quantity"));

        SymbolException sizeOverflow =
                assertThrows(
                        SymbolException.class,
                        () ->
                                SymbolTransforms.marker(
                                        VIEW_BOX,
                                        placement(
                                                new SymbolSize(
                                                        Double.MAX_VALUE, 1.0, SymbolUnit.MAP_UNIT),
                                                SymbolAnchor.CENTER,
                                                0.0,
                                                0.0,
                                                0.0,
                                                SymbolRotationMode.SCREEN_RELATIVE),
                                        FEATURE,
                                        BASIS));
        assertEquals(SymbolException.TRANSFORM_NON_FINITE, sizeOverflow.code());
        assertEquals("marker-width", sizeOverflow.context().get("quantity"));
    }

    @Test
    void everyReachableDerivedOverflowStageUsesStableSymbolFailure() {
        double maximum = Double.MAX_VALUE;
        MarkerPlacement oneMapUnit =
                placement(
                        SymbolSize.square(1.0, SymbolUnit.MAP_UNIT),
                        SymbolAnchor.NORTH_WEST,
                        0.0,
                        0.0,
                        0.0,
                        SymbolRotationMode.MAP_RELATIVE);

        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX,
                                placement(
                                        new SymbolSize(1.0, maximum, SymbolUnit.MAP_UNIT),
                                        SymbolAnchor.CENTER,
                                        0.0,
                                        0.0,
                                        0.0,
                                        SymbolRotationMode.SCREEN_RELATIVE),
                                FEATURE,
                                BASIS),
                "marker-height");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX, withOffsets(oneMapUnit, maximum, 0.0), FEATURE, BASIS),
                "marker-offset-x-product");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX, withOffsets(oneMapUnit, 0.0, maximum), FEATURE, BASIS),
                "marker-offset-y-product");
        MapScreenBasis diagonalDown =
                MapScreenBasis.of(new Coordinate(1.0, 1.0), new Coordinate(1.0, -1.0));
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX,
                                withOffsets(oneMapUnit, maximum, maximum),
                                FEATURE,
                                diagonalDown),
                "marker-offset-screen-x");
        MapScreenBasis diagonalUp =
                MapScreenBasis.of(new Coordinate(1.0, -1.0), new Coordinate(-1.0, -1.0));
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX,
                                withOffsets(oneMapUnit, -maximum, -maximum),
                                FEATURE,
                                diagonalUp),
                "marker-offset-screen-y");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX,
                                withOffsets(
                                        screenPlacement(SymbolAnchor.NORTH_WEST, 1.0, 1.0),
                                        maximum,
                                        0.0),
                                new Coordinate(maximum, 0.0),
                                BASIS),
                "marker-anchor-x");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                VIEW_BOX,
                                withOffsets(
                                        screenPlacement(SymbolAnchor.NORTH_WEST, 1.0, 1.0),
                                        0.0,
                                        maximum),
                                new Coordinate(0.0, maximum),
                                BASIS),
                "marker-anchor-y");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, Double.MIN_VALUE, 1.0),
                                screenPlacement(SymbolAnchor.NORTH_WEST, maximum, 1.0),
                                FEATURE,
                                BASIS),
                "marker-scale-x");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, 1.0, Double.MIN_VALUE),
                                screenPlacement(SymbolAnchor.NORTH_WEST, 1.0, maximum),
                                FEATURE,
                                BASIS),
                "marker-scale-y");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(maximum / 2.0, 0.0, maximum * 0.75, 1.0),
                                screenPlacement(SymbolAnchor.NORTH_WEST, maximum, 1.0),
                                FEATURE,
                                BASIS),
                "marker-viewbox-translation-x");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, maximum / 2.0, 1.0, maximum * 0.75),
                                screenPlacement(SymbolAnchor.NORTH_WEST, 1.0, maximum),
                                FEATURE,
                                BASIS),
                "marker-viewbox-translation-y");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(maximum * 0.6, 0.0, maximum, 1.0),
                                screenPlacement(SymbolAnchor.EAST, maximum * 0.5, 1.0),
                                FEATURE,
                                BASIS),
                "marker-local-translation-x");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, maximum * 0.6, 1.0, maximum),
                                screenPlacement(SymbolAnchor.SOUTH, 1.0, maximum * 0.5),
                                FEATURE,
                                BASIS),
                "marker-local-translation-y");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, 1.0, 1.0),
                                rotatedScreenPlacement(
                                        SymbolAnchor.EAST, maximum * 0.5, 1.0, 180.0),
                                new Coordinate(maximum * 0.75, 0.0),
                                BASIS),
                "marker-m02");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, 1.0, 1.0),
                                rotatedScreenPlacement(
                                        SymbolAnchor.SOUTH, 1.0, maximum * 0.5, 180.0),
                                new Coordinate(0.0, maximum * 0.75),
                                BASIS),
                "marker-m12");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(maximum * 0.4, 0.0, maximum, 1.0),
                                screenPlacement(SymbolAnchor.NORTH_WEST, maximum * 0.9, 1.0),
                                new Coordinate(0.0, 0.0),
                                BASIS),
                "marker-corner-x-product");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, maximum * 0.4, 1.0, maximum),
                                screenPlacement(SymbolAnchor.NORTH_WEST, 1.0, maximum * 0.9),
                                new Coordinate(0.0, 0.0),
                                BASIS),
                "marker-corner-y-product");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, maximum / 2.0, maximum / 2.0),
                                rotatedScreenPlacement(
                                        SymbolAnchor.NORTH_WEST, maximum, maximum, 315.0),
                                new Coordinate(0.0, 0.0),
                                BASIS),
                "marker-corner-x");
        assertQuantity(
                () ->
                        SymbolTransforms.marker(
                                new Envelope(0.0, 0.0, maximum / 2.0, maximum / 2.0),
                                rotatedScreenPlacement(
                                        SymbolAnchor.NORTH_WEST, maximum, maximum, 45.0),
                                new Coordinate(0.0, 0.0),
                                BASIS),
                "marker-corner-y");
    }

    private static MarkerPlacement screenPlacement(
            SymbolAnchor anchor, double width, double height) {
        return rotatedScreenPlacement(anchor, width, height, 0.0);
    }

    private static MarkerPlacement rotatedScreenPlacement(
            SymbolAnchor anchor, double width, double height, double rotation) {
        return placement(
                new SymbolSize(width, height, SymbolUnit.SCREEN_PIXEL),
                anchor,
                0.0,
                0.0,
                rotation,
                SymbolRotationMode.SCREEN_RELATIVE);
    }

    private static MarkerPlacement withOffsets(
            MarkerPlacement placement, double offsetX, double offsetY) {
        return new MarkerPlacement(
                placement.size(),
                placement.anchor(),
                offsetX,
                offsetY,
                placement.rotationDegrees(),
                placement.rotationMode());
    }

    private static void assertQuantity(Executable executable, String expectedQuantity) {
        SymbolException failure = assertThrows(SymbolException.class, executable);
        assertEquals(SymbolException.TRANSFORM_NON_FINITE, failure.code());
        assertEquals(expectedQuantity, failure.context().get("quantity"));
    }

    private static MarkerPlacement placement(
            SymbolSize size,
            SymbolAnchor anchor,
            double offsetX,
            double offsetY,
            double rotation,
            SymbolRotationMode rotationMode) {
        return new MarkerPlacement(size, anchor, offsetX, offsetY, rotation, rotationMode);
    }

    private static void assertEnvelope(Envelope expected, Envelope actual) {
        assertEquals(expected.minX(), actual.minX(), TOLERANCE);
        assertEquals(expected.minY(), actual.minY(), TOLERANCE);
        assertEquals(expected.maxX(), actual.maxX(), TOLERANCE);
        assertEquals(expected.maxY(), actual.maxY(), TOLERANCE);
    }
}
