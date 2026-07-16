package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import org.junit.jupiter.api.Test;

class LineTangentsTest {
    @Test
    void findsOutwardBearingsAcrossLeadingAndTrailingRepeats() {
        LineEndpointBearings horizontal =
                LineTangents.outwardScreenBearings(
                        CoordinateSequence.of(0.0, 0.0, -0.0, 0.0, 10.0, 0.0, 10.0, -0.0),
                        "road",
                        2);
        assertEquals(180.0, horizontal.startBearingDegrees().orElseThrow());
        assertEquals(0.0, horizontal.endBearingDegrees().orElseThrow());

        LineEndpointBearings vertical =
                LineTangents.outwardScreenBearings(
                        CoordinateSequence.of(0.0, 0.0, 0.0, 10.0), "road", 0);
        assertEquals(270.0, vertical.startBearingDegrees().orElseThrow());
        assertEquals(90.0, vertical.endBearingDegrees().orElseThrow());
    }

    @Test
    void allCoincidentPartHasNoEndpointBearings() {
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(
                        CoordinateSequence.of(3.0, 4.0, 3.0, 4.0, 3.0, 4.0), "point-line", 0);
        assertFalse(bearings.startBearingDegrees().isPresent());
        assertFalse(bearings.endBearingDegrees().isPresent());
    }

    @Test
    void packedRangeMatchesAnEquivalentStandalonePartWithoutCopyingSemantics() {
        CoordinateSequence packed =
                CoordinateSequence.of(
                        -100.0, -100.0, 2.0, 3.0, 2.0, 3.0, 8.0, 3.0, 8.0, 9.0, 100.0, 100.0);
        CoordinateSequence standalone =
                CoordinateSequence.of(2.0, 3.0, 2.0, 3.0, 8.0, 3.0, 8.0, 9.0);

        assertEquals(
                LineTangents.outwardScreenBearings(standalone, "road", 4),
                LineTangents.outwardScreenBearings(packed, 1, 5, "road", 4));
    }

    @Test
    void packedRangeValidatesBothFencepostsAndRequiresOneCoordinate() {
        CoordinateSequence coordinates = CoordinateSequence.of(0.0, 0.0, 1.0, 1.0);

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> LineTangents.outwardScreenBearings(coordinates, -1, 1, "road", 0));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> LineTangents.outwardScreenBearings(coordinates, 0, 3, "road", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LineTangents.outwardScreenBearings(coordinates, 1, 1, "road", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LineTangents.outwardScreenBearings(coordinates, 2, 1, "road", 0));

        LineEndpointBearings single =
                LineTangents.outwardScreenBearings(coordinates, 1, 2, "road", 0);
        assertFalse(single.startBearingDegrees().isPresent());
        assertFalse(single.endBearingDegrees().isPresent());
    }

    @Test
    void coordinateSequenceRejectsAnUnrepresentableEnvelopeBeforeTangentWork() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CoordinateSequence.of(Double.MAX_VALUE, 0.0, -Double.MAX_VALUE, 0.0));
    }

    @Test
    void endpointBearingOverridesPlacementRotationModeAndAddsConfiguredRotationOnce() {
        MarkerPlacement placement =
                new MarkerPlacement(
                        new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0.0,
                        0.0,
                        90.0,
                        SymbolRotationMode.MAP_RELATIVE);
        MarkerTransform transform =
                SymbolTransforms.markerAtScreenBearing(
                        new Envelope(-1.0, -1.0, 1.0, 1.0),
                        placement,
                        new Coordinate(50.0, 50.0),
                        MapScreenBasis.of(new Coordinate(0.0, 2.0), new Coordinate(2.0, 0.0)),
                        0.0);

        assertEquals(0.0, transform.m00(), 1.0e-12);
        assertEquals(10.0, transform.m10(), 1.0e-12);
        assertEquals(-5.0, transform.m01(), 1.0e-12);
    }
}
