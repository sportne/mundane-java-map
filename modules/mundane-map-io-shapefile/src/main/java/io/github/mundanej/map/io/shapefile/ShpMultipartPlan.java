package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.Envelope;

/** Scalar-only result of validating a common SHP multipart prefix. */
record ShpMultipartPlan(
        long record,
        long recordStart,
        long contentBytes,
        int partCount,
        int pointCount,
        long expectedBytes,
        long partTableStart,
        long coordinateStart,
        Envelope recordBox,
        int minimumCoordinatesPerPart,
        String aggregateCode,
        String aggregateReason,
        String spanCode,
        String spanReason) {}
