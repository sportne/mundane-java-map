package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded OGC Simple Features validity checks using x/y topology. */
@SuppressWarnings("StringConcatToTextBlock")
public final class GeometryValidity {
    private GeometryValidity() {}

    /** Stable validity reasons in deterministic check order. */
    public enum Reason {
        /** A line or ring has too few distinct x/y positions. */
        TOO_FEW_DISTINCT_POSITIONS,
        /** A polygon ring is not closed in x/y. */
        RING_NOT_CLOSED,
        /** A polygon ring has zero signed x/y area. */
        ZERO_AREA_RING,
        /** A polygon ring crosses or touches itself away from adjacent vertices. */
        RING_SELF_INTERSECTION,
        /** An interior ring is outside or touches the exterior ring. */
        HOLE_OUTSIDE_SHELL,
        /** Two polygon rings cross or touch. */
        RING_INTERSECTION,
        /** Two polygon interiors overlap. */
        POLYGON_INTERIOR_OVERLAP
    }

    /**
     * One stable first-failure diagnostic.
     *
     * @param reason machine-readable reason
     * @param geometryPath zero-based collection/polygon/ring path
     * @param location representative x/y failure location when one exists
     */
    public record Issue(Reason reason, String geometryPath, Optional<Coordinate> location) {
        /** Validates and defensively retains the diagnostic. */
        public Issue {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(geometryPath, "geometryPath");
            Objects.requireNonNull(location, "location");
        }
    }

    /**
     * Immutable validity outcome.
     *
     * @param issue empty when valid, otherwise the deterministic first failure
     */
    public record Result(Optional<Issue> issue) {
        /** Validates the outcome. */
        public Result {
            Objects.requireNonNull(issue, "issue");
        }

        /**
         * Returns whether the geometry is valid under this bounded profile.
         *
         * @return whether no issue was found
         */
        public boolean isValid() {
            return issue.isEmpty();
        }
    }

    /**
     * Checks a geometry using the default prospective work limits.
     *
     * <p>Empty values are valid. Z and M ordinates are retained by the caller but do not
     * participate in Simple Features topology. Collection members are checked in encounter order.
     * The operation publishes either a complete result or a stable limit exception.
     *
     * @param geometry immutable input
     * @return validity outcome
     */
    public static Result check(Geometry geometry) {
        return check(geometry, GeometryTopologyLimits.DEFAULT);
    }

    /**
     * Checks a geometry under explicit prospective work limits.
     *
     * @param geometry immutable input
     * @param limits work limits
     * @return validity outcome
     * @throws GeometryTopologyException when a prospective limit is exceeded
     */
    public static Result check(Geometry geometry, GeometryTopologyLimits limits) {
        Objects.requireNonNull(geometry, "geometry");
        Work work = new Work(Objects.requireNonNull(limits, "limits"));
        Optional<Issue> issue = checkGeometry(geometry, "$", work);
        return new Result(issue);
    }

