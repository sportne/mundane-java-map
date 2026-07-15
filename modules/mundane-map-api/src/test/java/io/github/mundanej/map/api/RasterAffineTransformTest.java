package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RasterAffineTransformTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("raster", "Raster");

    @Test
    void worldFileCoefficientsRoundTripGridCenters() {
        RasterAffineTransform transform = RasterAffineTransform.of(2.0, 0.5, -0.25, -3.0, 100, 200);
        assertEquals(2.0, transform.a());
        assertEquals(0.5, transform.d());
        assertEquals(-0.25, transform.b());
        assertEquals(-3.0, transform.e());
        assertEquals(100, transform.c());
        assertEquals(200, transform.f());

        Coordinate map = transform.gridToMap(3, 4);
        assertEquals(new Coordinate(105, 189.5), map);
        Coordinate grid = transform.mapToGrid(map);
        assertEquals(3.0, grid.x(), 1.0e-12);
        assertEquals(4.0, grid.y(), 1.0e-12);
    }

    @Test
    void scaledInverseSupportsVeryLargeAndVerySmallLinearUnits() {
        RasterAffineTransform large = RasterAffineTransform.of(8.0e307, 0, 0, -4.0e307, 0, 0);
        Coordinate largeMap = large.gridToMap(0.5, 0.5);
        assertEquals(0.5, large.mapToGrid(largeMap).x(), 1.0e-15);
        assertEquals(0.5, large.mapToGrid(largeMap).y(), 1.0e-15);

        RasterAffineTransform small = RasterAffineTransform.of(1.0e-300, 0, 0, 2.0e-300, 0, 0);
        Coordinate smallMap = small.gridToMap(0.5, 0.5);
        assertEquals(0.5, small.mapToGrid(smallMap).x(), 1.0e-15);
        assertEquals(0.5, small.mapToGrid(smallMap).y(), 1.0e-15);
    }

    @Test
    void invalidCoefficientsSingularityAndUnrepresentableInverseAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterAffineTransform.of(Double.NaN, 0, 0, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterAffineTransform.of(1, 0, 0, Double.POSITIVE_INFINITY, 0, 0));
        RasterPlacementException singular =
                assertThrows(
                        RasterPlacementException.class,
                        () -> RasterAffineTransform.of(1, 2, 2, 4, 0, 0));
        assertEquals(RasterPlacementException.Reason.SINGULAR, singular.reason());
        RasterPlacementException inverse =
                assertThrows(
                        RasterPlacementException.class,
                        () ->
                                RasterAffineTransform.of(
                                        Double.MIN_VALUE, 0, 0, Double.MIN_VALUE, 0, 0));
        assertEquals(RasterPlacementException.Reason.INVERSE_NON_FINITE, inverse.reason());
        RasterPlacementException translation =
                assertThrows(
                        RasterPlacementException.class,
                        () -> RasterAffineTransform.of(1.0e-300, 0, 0, 1, Double.MAX_VALUE, 0));
        assertEquals(RasterPlacementException.Reason.INVERSE_NON_FINITE, translation.reason());
    }

    @Test
    void transformOperationsRejectNonFiniteInputsAndResults() {
        RasterAffineTransform transform = RasterAffineTransform.of(2, 0, 0, 2, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> transform.gridToMap(Double.NaN, 0));
        assertThrows(
                ArithmeticException.class,
                () -> transform.gridToMap(Double.MAX_VALUE, Double.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> transform.mapToGrid(null));
    }

    @Test
    void signedZeroIsCanonicalForValueSemantics() {
        RasterAffineTransform negativeZero = RasterAffineTransform.of(1, -0.0, -0.0, 1, -0.0, -0.0);
        RasterAffineTransform positiveZero = RasterAffineTransform.of(1, 0.0, 0.0, 1, 0.0, 0.0);
        assertEquals(positiveZero, negativeZero);
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
        assertEquals(0L, Double.doubleToRawLongBits(negativeZero.c()));
        assertTrue(negativeZero.toString().contains("a=1.0"));
        assertNotEquals(positiveZero, RasterAffineTransform.of(2, 0, 0, 1, 0, 0));
    }

    @Test
    void taggedPlacementExposesOnlyItsOwnedPayload() {
        Envelope bounds = new Envelope(1, 2, 3, 4);
        RasterGridPlacement axis = RasterGridPlacement.axisAligned(bounds);
        assertEquals(RasterGridPlacement.Kind.AXIS_ALIGNED, axis.kind());
        assertEquals(Optional.of(bounds), axis.axisAlignedBounds());
        assertTrue(axis.affineTransform().isEmpty());

        RasterAffineTransform transform = RasterAffineTransform.of(1, 0, 0, -1, 0, 0);
        RasterGridPlacement affine = RasterGridPlacement.affine(transform);
        assertEquals(RasterGridPlacement.Kind.AFFINE, affine.kind());
        assertTrue(affine.axisAlignedBounds().isEmpty());
        assertEquals(Optional.of(transform), affine.affineTransform());
        assertEquals(affine, RasterGridPlacement.affine(transform));
        assertFalse(axis.equals(affine));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterGridPlacement.axisAligned(new Envelope(1, 1, 1, 2)));
        assertThrows(NullPointerException.class, () -> RasterGridPlacement.affine(null));
    }

    @Test
    void compatibleMetadataConstructionCreatesExactAxisPlacement() {
        Envelope bounds = new Envelope(10, 20, 14, 23);
        RasterSourceMetadata metadata =
                new RasterSourceMetadata(IDENTITY, 4, 3, Optional.of(bounds), Optional.empty());
        assertEquals(Optional.of(bounds), metadata.mapBounds());
        assertEquals(
                Optional.of(RasterGridPlacement.axisAligned(bounds)), metadata.gridPlacement());
        assertEquals(
                metadata,
                RasterSourceMetadata.withPlacement(
                        IDENTITY, 4, 3, RasterGridPlacement.axisAligned(bounds), Optional.empty()));

        RasterSourceMetadata unplaced =
                new RasterSourceMetadata(IDENTITY, 4, 3, Optional.empty(), Optional.empty());
        assertTrue(unplaced.mapBounds().isEmpty());
        assertTrue(unplaced.gridPlacement().isEmpty());
    }

    @Test
    void affineMetadataDerivesHalfPixelExpandedEnvelope() {
        RasterAffineTransform northUp = RasterAffineTransform.of(2, 0, 0, -3, 101, 198.5);
        RasterSourceMetadata metadata =
                RasterSourceMetadata.withPlacement(
                        IDENTITY, 4, 3, RasterGridPlacement.affine(northUp), Optional.empty());
        assertEquals(Optional.of(new Envelope(100, 191, 108, 200)), metadata.mapBounds());

        RasterAffineTransform rotated = RasterAffineTransform.of(1, 1, -1, 1, 0, 0);
        RasterSourceMetadata rotatedMetadata =
                RasterSourceMetadata.withPlacement(
                        IDENTITY, 2, 1, RasterGridPlacement.affine(rotated), Optional.empty());
        assertEquals(new Envelope(-1, -1, 2, 2), rotatedMetadata.mapBounds().orElseThrow());
        assertEquals(
                Optional.of(RasterGridPlacement.affine(rotated)), rotatedMetadata.gridPlacement());
    }

    @Test
    void affineMetadataRejectsTranslationCollapsedFootprints() {
        RasterAffineTransform collapsed = RasterAffineTransform.of(1, 0, 0, 1, 1.0e16, 1.0e16);
        RasterPlacementException failure =
                assertThrows(
                        RasterPlacementException.class,
                        () ->
                                RasterSourceMetadata.withPlacement(
                                        IDENTITY,
                                        1,
                                        1,
                                        RasterGridPlacement.affine(collapsed),
                                        Optional.empty()));
        assertEquals(RasterPlacementException.Reason.ENVELOPE_NON_POSITIVE, failure.reason());
    }

    @Test
    void affineMetadataDistinguishesCornerEdgeAndEnvelopeNonFiniteFailures() {
        RasterAffineTransform corner =
                RasterAffineTransform.of(Double.MAX_VALUE, 0, 0, 1, -Double.MAX_VALUE, 0);
        assertPlacementReason(RasterPlacementException.Reason.CORNER_NON_FINITE, corner, 3, 1);

        RasterAffineTransform edge =
                RasterAffineTransform.of(Double.MAX_VALUE, 0, 0, 1, -0.5 * Double.MAX_VALUE, 0);
        assertPlacementReason(RasterPlacementException.Reason.ENVELOPE_NON_FINITE, edge, 2, 1);

        double wide = 0.75 * Double.MAX_VALUE;
        RasterAffineTransform envelope = RasterAffineTransform.of(wide, wide, wide, -wide, 0, 0);
        assertPlacementReason(RasterPlacementException.Reason.ENVELOPE_NON_FINITE, envelope, 1, 1);
    }

    private static void assertPlacementReason(
            RasterPlacementException.Reason reason,
            RasterAffineTransform transform,
            int width,
            int height) {
        RasterPlacementException failure =
                assertThrows(
                        RasterPlacementException.class,
                        () ->
                                RasterSourceMetadata.withPlacement(
                                        IDENTITY,
                                        width,
                                        height,
                                        RasterGridPlacement.affine(transform),
                                        Optional.empty()));
        assertEquals(reason, failure.reason());
    }
}
