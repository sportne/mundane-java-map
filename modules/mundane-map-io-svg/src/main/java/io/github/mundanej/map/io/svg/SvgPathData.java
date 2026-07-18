package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.VectorPathCommand;

@SuppressWarnings("ArrayRecordComponent")
record SvgPathData(
        VectorPathCommand[] commands,
        double[] ordinates,
        int drawingSegments,
        boolean hasOpenSubpath) {}