    private static Optional<Issue> checkGeometry(Geometry geometry, String path, Work work) {
        if (geometry instanceof EmptyGeometry) {
            return Optional.empty();
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int index = 0; index < collection.geometries().size(); index++) {
                Optional<Issue> issue =
                        checkGeometry(collection.geometries().get(index), path + "/" + index, work);
                if (issue.isPresent()) {
                    return issue;
                }
            }
            return Optional.empty();
        }
        Shape shape = Shape.of(geometry, work);
        if (shape.kind == GeometryKind.LINE_STRING
                || shape.kind == GeometryKind.MULTI_LINE_STRING) {
            for (int part = 0; part < shape.parts.size(); part++) {
                Part line = shape.parts.get(part);
                if (distinct(line, work) < 2) {
                    return issue(
                            Reason.TOO_FEW_DISTINCT_POSITIONS,
                            path + "/line/" + part,
                            line.coordinate(0));
                }
            }
            return Optional.empty();
        }
        if (shape.kind != GeometryKind.POLYGON && shape.kind != GeometryKind.MULTI_POLYGON) {
            return Optional.empty();
        }
        for (int polygon = 0; polygon < shape.polygons.size(); polygon++) {
            List<Part> rings = shape.polygons.get(polygon);
            for (int ring = 0; ring < rings.size(); ring++) {
                Part value = rings.get(ring);
                String ringPath = path + "/polygon/" + polygon + "/ring/" + ring;
                if (!same(value, 0, value.size() - 1)) {
                    return issue(
                            Reason.RING_NOT_CLOSED, ringPath, value.coordinate(value.size() - 1));
                }
                if (distinct(value, work) < 3) {
                    return issue(Reason.TOO_FEW_DISTINCT_POSITIONS, ringPath, value.coordinate(0));
                }
                Optional<Coordinate> crossing = selfIntersection(value, work);
                if (crossing.isPresent()) {
                    return issue(Reason.RING_SELF_INTERSECTION, ringPath, crossing.orElseThrow());
                }
                if (signedArea(value) == 0.0) {
                    return issue(Reason.ZERO_AREA_RING, ringPath, value.coordinate(0));
                }
            }
            Part shell = rings.getFirst();
            for (int hole = 1; hole < rings.size(); hole++) {
                Part interior = rings.get(hole);
                Optional<Coordinate> crossing = ringsIntersection(shell, interior, work);
                if (crossing.isPresent()) {
                    return issue(
                            Reason.RING_INTERSECTION,
                            path + "/polygon/" + polygon + "/ring/" + hole,
                            crossing.orElseThrow());
                }
                if (pointInRing(interior.x(0), interior.y(0), shell, work) != Location.INTERIOR) {
                    return issue(
                            Reason.HOLE_OUTSIDE_SHELL,
                            path + "/polygon/" + polygon + "/ring/" + hole,
                            interior.coordinate(0));
                }
            }
            for (int first = 1; first < rings.size(); first++) {
                for (int second = first + 1; second < rings.size(); second++) {
                    Optional<Coordinate> crossing =
                            ringsIntersection(rings.get(first), rings.get(second), work);
                    if (crossing.isPresent()
                            || pointInRing(
                                            rings.get(first).x(0),
                                            rings.get(first).y(0),
                                            rings.get(second),
                                            work)
                                    != Location.EXTERIOR
                            || pointInRing(
                                            rings.get(second).x(0),
                                            rings.get(second).y(0),
                                            rings.get(first),
                                            work)
                                    != Location.EXTERIOR) {
                        Coordinate location =
                                crossing.isPresent()
                                        ? crossing.orElseThrow()
                                        : rings.get(second).coordinate(0);
                        return issue(
                                Reason.RING_INTERSECTION, path + "/polygon/" + polygon, location);
                    }
                }
            }
        }
        for (int first = 0; first < shape.polygons.size(); first++) {
            for (int second = first + 1; second < shape.polygons.size(); second++) {
                Part a = shape.polygons.get(first).getFirst();
                Part b = shape.polygons.get(second).getFirst();
                Optional<Coordinate> crossing = interiorRingsIntersection(a, b, work);
                if (crossing.isPresent()
                        || pointInRing(a.x(0), a.y(0), b, work) == Location.INTERIOR
                        || pointInRing(b.x(0), b.y(0), a, work) == Location.INTERIOR) {
                    return issue(
                            Reason.POLYGON_INTERIOR_OVERLAP,
                            path + "/polygon/" + second,
                            crossing.orElseGet(() -> b.coordinate(0)));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Issue> issue(Reason reason, String path, Coordinate location) {
        return Optional.of(new Issue(reason, path, Optional.of(location)));
    }

    private static int distinct(Part part, Work work) {
        int count = 0;
        for (int index = 0;
                index < part.size() - (same(part, 0, part.size() - 1) ? 1 : 0);
                index++) {
            boolean seen = false;
            for (int prior = 0; prior < index; prior++) {
                work.comparison();
                if (same(part, index, prior)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                count++;
            }
        }
        return count;
    }

    private static double signedArea(Part ring) {
        double area = 0.0;
        for (int index = 1; index < ring.size(); index++) {
            area += ring.x(index - 1) * ring.y(index) - ring.x(index) * ring.y(index - 1);
        }
        return area / 2.0;
    }

    private static Optional<Coordinate> selfIntersection(Part ring, Work work) {
        int segments = ring.size() - 1;
        for (int first = 0; first < segments; first++) {
            for (int second = first + 1; second < segments; second++) {
                if (second == first + 1 || (first == 0 && second == segments - 1)) {
                    continue;
                }
                work.comparison();
                Optional<Coordinate> crossing = segmentIntersection(ring, first, ring, second);
                if (crossing.isPresent()) {
                    return crossing;
                }
            }
        }
        return Optional.empty();
    }

    static Optional<Coordinate> ringsIntersection(Part first, Part second, Work work) {
        for (int a = 0; a < first.size() - 1; a++) {
            for (int b = 0; b < second.size() - 1; b++) {
                work.comparison();
                Optional<Coordinate> crossing = segmentIntersection(first, a, second, b);
                if (crossing.isPresent()) {
                    return crossing;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Coordinate> interiorRingsIntersection(
            Part first, Part second, Work work) {
        for (int a = 0; a < first.size() - 1; a++) {
            for (int b = 0; b < second.size() - 1; b++) {
                work.comparison();
                double ax = first.x(a);
                double ay = first.y(a);
                double bx = first.x(a + 1);
                double by = first.y(a + 1);
                double cx = second.x(b);
                double cy = second.y(b);
                double dx = second.x(b + 1);
                double dy = second.y(b + 1);
                double abC = cross(ax, ay, bx, by, cx, cy);
                double abD = cross(ax, ay, bx, by, dx, dy);
                double cdA = cross(cx, cy, dx, dy, ax, ay);
                double cdB = cross(cx, cy, dx, dy, bx, by);
                boolean proper =
                        ((abC > 0.0 && abD < 0.0) || (abC < 0.0 && abD > 0.0))
                                && ((cdA > 0.0 && cdB < 0.0) || (cdA < 0.0 && cdB > 0.0));
                if (proper) {
                    return segmentIntersection(first, a, second, b);
                }
                if (abC == 0.0 && abD == 0.0 && overlappingSpan(ax, ay, bx, by, cx, cy, dx, dy)) {
                    return Optional.of(new Coordinate(cx, cy));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean overlappingSpan(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy,
            double dx,
            double dy) {
        if (Math.abs(bx - ax) >= Math.abs(by - ay)) {
            return Math.max(Math.min(ax, bx), Math.min(cx, dx))
                    < Math.min(Math.max(ax, bx), Math.max(cx, dx));
        }
        return Math.max(Math.min(ay, by), Math.min(cy, dy))
                < Math.min(Math.max(ay, by), Math.max(cy, dy));
    }

    static Optional<Coordinate> segmentIntersection(Part a, int ai, Part b, int bi) {
        double ax = a.x(ai);
        double ay = a.y(ai);
        double bx = a.x(ai + 1);
        double by = a.y(ai + 1);
        double cx = b.x(bi);
        double cy = b.y(bi);
        double dx = b.x(bi + 1);
        double dy = b.y(bi + 1);
        double abC = cross(ax, ay, bx, by, cx, cy);
        double abD = cross(ax, ay, bx, by, dx, dy);
        double cdA = cross(cx, cy, dx, dy, ax, ay);
        double cdB = cross(cx, cy, dx, dy, bx, by);
        if (((abC > 0.0 && abD < 0.0) || (abC < 0.0 && abD > 0.0))
                && ((cdA > 0.0 && cdB < 0.0) || (cdA < 0.0 && cdB > 0.0))) {
            double denominator = (ax - bx) * (cy - dy) - (ay - by) * (cx - dx);
            double determinantA = ax * by - ay * bx;
            double determinantB = cx * dy - cy * dx;
            return Optional.of(
                    new Coordinate(
                            (determinantA * (cx - dx) - (ax - bx) * determinantB) / denominator,
                            (determinantA * (cy - dy) - (ay - by) * determinantB) / denominator));
        }
        if (abC == 0.0 && onSegment(ax, ay, bx, by, cx, cy)) {
            return Optional.of(new Coordinate(cx, cy));
        }
        if (abD == 0.0 && onSegment(ax, ay, bx, by, dx, dy)) {
            return Optional.of(new Coordinate(dx, dy));
        }
        if (cdA == 0.0 && onSegment(cx, cy, dx, dy, ax, ay)) {
            return Optional.of(new Coordinate(ax, ay));
        }
        if (cdB == 0.0 && onSegment(cx, cy, dx, dy, bx, by)) {
            return Optional.of(new Coordinate(bx, by));
        }
        return Optional.empty();
    }

    static Location pointInRing(double x, double y, Part ring, Work work) {
        boolean inside = false;
        for (int index = 1; index < ring.size(); index++) {
            work.comparison();
            double ax = ring.x(index - 1);
            double ay = ring.y(index - 1);
            double bx = ring.x(index);
            double by = ring.y(index);
            if (cross(ax, ay, bx, by, x, y) == 0.0 && onSegment(ax, ay, bx, by, x, y)) {
                return Location.BOUNDARY;
            }
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) {
                inside = !inside;
            }
        }
        return inside ? Location.INTERIOR : Location.EXTERIOR;
    }

    private static boolean same(Part part, int first, int second) {
        return Double.compare(part.x(first), part.x(second)) == 0
                && Double.compare(part.y(first), part.y(second)) == 0;
    }

    static double cross(double ax, double ay, double bx, double by, double x, double y) {
        return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
    }

    static boolean onSegment(double ax, double ay, double bx, double by, double x, double y) {
        return x >= Math.min(ax, bx)
                && x <= Math.max(ax, bx)
                && y >= Math.min(ay, by)
                && y <= Math.max(ay, by);
    }

    enum Location {
        EXTERIOR,
        BOUNDARY,
        INTERIOR
    }

    static final class Work {
        private final GeometryTopologyLimits limits;
        private long comparisons;
        private int coordinates;

        Work(GeometryTopologyLimits limits) {
            this.limits = limits;
        }

        void addCoordinates(int count) {
            coordinates = Math.addExact(coordinates, count);
            if (coordinates > limits.maxCoordinates()) {
                throw limit(
                        GeometryTopologyException.COORDINATE_LIMIT,
                        "maxCoordinates",
                        coordinates,
                        limits.maxCoordinates());
            }
        }

        void comparison() {
            comparisons++;
            if (comparisons > limits.maxSegmentComparisons()) {
                throw limit(
                        GeometryTopologyException.COMPARISON_LIMIT,
                        "maxSegmentComparisons",
                        comparisons,
                        limits.maxSegmentComparisons());
            }
        }
    }

    static final class Part {
        final CoordinateSequence coordinates;
        final int start;
        final int end;

        Part(CoordinateSequence coordinates, int start, int end) {
            this.coordinates = coordinates;
            this.start = start;
            this.end = end;
        }

        int size() {
            return end - start;
        }

        double x(int index) {
            return coordinates.x(start + index);
        }

        double y(int index) {
            return coordinates.y(start + index);
        }

        Coordinate coordinate(int index) {
            return coordinates.coordinate(start + index);
        }
    }

    static final class Shape {
        final GeometryKind kind;
        final CoordinateSequence coordinates;
        final List<Part> parts;
        final List<List<Part>> polygons;

        private Shape(
                GeometryKind kind,
                CoordinateSequence coordinates,
                List<Part> parts,
                List<List<Part>> polygons) {
            this.kind = kind;
            this.coordinates = coordinates;
            this.parts = parts;
            this.polygons = polygons;
        }

        static Shape of(Geometry geometry, Work work) {
            CoordinateSequence coordinates;
            int[] parts;
            int[] polygons;
            if (geometry instanceof DimensionalGeometry dimensional) {
                coordinates = dimensional.coordinates();
                parts = dimensional.partOffsets();
                polygons = dimensional.polygonPartOffsets();
            } else if (geometry instanceof io.github.mundanej.map.api.PointGeometry point) {
                coordinates = CoordinateSequence.of(point.coordinate().x(), point.coordinate().y());
                parts = new int[0];
                polygons = new int[0];
            } else if (geometry instanceof io.github.mundanej.map.api.MultiPointGeometry points) {
                coordinates = points.coordinates();
                parts = new int[0];
                polygons = new int[0];
            } else if (geometry instanceof LineStringGeometry line) {
                coordinates = line.coordinates();
                parts = new int[] {0, coordinates.size()};
                polygons = new int[0];
            } else if (geometry instanceof MultiLineStringGeometry lines) {
                coordinates = lines.coordinates();
                parts = lines.partOffsets();
                polygons = new int[0];
            } else if (geometry instanceof PolygonGeometry polygon) {
                List<CoordinateSequence> rings = new ArrayList<>();
                rings.add(polygon.exterior());
                rings.addAll(polygon.holes());
                coordinates = pack(rings);
                parts = offsets(rings);
                polygons = new int[] {0, rings.size()};
            } else if (geometry instanceof MultiPolygonGeometry multi) {
                coordinates = multi.coordinates();
                parts = multi.ringOffsets();
                polygons = multi.polygonRingOffsets();
            } else {
                throw new IllegalArgumentException("Geometry collections must be traversed first");
            }
            work.addCoordinates(coordinates.size());
            List<Part> partValues = new ArrayList<>();
            for (int index = 1; index < parts.length; index++) {
                partValues.add(new Part(coordinates, parts[index - 1], parts[index]));
            }
            List<List<Part>> polygonValues = new ArrayList<>();
            for (int index = 1; index < polygons.length; index++) {
                polygonValues.add(
                        List.copyOf(partValues.subList(polygons[index - 1], polygons[index])));
            }
            return new Shape(
                    geometry.kind(),
                    coordinates,
                    List.copyOf(partValues),
                    List.copyOf(polygonValues));
        }

        private static CoordinateSequence pack(List<CoordinateSequence> sequences) {
            int size = sequences.stream().mapToInt(CoordinateSequence::size).sum();
            double[] packed = new double[Math.multiplyExact(size, 2)];
            int target = 0;
            for (CoordinateSequence sequence : sequences) {
                double[] values = sequence.toArray();
                System.arraycopy(values, 0, packed, target, values.length);
                target += values.length;
            }
            return CoordinateSequence.of(packed);
        }

        private static int[] offsets(List<CoordinateSequence> sequences) {
            int[] values = new int[sequences.size() + 1];
            for (int index = 0; index < sequences.size(); index++) {
                values[index + 1] = values[index] + sequences.get(index).size();
            }
            return values;
        }
    }

    private static GeometryTopologyException limit(
            String code, String name, long actual, long maximum) {
        return new GeometryTopologyException(
                code,
                "Geometry topology " + name + " limit exceeded",
                Map.of(
                        "actual",
                        Long.toString(actual),
                        "limit",
                        Long.toString(maximum),
                        "name",
                        name));
    }
}
