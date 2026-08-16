package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.GeometryLimits;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dimension-preserving clipping to a closed axis-aligned x/y envelope. */
@SuppressWarnings("StringConcatToTextBlock")
public final class GeometryEnvelopeClipper {
    private GeometryEnvelopeClipper() {}

    /**
     * Clips a geometry using default prospective limits.
     *
     * <p>Typed emptiness and collection nesting/order are preserved. Existing vertices retain all
     * ordinates; new boundary vertices linearly interpolate Z and M. A split line may become a
     * multiline. Polygon clipping uses the deterministic Sutherland-Hodgman envelope overlay and
     * preserves ring encounter order. No repair is performed.
     *
     * @param geometry immutable input
     * @param envelope closed clipping envelope
     * @return complete clipped geometry
     */
    public static Geometry clip(Geometry geometry, Envelope envelope) {
        return clip(geometry, envelope, GeometryTopologyLimits.DEFAULT);
    }

    /**
     * Clips a geometry under explicit prospective input, work, and output limits.
     *
     * @param geometry immutable input
     * @param envelope closed clipping envelope
     * @param limits prospective limits
     * @return complete clipped geometry
     * @throws GeometryTopologyException when a limit is exceeded before publication
     */
    public static Geometry clip(
            Geometry geometry, Envelope envelope, GeometryTopologyLimits limits) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(envelope, "envelope");
        ClipWork work = new ClipWork(Objects.requireNonNull(limits, "limits"));
        return clipGeometry(geometry, envelope, work);
    }

    private static Geometry clipGeometry(Geometry geometry, Envelope envelope, ClipWork work) {
        if (geometry instanceof EmptyGeometry) {
            return geometry;
        }
        if (geometry instanceof GeometryCollection collection) {
            List<Geometry> children = new ArrayList<>(collection.geometries().size());
            for (Geometry child : collection.geometries()) {
                children.add(clipGeometry(child, envelope, work));
            }
            return children.isEmpty()
                    ? GeometryCollection.empty(collection.dimension())
                    : GeometryCollection.of(children);
        }
        GeometryValidity.Shape shape = GeometryValidity.Shape.of(geometry, work.topology);
        return switch (geometry.kind()) {
            case POINT, MULTI_POINT -> clipPoints(geometry, shape, envelope, work);
            case LINE_STRING, MULTI_LINE_STRING -> clipLines(geometry, shape, envelope, work);
            case POLYGON, MULTI_POLYGON -> clipPolygons(geometry, shape, envelope, work);
            case GEOMETRY_COLLECTION -> throw new IllegalStateException("Collection handled first");
        };
    }

    private static Geometry clipPoints(
            Geometry source, GeometryValidity.Shape shape, Envelope envelope, ClipWork work) {
        int stride = shape.coordinates.dimension().stride();
        List<double[]> kept = new ArrayList<>();
        for (int index = 0; index < shape.coordinates.size(); index++) {
            if (contains(envelope, shape.coordinates.x(index), shape.coordinates.y(index))) {
                kept.add(position(shape.coordinates, index));
            }
        }
        if (kept.isEmpty()) {
            return new EmptyGeometry(source.kind(), source.dimension());
        }
        work.output(kept.size());
        CoordinateSequence coordinates = sequence(shape.coordinates.dimension(), kept, stride);
        if (source instanceof DimensionalGeometry) {
            return source.kind() == GeometryKind.POINT
                    ? DimensionalGeometry.point(coordinates)
                    : DimensionalGeometry.multiPoint(coordinates);
        }
        if (source.kind() == GeometryKind.POINT) {
            return source;
        }
        return new MultiPointGeometry(coordinates);
    }

    private static Geometry clipLines(
            Geometry source, GeometryValidity.Shape shape, Envelope envelope, ClipWork work) {
        List<List<double[]>> fragments = new ArrayList<>();
        for (GeometryValidity.Part part : shape.parts) {
            List<double[]> active = new ArrayList<>();
            for (int segment = 0; segment < part.size() - 1; segment++) {
                work.topology.comparison();
                Segment clipped = clipSegment(part, segment, envelope);
                if (clipped == null) {
                    publish(active, fragments);
                    active = new ArrayList<>();
                    continue;
                }
                if (!active.isEmpty() && !same(active.getLast(), clipped.start)) {
                    publish(active, fragments);
                    active = new ArrayList<>();
                }
                appendDistinct(active, clipped.start);
                appendDistinct(active, clipped.end);
            }
            publish(active, fragments);
        }
        if (fragments.isEmpty()) {
            return new EmptyGeometry(source.kind(), source.dimension());
        }
        int count = fragments.stream().mapToInt(List::size).sum();
        work.output(count);
        GeometryDimension dimension = shape.coordinates.dimension();
        CoordinateSequence coordinates =
                sequence(dimension, flatten(fragments), dimension.stride());
        int[] offsets = offsets(fragments);
        if (source instanceof DimensionalGeometry) {
            if (source.kind() == GeometryKind.LINE_STRING && fragments.size() == 1) {
                return DimensionalGeometry.lineString(coordinates);
            }
            return DimensionalGeometry.multiLineString(coordinates, offsets);
        }
        if (source.kind() == GeometryKind.LINE_STRING && fragments.size() == 1) {
            return new LineStringGeometry(coordinates);
        }
        return MultiLineStringGeometry.of(coordinates, offsets);
    }

    private static Geometry clipPolygons(
            Geometry source, GeometryValidity.Shape shape, Envelope envelope, ClipWork work) {
        List<List<List<double[]>>> polygons = new ArrayList<>();
        for (List<GeometryValidity.Part> polygon : shape.polygons) {
            List<double[]> shell = clipRing(polygon.getFirst(), envelope, work);
            if (shell.size() < 4 || signedArea(shell) == 0.0) {
                continue;
            }
            List<List<double[]>> rings = new ArrayList<>();
            rings.add(shell);
            for (int hole = 1; hole < polygon.size(); hole++) {
                List<double[]> clippedHole = clipRing(polygon.get(hole), envelope, work);
                if (clippedHole.size() >= 4
                        && signedArea(clippedHole) != 0.0
                        && pointInRing(clippedHole.getFirst(), shell)) {
                    rings.add(clippedHole);
                }
            }
            polygons.add(rings);
        }
        if (polygons.isEmpty()) {
            return new EmptyGeometry(source.kind(), source.dimension());
        }
        List<List<double[]>> rings = polygons.stream().flatMap(List::stream).toList();
        int count = rings.stream().mapToInt(List::size).sum();
        work.output(count);
        GeometryDimension dimension = shape.coordinates.dimension();
        CoordinateSequence coordinates = sequence(dimension, flatten(rings), dimension.stride());
        int[] ringOffsets = offsets(rings);
        int[] polygonOffsets = new int[polygons.size() + 1];
        for (int index = 0; index < polygons.size(); index++) {
            polygonOffsets[index + 1] = polygonOffsets[index] + polygons.get(index).size();
        }
        if (source instanceof DimensionalGeometry) {
            if (source.kind() == GeometryKind.POLYGON) {
                return DimensionalGeometry.polygon(coordinates, ringOffsets);
            }
            return DimensionalGeometry.multiPolygon(
                    coordinates, ringOffsets, polygonOffsets, GeometryLimits.DEFAULT);
        }
        if (source.kind() == GeometryKind.POLYGON) {
            return polygonGeometry(rings);
        }
        List<PolygonGeometry> values = new ArrayList<>(polygons.size());
        for (List<List<double[]>> polygon : polygons) {
            values.add(polygonGeometry(polygon));
        }
        return MultiPolygonGeometry.ofPolygons(values);
    }

    private static PolygonGeometry polygonGeometry(List<List<double[]>> rings) {
        CoordinateSequence shell = sequence(GeometryDimension.XY, rings.getFirst(), 2);
        List<CoordinateSequence> holes = new ArrayList<>();
        for (int index = 1; index < rings.size(); index++) {
            holes.add(sequence(GeometryDimension.XY, rings.get(index), 2));
        }
        return new PolygonGeometry(shell, holes);
    }

    private static List<double[]> clipRing(
            GeometryValidity.Part ring, Envelope envelope, ClipWork work) {
        List<double[]> values = new ArrayList<>(ring.size());
        for (int index = 0; index < ring.size() - 1; index++) {
            values.add(position(ring.coordinates, ring.start + index));
        }
        for (Boundary boundary : Boundary.values()) {
            if (values.isEmpty()) {
                break;
            }
            List<double[]> output = new ArrayList<>();
            double[] previous = values.getLast();
            boolean previousInside = boundary.inside(previous, envelope);
            for (double[] current : values) {
                work.topology.comparison();
                boolean currentInside = boundary.inside(current, envelope);
                if (currentInside != previousInside) {
                    output.add(boundary.intersection(previous, current, envelope));
                }
                if (currentInside) {
                    output.add(current.clone());
                }
                previous = current;
                previousInside = currentInside;
            }
            values = output;
        }
        if (!values.isEmpty()) {
            appendDistinct(values, values.getFirst().clone());
        }
        return values;
    }

    private static Segment clipSegment(GeometryValidity.Part part, int segment, Envelope envelope) {
        double[] start = position(part.coordinates, part.start + segment);
        double[] end = position(part.coordinates, part.start + segment + 1);
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double[] range = {0.0, 1.0};
        if (!clipParameter(-dx, start[0] - envelope.minX(), range)
                || !clipParameter(dx, envelope.maxX() - start[0], range)
                || !clipParameter(-dy, start[1] - envelope.minY(), range)
                || !clipParameter(dy, envelope.maxY() - start[1], range)) {
            return null;
        }
        return new Segment(interpolate(start, end, range[0]), interpolate(start, end, range[1]));
    }

    private static boolean clipParameter(double direction, double distance, double[] range) {
        if (direction == 0.0) {
            return distance >= 0.0;
        }
        double ratio = distance / direction;
        if (direction < 0.0) {
            if (ratio > range[1]) {
                return false;
            }
            range[0] = Math.max(range[0], ratio);
        } else {
            if (ratio < range[0]) {
                return false;
            }
            range[1] = Math.min(range[1], ratio);
        }
        return true;
    }

    private static double[] interpolate(double[] start, double[] end, double ratio) {
        if (ratio == 0.0) {
            return start.clone();
        }
        if (ratio == 1.0) {
            return end.clone();
        }
        double[] result = new double[start.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = start[index] + ratio * (end[index] - start[index]);
        }
        return result;
    }

    private static double[] position(CoordinateSequence coordinates, int index) {
        GeometryDimension dimension = coordinates.dimension();
        double[] result = new double[dimension.stride()];
        result[0] = coordinates.x(index);
        result[1] = coordinates.y(index);
        if (dimension.hasZ()) {
            result[dimension.zOffset()] = coordinates.z(index);
        }
        if (dimension.hasM()) {
            result[dimension.mOffset()] = coordinates.m(index);
        }
        return result;
    }

    private static CoordinateSequence sequence(
            GeometryDimension dimension, List<double[]> positions, int stride) {
        double[] packed = new double[Math.multiplyExact(positions.size(), stride)];
        int target = 0;
        for (double[] position : positions) {
            System.arraycopy(position, 0, packed, target, stride);
            target += stride;
        }
        return CoordinateSequence.of(dimension, packed);
    }

    private static int[] offsets(List<? extends List<double[]>> parts) {
        int[] result = new int[parts.size() + 1];
        for (int index = 0; index < parts.size(); index++) {
            result[index + 1] = result[index] + parts.get(index).size();
        }
        return result;
    }

    private static List<double[]> flatten(List<? extends List<double[]>> parts) {
        List<double[]> result = new ArrayList<>();
        for (List<double[]> part : parts) {
            result.addAll(part);
        }
        return result;
    }

    private static void publish(List<double[]> active, List<List<double[]>> fragments) {
        if (active.size() >= 2) {
            fragments.add(List.copyOf(active));
        }
    }

    private static void appendDistinct(List<double[]> target, double[] position) {
        if (target.isEmpty() || !same(target.getLast(), position)) {
            target.add(position);
        }
    }

    private static boolean same(double[] first, double[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            if (Double.compare(first[index], second[index]) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(Envelope envelope, double x, double y) {
        return x >= envelope.minX()
                && x <= envelope.maxX()
                && y >= envelope.minY()
                && y <= envelope.maxY();
    }

    private static double signedArea(List<double[]> ring) {
        double result = 0.0;
        for (int index = 1; index < ring.size(); index++) {
            result +=
                    ring.get(index - 1)[0] * ring.get(index)[1]
                            - ring.get(index)[0] * ring.get(index - 1)[1];
        }
        return result / 2.0;
    }

    private static boolean pointInRing(double[] point, List<double[]> ring) {
        boolean inside = false;
        for (int index = 1; index < ring.size(); index++) {
            double[] a = ring.get(index - 1);
            double[] b = ring.get(index);
            if ((a[1] > point[1]) != (b[1] > point[1])
                    && point[0] < (b[0] - a[0]) * (point[1] - a[1]) / (b[1] - a[1]) + a[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private enum Boundary {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP;

        boolean inside(double[] point, Envelope envelope) {
            return switch (this) {
                case LEFT -> point[0] >= envelope.minX();
                case RIGHT -> point[0] <= envelope.maxX();
                case BOTTOM -> point[1] >= envelope.minY();
                case TOP -> point[1] <= envelope.maxY();
            };
        }

        double[] intersection(double[] start, double[] end, Envelope envelope) {
            double ratio =
                    switch (this) {
                        case LEFT -> (envelope.minX() - start[0]) / (end[0] - start[0]);
                        case RIGHT -> (envelope.maxX() - start[0]) / (end[0] - start[0]);
                        case BOTTOM -> (envelope.minY() - start[1]) / (end[1] - start[1]);
                        case TOP -> (envelope.maxY() - start[1]) / (end[1] - start[1]);
                    };
            double[] result = interpolate(start, end, ratio);
            switch (this) {
                case LEFT -> result[0] = envelope.minX();
                case RIGHT -> result[0] = envelope.maxX();
                case BOTTOM -> result[1] = envelope.minY();
                case TOP -> result[1] = envelope.maxY();
            }
            return result;
        }
    }

    private static final class Segment {
        private final double[] start;
        private final double[] end;

        private Segment(double[] start, double[] end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class ClipWork {
        private final GeometryTopologyLimits limits;
        private final GeometryValidity.Work topology;
        private int output;

        private ClipWork(GeometryTopologyLimits limits) {
            this.limits = limits;
            this.topology = new GeometryValidity.Work(limits);
        }

        private void output(int count) {
            output = Math.addExact(output, count);
            if (output > limits.maxOutputCoordinates()) {
                throw new GeometryTopologyException(
                        GeometryTopologyException.OUTPUT_LIMIT,
                        "Geometry topology maxOutputCoordinates limit exceeded",
                        Map.of(
                                "actual",
                                Integer.toString(output),
                                "limit",
                                Integer.toString(limits.maxOutputCoordinates()),
                                "name",
                                "maxOutputCoordinates"));
            }
        }
    }
}
