package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Bounded two-dimensional shortest-path splitting at the geographic antimeridian. */
public final class GeographicSeamSplitter {
    /** Maximum seam intersections inserted into one logical geometry. */
    public static final int MAXIMUM_INSERTED_CROSSINGS = 4_096;

    private static final double MINIMUM_X = -180.0;
    private static final double MAXIMUM_X = 180.0;
    private static final double PERIOD = 360.0;
    private static final long MAXIMUM_CONTAINMENT_COMPARISONS = 1_048_576L;

    private GeographicSeamSplitter() {}

    /**
     * Splits a canonical longitude/latitude geometry into packed canonical fragments.
     *
     * <p>Adjacent longitudes follow the shortest delta in {@code [-180, 180)}. Each fragment keeps
     * canonical longitude ordinates and declares the signed world offset that reconstructs its
     * continuous position. Exact half-period ties therefore travel westward.
     *
     * @param geometry immutable canonical two-dimensional geometry
     * @param cancellation operation token checked during bounded work
     * @return immutable ordered fragments and inserted-crossing count
     * @throws GeographicSeamException for cancellation, excessive crossings, or an unsupported
     *     polygon topology
     */
    public static Result split(Geometry geometry, CancellationToken cancellation) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancellationRequested()) {
            throw cancelled();
        }
        Work work = new Work(cancellation);
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            return completed(new Result(List.of(new Fragment(geometry, 0L)), 0), cancellation);
        }
        if (geometry instanceof LineStringGeometry line) {
            work.addLine(line.coordinates());
            return completed(work.lineResult(), cancellation);
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            for (int part = 0; part < lines.partCount(); part++) {
                work.addLine(
                        slice(
                                lines.coordinates(),
                                lines.partOffset(part),
                                lines.partOffset(part + 1)));
            }
            return completed(work.lineResult(), cancellation);
        }
        if (geometry instanceof PolygonGeometry polygon) {
            work.addPolygon(polygon);
            return completed(work.polygonResult(), cancellation);
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
            int firstRing = polygons.polygonRingOffset(polygon);
            int lastRing = polygons.polygonRingOffset(polygon + 1);
            CoordinateSequence exterior =
                    slice(
                            polygons.coordinates(),
                            polygons.ringOffset(firstRing),
                            polygons.ringOffset(firstRing + 1));
            List<CoordinateSequence> holes = new ArrayList<>();
            for (int ring = firstRing + 1; ring < lastRing; ring++) {
                holes.add(
                        slice(
                                polygons.coordinates(),
                                polygons.ringOffset(ring),
                                polygons.ringOffset(ring + 1)));
            }
            work.addPolygon(new PolygonGeometry(exterior, holes));
        }
        return completed(work.polygonResult(), cancellation);
    }

    /**
     * One canonical packed fragment and its relative continuous-world offset.
     *
     * @param geometry canonical packed geometry
     * @param worldOffset signed world offset reconstructing the continuous position
     * @param retainsLogicalStart whether the fragment retains a real logical line start
     * @param retainsLogicalEnd whether the fragment retains a real logical line end
     */
    public record Fragment(
            Geometry geometry,
            long worldOffset,
            boolean retainsLogicalStart,
            boolean retainsLogicalEnd) {
        /** Validates the immutable fragment. */
        public Fragment {
            Objects.requireNonNull(geometry, "geometry");
        }

        /**
         * Creates a fragment that retains both ordinary logical endpoints.
         *
         * @param geometry canonical packed geometry
         * @param worldOffset signed world offset reconstructing the continuous position
         */
        public Fragment(Geometry geometry, long worldOffset) {
            this(geometry, worldOffset, true, true);
        }
    }

    /**
     * Immutable split result in deterministic fragment order.
     *
     * @param fragments non-empty ordered canonical fragments
     * @param insertedCrossings number of inserted seam intersections
     */
    public record Result(List<Fragment> fragments, int insertedCrossings) {
        /** Defensively copies and validates the result. */
        public Result {
            fragments = List.copyOf(Objects.requireNonNull(fragments, "fragments"));
            if (fragments.isEmpty()
                    || insertedCrossings < 0
                    || insertedCrossings > MAXIMUM_INSERTED_CROSSINGS) {
                throw new IllegalArgumentException("Geographic seam result is outside its profile");
            }
        }
    }

    /** Stable checked failure from geographic seam planning. */
    @SuppressWarnings("serial")
    public static final class GeographicSeamException extends RuntimeException {
        /** Stable machine-readable failure code. */
        private final String code;

        /** Immutable bounded diagnostic context. */
        private final Map<String, String> context;

        private GeographicSeamException(String code, String message, Map<String, String> context) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
            this.context = Map.copyOf(Objects.requireNonNull(context, "context"));
        }

        /**
         * Returns the stable machine-readable failure code.
         *
         * @return stable failure code
         */
        public String code() {
            return code;
        }

        /**
         * Returns immutable bounded diagnostic context.
         *
         * @return immutable diagnostic context
         */
        public Map<String, String> context() {
            return context;
        }
    }

    private static final class Work {
        private final CancellationToken cancellation;
        private final List<LinePiece> linePieces = new ArrayList<>();
        private final TreeMap<Long, List<PolygonGeometry>> polygonParts = new TreeMap<>();
        private int crossings;
        private int workUnits;
        private long containmentComparisons;

        private Work(CancellationToken cancellation) {
            this.cancellation = cancellation;
        }

        private void addLine(CoordinateSequence source) {
            List<Vertex> unwrapped = unwrapLine(source);
            long activeWindow = initialLineWindow(unwrapped);
            List<Vertex> active = new ArrayList<>();
            boolean retainsStart = true;
            active.add(canonical(unwrapped.getFirst(), activeWindow));
            for (int index = 1; index < unwrapped.size(); index++) {
                checkWork();
                Vertex start = unwrapped.get(index - 1);
                Vertex end = unwrapped.get(index);
                long targetWindow = windowForSegmentEnd(end.x(), start.x());
                if (targetWindow != activeWindow
                        && isWindowBoundary(end.x(), targetWindow)
                        && !continuesAcrossBoundary(unwrapped, index, targetWindow)) {
                    appendDistinct(active, canonical(end, activeWindow));
                    continue;
                }
                if (targetWindow == activeWindow) {
                    appendDistinct(active, canonical(end, activeWindow));
                    continue;
                }
                if (Math.abs(targetWindow - activeWindow) != 1L) {
                    throw geometryFailure("projectedSeam");
                }
                boolean east = targetWindow > activeWindow;
                double seam = east ? windowMaximum(activeWindow) : windowMinimum(activeWindow);
                double ratio = (seam - start.x()) / (end.x() - start.x());
                double y = start.y() + ratio * (end.y() - start.y());
                Vertex oldSeam = new Vertex(east ? MAXIMUM_X : MINIMUM_X, y);
                appendDistinct(active, oldSeam);
                publishLine(activeWindow, active, retainsStart, false);
                crossing();
                activeWindow = targetWindow;
                active = new ArrayList<>();
                retainsStart = false;
                active.add(new Vertex(east ? MINIMUM_X : MAXIMUM_X, y));
                appendDistinct(active, canonical(end, activeWindow));
            }
            publishLine(activeWindow, active, retainsStart, true);
        }

        private void addPolygon(PolygonGeometry polygon) {
            int published = 0;
            List<Vertex> exterior = unwrap(polygon.exterior(), true, 0.0);
            validateUnwrappedRing(exterior);
            double exteriorCenter = averageX(exterior);
            List<List<Vertex>> holes = new ArrayList<>();
            for (CoordinateSequence sourceHole : polygon.holes()) {
                List<Vertex> hole = unwrap(sourceHole, true, exteriorCenter);
                validateUnwrappedRing(hole);
                holes.add(hole);
            }
            long firstWindow = minimumWindow(exterior);
            long lastWindow = maximumWindow(exterior);
            if (lastWindow - firstWindow > MAXIMUM_INSERTED_CROSSINGS) {
                throw crossingLimit();
            }
            for (long current = firstWindow; current <= lastWindow; current++) {
                checkWork();
                List<Vertex> clippedExterior = clipRing(exterior, current, "ambiguousRing");
                if (clippedExterior.isEmpty()) {
                    continue;
                }
                CoordinateSequence canonicalExterior = canonicalRing(clippedExterior, current);
                if (Math.abs(signedArea(canonicalExterior)) == 0.0) {
                    throw geometryFailure("invalidRing");
                }
                List<CoordinateSequence> canonicalHoles = new ArrayList<>();
                for (List<Vertex> hole : holes) {
                    if (minimumWindow(hole) != maximumWindow(hole)) {
                        throw geometryFailure("ambiguousHole");
                    }
                    List<Vertex> clippedHole = clipRing(hole, current, "ambiguousHole");
                    if (clippedHole.isEmpty()) {
                        continue;
                    }
                    CoordinateSequence canonicalHole = canonicalRing(clippedHole, current);
                    validateHoleContainment(canonicalExterior, canonicalHole);
                    validateHoleSeparation(canonicalHoles, canonicalHole);
                    canonicalHoles.add(canonicalHole);
                }
                polygonParts
                        .computeIfAbsent(current, ignored -> new ArrayList<>())
                        .add(new PolygonGeometry(canonicalExterior, canonicalHoles));
                published++;
            }
            if (published == 0) {
                throw geometryFailure("invalidRing");
            }
        }

        private Result lineResult() {
            if (linePieces.isEmpty()) {
                throw geometryFailure("projectedSeam");
            }
            List<Fragment> fragments = new ArrayList<>(linePieces.size());
            for (LinePiece piece : linePieces) {
                fragments.add(
                        new Fragment(
                                new LineStringGeometry(piece.coordinates()),
                                piece.worldOffset(),
                                piece.retainsStart(),
                                piece.retainsEnd()));
            }
            return new Result(fragments, crossings);
        }

        private Result polygonResult() {
            if (polygonParts.isEmpty()) {
                throw geometryFailure("invalidRing");
            }
            List<Fragment> fragments = new ArrayList<>(polygonParts.size());
            polygonParts.forEach(
                    (offset, polygons) -> {
                        Geometry geometry =
                                polygons.size() == 1
                                        ? polygons.getFirst()
                                        : MultiPolygonGeometry.ofPolygons(polygons);
                        fragments.add(new Fragment(geometry, offset));
                    });
            return new Result(fragments, crossings);
        }

        private List<Vertex> unwrap(
                CoordinateSequence source, boolean ring, double preferredCenter) {
            List<Vertex> result = new ArrayList<>(source.size());
            double first = canonicalLongitude(source.x(0));
            result.add(new Vertex(first, source.y(0)));
            double previousRaw = source.x(0);
            double previousUnwrapped = first;
            for (int index = 1; index < source.size(); index++) {
                checkWork();
                double raw = source.x(index);
                double delta = shortestDelta(raw - previousRaw);
                double next = previousUnwrapped + delta;
                result.add(new Vertex(next, source.y(index)));
                previousRaw = raw;
                previousUnwrapped = next;
            }
            if (ring) {
                double closureError = result.getLast().x() - result.getFirst().x();
                if (Double.compare(closureError, 0.0) != 0) {
                    throw geometryFailure("invalidRing");
                }
                double shift = Math.rint((preferredCenter - averageX(result)) / PERIOD) * PERIOD;
                if (shift != 0.0) {
                    List<Vertex> shifted = new ArrayList<>(result.size());
                    for (Vertex vertex : result) {
                        shifted.add(new Vertex(vertex.x() + shift, vertex.y()));
                    }
                    result = shifted;
                }
            }
            return result;
        }

        private List<Vertex> unwrapLine(CoordinateSequence source) {
            List<Vertex> result = new ArrayList<>(source.size());
            double first = source.x(0);
            if (first < MINIMUM_X || first > MAXIMUM_X) {
                throw geometryFailure("projectedSeam");
            }
            result.add(new Vertex(first, source.y(0)));
            double previousRaw = first;
            double previousUnwrapped = first;
            for (int index = 1; index < source.size(); index++) {
                checkWork();
                double raw = source.x(index);
                if (raw < MINIMUM_X || raw > MAXIMUM_X) {
                    throw geometryFailure("projectedSeam");
                }
                double next = previousUnwrapped + shortestDelta(raw - previousRaw);
                result.add(new Vertex(next, source.y(index)));
                previousRaw = raw;
                previousUnwrapped = next;
            }
            return result;
        }

        private void publishLine(
                long world, List<Vertex> vertices, boolean retainsStart, boolean retainsEnd) {
            removeConsecutiveDuplicates(vertices);
            if (vertices.size() >= 2) {
                linePieces.add(new LinePiece(world, sequence(vertices), retainsStart, retainsEnd));
            }
        }

        private List<Vertex> clipRing(List<Vertex> source, long world, String ambiguityReason) {
            List<Vertex> open = new ArrayList<>(source.subList(0, source.size() - 1));
            List<Vertex> lower = clip(open, windowMinimum(world), true, ambiguityReason);
            List<Vertex> both = clip(lower, windowMaximum(world), false, ambiguityReason);
            removeConsecutiveDuplicates(both);
            if (both.size() < 3) {
                return List.of();
            }
            if (!both.getFirst().equals(both.getLast())) {
                both.add(both.getFirst());
            }
            if (both.size() < 4) {
                return List.of();
            }
            return both;
        }

        private List<Vertex> clip(
                List<Vertex> input,
                double boundary,
                boolean retainGreater,
                String ambiguityReason) {
            if (input.isEmpty()) {
                return List.of();
            }
            List<Vertex> result = new ArrayList<>();
            Vertex previous = input.getLast();
            boolean previousInside = inside(previous.x(), boundary, retainGreater);
            int transitions = 0;
            for (Vertex current : input) {
                checkWork();
                boolean currentInside = inside(current.x(), boundary, retainGreater);
                if (currentInside != previousInside) {
                    if (++transitions > 2) {
                        throw geometryFailure(ambiguityReason);
                    }
                    double ratio = (boundary - previous.x()) / (current.x() - previous.x());
                    result.add(
                            new Vertex(
                                    boundary, previous.y() + ratio * (current.y() - previous.y())));
                    crossing();
                }
                if (currentInside) {
                    result.add(current);
                }
                previous = current;
                previousInside = currentInside;
            }
            return result;
        }

        private void checkWork() {
            if ((++workUnits & 4095) == 0 && cancellation.isCancellationRequested()) {
                throw cancelled();
            }
        }

        private void crossing() {
            crossings++;
            if (crossings > MAXIMUM_INSERTED_CROSSINGS) {
                throw crossingLimit();
            }
        }

        private void validateHoleContainment(CoordinateSequence exterior, CoordinateSequence hole) {
            for (int holeVertex = 0; holeVertex < hole.size() - 1; holeVertex++) {
                if (!strictlyContains(exterior, hole.x(holeVertex), hole.y(holeVertex))) {
                    throw geometryFailure("ambiguousHole");
                }
            }
            if (ringsIntersect(hole, exterior)) {
                throw geometryFailure("ambiguousHole");
            }
        }

        private void validateHoleSeparation(
                List<CoordinateSequence> retained, CoordinateSequence candidate) {
            for (CoordinateSequence previous : retained) {
                if (ringsIntersect(previous, candidate)
                        || strictlyContains(previous, candidate.x(0), candidate.y(0))
                        || strictlyContains(candidate, previous.x(0), previous.y(0))) {
                    throw geometryFailure("ambiguousHole");
                }
            }
        }

        private boolean ringsIntersect(CoordinateSequence first, CoordinateSequence second) {
            for (int firstEdge = 1; firstEdge < first.size(); firstEdge++) {
                for (int secondEdge = 1; secondEdge < second.size(); secondEdge++) {
                    containmentComparison();
                    if (segmentsIntersect(
                            first.x(firstEdge - 1),
                            first.y(firstEdge - 1),
                            first.x(firstEdge),
                            first.y(firstEdge),
                            second.x(secondEdge - 1),
                            second.y(secondEdge - 1),
                            second.x(secondEdge),
                            second.y(secondEdge))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean strictlyContains(CoordinateSequence ring, double x, double y) {
            boolean inside = false;
            int count = ring.size() - 1;
            for (int first = 0, previous = count - 1; first < count; previous = first++) {
                containmentComparison();
                double ax = ring.x(previous);
                double ay = ring.y(previous);
                double bx = ring.x(first);
                double by = ring.y(first);
                if (onSegment(ax, ay, bx, by, x, y)) {
                    return false;
                }
                if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private void containmentComparison() {
            if (++containmentComparisons > MAXIMUM_CONTAINMENT_COMPARISONS) {
                throw containmentLimit();
            }
            checkWork();
        }
    }

    private static CoordinateSequence canonicalRing(List<Vertex> ring, long world) {
        List<Vertex> shifted = new ArrayList<>(ring.size());
        double offset = world * PERIOD;
        for (Vertex vertex : ring) {
            double x = vertex.x() - offset;
            if (x < MINIMUM_X || x > MAXIMUM_X) {
                throw geometryFailure("invalidRing");
            }
            shifted.add(new Vertex(x, vertex.y()));
        }
        return sequence(shifted);
    }

    private static Result completed(Result result, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw cancelled();
        }
        return result;
    }

    private static long initialLineWindow(List<Vertex> line) {
        Vertex first = line.getFirst();
        long result = window(first.x());
        if (line.size() > 1 && isWindowBoundary(first.x(), result) && line.get(1).x() < first.x()) {
            return result - 1L;
        }
        return result;
    }

    private static boolean continuesAcrossBoundary(
            List<Vertex> line, int boundaryIndex, long targetWindow) {
        Vertex boundary = line.get(boundaryIndex);
        for (int index = boundaryIndex + 1; index < line.size(); index++) {
            Vertex next = line.get(index);
            if (next.x() != boundary.x()) {
                return windowForSegmentEnd(next.x(), boundary.x()) == targetWindow;
            }
        }
        return false;
    }

    private static boolean isWindowBoundary(double x, long window) {
        return x == windowMinimum(window);
    }

    private static void validateUnwrappedRing(List<Vertex> ring) {
        if (ring.size() < 4 || !ring.getFirst().equals(ring.getLast())) {
            throw geometryFailure("invalidRing");
        }
        if (Math.abs(signedArea(sequence(ring))) == 0.0) {
            throw geometryFailure("invalidRing");
        }
    }

    private static boolean onSegment(
            double ax, double ay, double bx, double by, double px, double py) {
        double cross = (px - ax) * (by - ay) - (py - ay) * (bx - ax);
        return cross == 0.0
                && px >= Math.min(ax, bx)
                && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by)
                && py <= Math.max(ay, by);
    }

    private static boolean segmentsIntersect(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy,
            double dx,
            double dy) {
        double first = orientation(ax, ay, bx, by, cx, cy);
        double second = orientation(ax, ay, bx, by, dx, dy);
        double third = orientation(cx, cy, dx, dy, ax, ay);
        double fourth = orientation(cx, cy, dx, dy, bx, by);
        return (first == 0.0 && onSegment(ax, ay, bx, by, cx, cy))
                || (second == 0.0 && onSegment(ax, ay, bx, by, dx, dy))
                || (third == 0.0 && onSegment(cx, cy, dx, dy, ax, ay))
                || (fourth == 0.0 && onSegment(cx, cy, dx, dy, bx, by))
                || ((first > 0.0) != (second > 0.0) && (third > 0.0) != (fourth > 0.0));
    }

    private static double orientation(
            double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static double signedArea(CoordinateSequence ring) {
        double twice = 0.0;
        for (int index = 1; index < ring.size(); index++) {
            twice += ring.x(index - 1) * ring.y(index) - ring.x(index) * ring.y(index - 1);
        }
        return twice / 2.0;
    }

    private static double averageX(List<Vertex> ring) {
        double total = 0.0;
        int count = Math.max(1, ring.size() - 1);
        for (int index = 0; index < count; index++) {
            total += ring.get(index).x();
        }
        return total / count;
    }

    private static long minimumWindow(List<Vertex> ring) {
        long minimum = Long.MAX_VALUE;
        for (Vertex vertex : ring) {
            minimum = Math.min(minimum, window(vertex.x()));
        }
        return minimum;
    }

    private static long maximumWindow(List<Vertex> ring) {
        long maximum = Long.MIN_VALUE;
        for (Vertex vertex : ring) {
            maximum = Math.max(maximum, windowForMaximum(vertex.x()));
        }
        return maximum;
    }

    private static long windowForMaximum(double x) {
        long result = window(x);
        if (x == windowMinimum(result) && result > Long.MIN_VALUE) {
            return result - 1;
        }
        return result;
    }

    private static long windowForSegmentEnd(double end, double start) {
        long result = window(end);
        if (end == windowMinimum(result) && end < start) {
            return result - 1;
        }
        return result;
    }

    private static long window(double x) {
        double value = Math.floor((x - MINIMUM_X) / PERIOD);
        if (!Double.isFinite(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw geometryFailure("projectedSeam");
        }
        return (long) value;
    }

    private static double windowMinimum(long world) {
        return MINIMUM_X + world * PERIOD;
    }

    private static double windowMaximum(long world) {
        return MAXIMUM_X + world * PERIOD;
    }

    private static Vertex canonical(Vertex vertex, long world) {
        double x = vertex.x() - world * PERIOD;
        if (x < MINIMUM_X || x > MAXIMUM_X) {
            throw geometryFailure("projectedSeam");
        }
        return new Vertex(x, vertex.y());
    }

    private static double canonicalLongitude(double longitude) {
        if (longitude == MAXIMUM_X) {
            return MINIMUM_X;
        }
        if (longitude < MINIMUM_X || longitude > MAXIMUM_X) {
            throw geometryFailure("projectedSeam");
        }
        return longitude;
    }

    private static double shortestDelta(double raw) {
        double result = raw - PERIOD * Math.floor((raw + 180.0) / PERIOD);
        return result == 180.0 ? -180.0 : result;
    }

    private static boolean inside(double x, double boundary, boolean retainGreater) {
        return retainGreater ? x >= boundary : x <= boundary;
    }

    private static void appendDistinct(List<Vertex> target, Vertex value) {
        if (target.isEmpty() || !target.getLast().equals(value)) {
            target.add(value);
        }
    }

    private static void removeConsecutiveDuplicates(List<Vertex> values) {
        for (int index = values.size() - 1; index > 0; index--) {
            if (values.get(index).equals(values.get(index - 1))) {
                values.remove(index);
            }
        }
    }

    private static CoordinateSequence sequence(List<Vertex> vertices) {
        double[] packed = new double[Math.multiplyExact(vertices.size(), 2)];
        for (int index = 0; index < vertices.size(); index++) {
            packed[index * 2] = vertices.get(index).x();
            packed[index * 2 + 1] = vertices.get(index).y();
        }
        return CoordinateSequence.of(packed);
    }

    private static CoordinateSequence slice(CoordinateSequence source, int first, int last) {
        double[] packed = new double[Math.multiplyExact(last - first, 2)];
        for (int index = first; index < last; index++) {
            packed[(index - first) * 2] = source.x(index);
            packed[(index - first) * 2 + 1] = source.y(index);
        }
        return CoordinateSequence.of(packed);
    }

    private static GeographicSeamException crossingLimit() {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("scope", "worldWrap");
        context.put("limit", "seamCrossings");
        context.put("maximum", Integer.toString(MAXIMUM_INSERTED_CROSSINGS));
        return new GeographicSeamException(
                "SOURCE_LIMIT_EXCEEDED", "Geographic seam crossing limit exceeded", context);
    }

    private static GeographicSeamException containmentLimit() {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("scope", "worldWrap");
        context.put("limit", "containmentComparisons");
        context.put("maximum", Long.toString(MAXIMUM_CONTAINMENT_COMPARISONS));
        return new GeographicSeamException(
                "SOURCE_LIMIT_EXCEEDED", "Geographic containment limit exceeded", context);
    }

    private static GeographicSeamException cancelled() {
        return new GeographicSeamException(
                "SOURCE_CANCELLED",
                "Geographic seam planning was cancelled",
                Map.of("operation", "world-wrap-geometry"));
    }

    private static GeographicSeamException geometryFailure(String reason) {
        return new GeographicSeamException(
                "WORLD_WRAP_GEOMETRY_UNSUPPORTED",
                "Geographic geometry cannot be split without topology repair",
                Map.of("reason", reason));
    }

    private record Vertex(double x, double y) {}

    private record LinePiece(
            long worldOffset,
            CoordinateSequence coordinates,
            boolean retainsStart,
            boolean retainsEnd) {}
}
