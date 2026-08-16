package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryException;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.OrdinateLossPolicy;
import io.github.mundanej.map.api.PointGeometry;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryDownProjectionTest {
    @Test
    void xyValuesAreReturnedWithoutAllocation() {
        Geometry point = new PointGeometry(new Coordinate(1, 2));

        assertSame(point, GeometryDownProjection.toXy(point, OrdinateLossPolicy.REJECT));
    }

    @Test
    void rejectionUsesStableDiagnostics() {
        Geometry point =
                DimensionalGeometry.point(CoordinateSequence.of(GeometryDimension.XYZ, 1, 2, 3));

        GeometryException failure =
                assertThrows(
                        GeometryException.class,
                        () -> GeometryDownProjection.toXy(point, OrdinateLossPolicy.REJECT));

        assertEquals(GeometryException.ORDINATE_LOSS_REJECTED, failure.code());
        assertEquals("XYZ", failure.context().get("dimension"));
    }

    @Test
    void namedLossPolicyPreservesKindsOffsetsEmptiesAndOrder() {
        DimensionalGeometry polygon =
                DimensionalGeometry.polygon(
                        CoordinateSequence.of(
                                GeometryDimension.XYZM,
                                0,
                                0,
                                1,
                                2,
                                2,
                                0,
                                3,
                                4,
                                2,
                                2,
                                5,
                                6,
                                0,
                                0,
                                1,
                                2),
                        new int[] {0, 4});
        GeometryCollection collection =
                GeometryCollection.of(
                        List.of(
                                new EmptyGeometry(GeometryKind.LINE_STRING, GeometryDimension.XYM),
                                polygon));

        GeometryCollection result =
                (GeometryCollection)
                        GeometryDownProjection.toXy(collection, OrdinateLossPolicy.DROP_TO_XY);
        EmptyGeometry empty = (EmptyGeometry) result.geometries().get(0);
        DimensionalGeometry projected = (DimensionalGeometry) result.geometries().get(1);

        assertEquals(GeometryDimension.XY, result.dimension());
        assertEquals(GeometryKind.LINE_STRING, empty.kind());
        assertEquals(GeometryDimension.XY, empty.dimension());
        assertEquals(GeometryKind.POLYGON, projected.kind());
        assertArrayEquals(new int[] {0, 4}, projected.partOffsets());
        assertArrayEquals(new double[] {0, 0, 2, 0, 2, 2, 0, 0}, projected.coordinates().toArray());
    }
}
