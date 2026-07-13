package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.github.mundanej.map.api.VectorPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorPath2DTest {
    @Test
    void convertsEverySegmentAndKeepsOpenSubpathsOutOfTheFillPath() {
        VectorPath source =
                VectorPath.builder()
                        .moveTo(0.0, 0.0)
                        .lineTo(2.0, 0.0)
                        .quadraticTo(3.0, 1.0, 2.0, 2.0)
                        .cubicTo(1.5, 3.0, 0.5, 3.0, 0.0, 2.0)
                        .close()
                        .moveTo(10.0, 10.0)
                        .lineTo(20.0, 20.0)
                        .build();

        VectorPath2D.Converted converted = VectorPath2D.convert(source);

        assertEquals(Path2D.WIND_EVEN_ODD, converted.strokePath().getWindingRule());
        assertEquals(Path2D.WIND_EVEN_ODD, converted.fillPath().getWindingRule());
        List<Segment> strokeSegments = segments(converted.strokePath());
        assertEquals(
                List.of(
                        PathIterator.SEG_MOVETO,
                        PathIterator.SEG_LINETO,
                        PathIterator.SEG_QUADTO,
                        PathIterator.SEG_CUBICTO,
                        PathIterator.SEG_CLOSE,
                        PathIterator.SEG_MOVETO,
                        PathIterator.SEG_LINETO),
                strokeSegments.stream().map(Segment::kind).toList());
        assertArrayEquals(new double[] {3.0, 1.0, 2.0, 2.0}, strokeSegments.get(2).coordinates());
        assertArrayEquals(
                new double[] {1.5, 3.0, 0.5, 3.0, 0.0, 2.0}, strokeSegments.get(3).coordinates());
        assertEquals(
                List.of(
                        PathIterator.SEG_MOVETO,
                        PathIterator.SEG_LINETO,
                        PathIterator.SEG_QUADTO,
                        PathIterator.SEG_CUBICTO,
                        PathIterator.SEG_CLOSE),
                segments(converted.fillPath()).stream().map(Segment::kind).toList());
        assertEquals(0.0, converted.fillPath().getBounds2D().getMinX());
        assertEquals(2.75, converted.fillPath().getBounds2D().getMaxY(), 1.0e-12);
    }

    @Test
    void eachConversionReturnsIndependentJava2dValues() {
        VectorPath source = VectorPath.builder().moveTo(0.0, 0.0).lineTo(1.0, 1.0).close().build();

        VectorPath2D.Converted first = VectorPath2D.convert(source);
        VectorPath2D.Converted second = VectorPath2D.convert(source);

        assertNotSame(first.strokePath(), second.strokePath());
        assertNotSame(first.fillPath(), second.fillPath());
    }

    private static List<Segment> segments(Path2D path) {
        List<Segment> result = new ArrayList<>();
        PathIterator iterator = path.getPathIterator(null);
        double[] coordinates = new double[6];
        while (!iterator.isDone()) {
            int kind = iterator.currentSegment(coordinates);
            int length =
                    switch (kind) {
                        case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 2;
                        case PathIterator.SEG_QUADTO -> 4;
                        case PathIterator.SEG_CUBICTO -> 6;
                        case PathIterator.SEG_CLOSE -> 0;
                        default -> throw new AssertionError("Unknown Java2D path segment");
                    };
            result.add(new Segment(kind, java.util.Arrays.copyOf(coordinates, length)));
            iterator.next();
        }
        return result;
    }

    private static final class Segment {
        private final int kind;
        private final double[] coordinates;

        private Segment(int kind, double[] coordinates) {
            this.kind = kind;
            this.coordinates = coordinates;
        }

        int kind() {
            return kind;
        }

        double[] coordinates() {
            return coordinates;
        }
    }
}
