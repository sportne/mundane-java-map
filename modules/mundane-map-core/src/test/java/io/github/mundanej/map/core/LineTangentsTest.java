package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolException;
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
    void overflowHasStableFeaturePartAndEndpointContext() {
        SymbolException failure =
                assertThrows(
                        SymbolException.class,
                        () ->
                                LineTangents.outwardScreenBearings(
                                        CoordinateSequence.of(
                                                Double.MAX_VALUE, 0.0, -Double.MAX_VALUE, 0.0),
                                        "overflow-road",
                                        7));
        assertEquals(SymbolException.TRANSFORM_NON_FINITE, failure.code());
        assertEquals("overflow-road", failure.context().get("featureId"));
        assertEquals("7", failure.context().get("partIndex"));
        assertEquals("start", failure.context().get("endpoint"));
        assertEquals("line-tangent-delta", failure.context().get("quantity"));
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
