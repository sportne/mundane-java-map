package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.Envelope;

/** Cursor-confined common multipart arrays after complete validation. */
final class ShpMultipartPayload {
    private final double[] packedCoordinates;
    private final int[] fenceposts;
    private final Envelope recordBox;
    private final Envelope coordinateEnvelope;

    ShpMultipartPayload(
            double[] packedCoordinates,
            int[] fenceposts,
            Envelope recordBox,
            Envelope coordinateEnvelope) {
        this.packedCoordinates = packedCoordinates;
        this.fenceposts = fenceposts;
        this.recordBox = recordBox;
        this.coordinateEnvelope = coordinateEnvelope;
    }

    double[] packedCoordinates() {
        return packedCoordinates;
    }

    int[] fenceposts() {
        return fenceposts;
    }

    Envelope recordBox() {
        return recordBox;
    }

    Envelope coordinateEnvelope() {
        return coordinateEnvelope;
    }
}
