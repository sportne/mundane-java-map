package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class ShpMultipartPayloadTest {
    @Test
    void validatedCursorPayloadReturnsItsConfinedArraysAndEnvelopes() {
        double[] coordinates = {1, 2, 3, 4};
        int[] fenceposts = {0, 2};
        Envelope record = new Envelope(0, 0, 4, 5);
        Envelope coordinate = new Envelope(1, 2, 3, 4);
        ShpMultipartPayload payload =
                new ShpMultipartPayload(coordinates, fenceposts, record, coordinate);

        assertSame(coordinates, payload.packedCoordinates());
        assertSame(fenceposts, payload.fenceposts());
        assertSame(record, payload.recordBox());
        assertSame(coordinate, payload.coordinateEnvelope());
    }
}
