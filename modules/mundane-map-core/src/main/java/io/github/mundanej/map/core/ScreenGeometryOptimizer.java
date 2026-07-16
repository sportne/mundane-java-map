package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic JDK-only clipping and simplification for already projected screen geometry. */
public final class ScreenGeometryOptimizer {
    private ScreenGeometryOptimizer() {}

    /**
     * Builds a bounded operation-local rendering plan.
     *
     * @param authoritativeScreenGeometry immutable line or polygon geometry in logical pixels
     * @param expandedScreenClip closed clip including antialias/stroke support
     * @param tolerancePixels finite non-negative simplification tolerance
     * @param limits positive optimization budgets
     * @return immutable rendering decision retaining the authoritative geometry
     * @throws IllegalArgumentException for point geometry or invalid tolerance
     */
    public static ScreenGeometryOptimization optimize(
            Geometry authoritativeScreenGeometry,
            Envelope expandedScreenClip,
            double tolerancePixels,
            ScreenGeometryOptimizationLimits limits) {
        Geometry geometry =
                Objects.requireNonNull(authoritativeScreenGeometry, "authoritativeScreenGeometry");
        Envelope clip = Objects.requireNonNull(expandedScreenClip, "expandedScreenClip");
        Objects.requireNonNull(limits, "limits");
        if (!Double.isFinite(tolerancePixels) || tolerancePixels < 0.0) {
            throw new IllegalArgumentException("Tolerance must be finite and non-negative");
        }
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            throw new IllegalArgumentException("Point geometry has no optimizable path");
        }
        try {
            int components = componentCount(geometry);
            long conservativeCoordinates = maximumOutputCoordinateCount(geometry);
            if (conservativeCoordinates > limits.maximumOutputCoordinates()
                    || conservativeCoordinates > Integer.MAX_VALUE) {
                return fallback(geometry, components);
            }
            long completeMultipartBuildBytes =
                    completeMultipartResultBuildBytes(geometry, conservativeCoordinates);
            if (completeMultipartBuildBytes > limits.maximumBuildBytes()) {
                return fallback(geometry, components);
            }
            BuildBudget build = new BuildBudget(limits.maximumBuildBytes());
            TopologyBudget topology = new TopologyBudget(limits.maximumTopologyComparisons());
            if (geometry instanceof LineStringGeometry line) {
                return optimizeLines(
                        geometry, List.of(line.coordinates()), clip, tolerancePixels, build);
            }
            if (geometry instanceof MultiLineStringGeometry lines) {
                List<CoordinateSequence> parts = new ArrayList<>(lines.partCount());
                for (int part = 0; part < lines.partCount(); part++) {
                    parts.add(
                            slice(
                                    lines.coordinates(),
                                    lines.partOffset(part),
                                    lines.partOffset(part + 1),
                                    build));
                }
                return optimizeLines(geometry, parts, clip, tolerancePixels, build);
            }
            if (geometry instanceof PolygonGeometry polygon) {
                return optimizePolygons(
                        geometry, List.of(polygon), clip, tolerancePixels, build, topology);
            }
            MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
            List<PolygonGeometry> values = new ArrayList<>(polygons.polygonCount());
            for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
                int firstRing = polygons.polygonRingOffset(polygon);
                int lastRing = polygons.polygonRingOffset(polygon + 1);
                CoordinateSequence exterior =
                        slice(
                                polygons.coordinates(),
                                polygons.ringOffset(firstRing),
                                polygons.ringOffset(firstRing + 1),
                                build);
                List<CoordinateSequence> holes = new ArrayList<>();
                for (int ring = firstRing + 1; ring < lastRing; ring++) {
                    holes.add(
                            slice(
                                    polygons.coordinates(),
                                    polygons.ringOffset(ring),
                                    polygons.ringOffset(ring + 1),
                                    build));
                }
                values.add(new PolygonGeometry(exterior, holes));
            }
            return optimizePolygons(geometry, values, clip, tolerancePixels, build, topology);
        } catch (ArithmeticException
                | NegativeArraySizeException
                | NumericUnsafeException
                | BuildLimitExceededException
                | TopologyLimitExceededException failure) {
            return fallback(geometry, componentCount(geometry));
        }
    }

    private static ScreenGeometryOptimization optimizeLines(
            Geometry authoritative,
            List<CoordinateSequence> parts,
            Envelope clip,
            double tolerance,
            BuildBudget build) {
        List<CoordinateSequence> fragments = new ArrayList<>();
        int[] offsets = build.ints(Math.addExact(parts.size(), 1));
        boolean changed = false;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            CoordinateSequence part = parts.get(partIndex);
            List<double[]> clipped = clipLine(part, clip, build);
            if (clipped.size() != 1 || !same(part, clipped.isEmpty() ? null : clipped.getFirst())) {
                changed = true;
            }
            for (double[] values : clipped) {
                double[] simplified = simplifyOpen(values, tolerance, build);
                if (simplified.length >= 4 && distinctEndpoints(simplified)) {
                    if (simplified.length != values.length) {
                        changed = true;
                    }
                    fragments.add(sequenceOf(simplified, build));
                } else {
                    changed = true;
                }
            }
            offsets[partIndex + 1] = fragments.size();
        }
        if (fragments.isEmpty()) {
            return result(
                    authoritative,
                    null,
                    ScreenGeometryOptimizationOutcome.PATH_CULLED,
                    offsets,
                    build);
        }
        if (!changed) {
            return result(
                    authoritative,
                    authoritative,
                    ScreenGeometryOptimizationOutcome.UNCHANGED,
                    offsets,
                    build);
        }
        Geometry rendering =
                fragments.size() == 1 && authoritative instanceof LineStringGeometry
                        ? new LineStringGeometry(fragments.getFirst())
                        : packLines(fragments, build);
        return result(
                authoritative,
                rendering,
                ScreenGeometryOptimizationOutcome.OPTIMIZED,
                offsets,
                build);
    }

    private static ScreenGeometryOptimization optimizePolygons(
            Geometry authoritative,
            List<PolygonGeometry> polygons,
            Envelope clip,
            double tolerance,
            BuildBudget build,
            TopologyBudget topology) {
        List<PolygonGeometry> rendering = new ArrayList<>();
        int[] offsets = build.ints(Math.addExact(polygons.size(), 1));
        boolean optimized = false;
        boolean fellBack = false;
        for (int index = 0; index < polygons.size(); index++) {
            PolygonGeometry polygon = polygons.get(index);
            if (disjoint(polygonEnvelope(polygon), clip)) {
                optimized = true;
                offsets[index + 1] = rendering.size();
                continue;
            }
            PolygonGeometry candidate =
                    safePolygonCandidate(polygon, clip, tolerance, build, topology);
            if (candidate == null) {
                rendering.add(polygon);
                fellBack = true;
            } else {
                rendering.add(candidate);
                optimized |= !candidate.equals(polygon);
            }
            offsets[index + 1] = rendering.size();
        }
        if (rendering.isEmpty()) {
            return result(
                    authoritative,
                    null,
                    ScreenGeometryOptimizationOutcome.PATH_CULLED,
                    offsets,
                    build);
        }
        if (!optimized && !fellBack) {
            return result(
                    authoritative,
                    authoritative,
                    ScreenGeometryOptimizationOutcome.UNCHANGED,
                    offsets,
                    build);
        }
        if (!optimized) {
            return fallback(authoritative, polygons.size());
        }
        Geometry packed =
                rendering.size() == 1 && authoritative instanceof PolygonGeometry
                        ? rendering.getFirst()
                        : packPolygons(rendering, build);
        return result(
                authoritative,
                packed,
                fellBack
                        ? ScreenGeometryOptimizationOutcome.FALLBACK
                        : ScreenGeometryOptimizationOutcome.OPTIMIZED,
                offsets,
                build);
    }

    private static PolygonGeometry safePolygonCandidate(
            PolygonGeometry polygon,
            Envelope clip,
            double tolerance,
            BuildBudget build,
            TopologyBudget topology) {
        if (!validPolygon(polygon, topology, build)) {
            return null;
        }
        boolean contained = contains(clip, polygonEnvelope(polygon));
        CoordinateSequence exterior = polygon.exterior();
        List<CoordinateSequence> holes = polygon.holes();
        if (!contained) {
            if (!strictlyConvex(exterior, topology, build)) {
                return null;
            }
            for (CoordinateSequence hole : holes) {
                if (!contains(clip, hole.envelope()) && !disjoint(hole.envelope(), clip)) {
                    return null;
                }
            }
            double[] clipped = clipRing(copyOf(exterior, build), clip, build);
            if (clipped == null) {
                return null;
            }
            exterior = sequenceOf(clipped, build);
            holes = holes.stream().filter(hole -> contains(clip, hole.envelope())).toList();
        }
        CoordinateSequence simplifiedExterior = simplifyClosed(exterior, tolerance, build);
        if (simplifiedExterior == null) {
            return null;
        }
        List<CoordinateSequence> simplifiedHoles = new ArrayList<>();
        for (CoordinateSequence hole : holes) {
            CoordinateSequence simplified = simplifyClosed(hole, tolerance, build);
            if (simplified == null) {
                return null;
            }
            simplifiedHoles.add(simplified);
        }
        PolygonGeometry candidate = new PolygonGeometry(simplifiedExterior, simplifiedHoles);
        Orientation candidateSign = ringSign(candidate.exterior(), topology);
        Orientation sourceSign = ringSign(polygon.exterior(), topology);
        if (!validPolygon(candidate, topology, build) || candidateSign != sourceSign) {
            return null;
        }
        for (int index = 0; index < candidate.holes().size(); index++) {
            if (ringSign(candidate.holes().get(index), topology)
                    != ringSign(holes.get(index), topology)) {
                return null;
            }
        }
        return candidate;
    }

    private static List<double[]> clipLine(
            CoordinateSequence source, Envelope clip, BuildBudget build) {
        List<double[]> result = new ArrayList<>();
        DoubleBuilder current = null;
        double previousX = positiveZero(source.x(0));
        double previousY = positiveZero(source.y(0));
        for (int index = 1; index < source.size(); index++) {
            double x = positiveZero(source.x(index));
            double y = positiveZero(source.y(index));
            if (Double.compare(previousX, x) == 0 && Double.compare(previousY, y) == 0) {
                continue;
            }
            double[] segment = liangBarsky(previousX, previousY, x, y, clip, build);
            if (segment == null || !distinctEndpoints(segment)) {
                if (current != null && current.coordinateCount() >= 2) {
                    result.add(current.toArray());
                }
                current = null;
            } else {
                if (current == null
                        || Double.compare(current.lastX(), segment[0]) != 0
                        || Double.compare(current.lastY(), segment[1]) != 0) {
                    if (current != null && current.coordinateCount() >= 2) {
                        result.add(current.toArray());
                    }
                    current = new DoubleBuilder(build);
                    current.add(segment[0], segment[1]);
                }
                current.add(segment[2], segment[3]);
            }
            previousX = x;
            previousY = y;
        }
        if (current != null && current.coordinateCount() >= 2) {
            result.add(current.toArray());
        }
        return result;
    }

    private static double[] liangBarsky(
            double x1, double y1, double x2, double y2, Envelope clip, BuildBudget build) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
            throw new NumericUnsafeException();
        }
        ClipRange range = new ClipRange();
        if (!clipParameter(-dx, x1 - clip.minX(), range)
                || !clipParameter(dx, clip.maxX() - x1, range)
                || !clipParameter(-dy, y1 - clip.minY(), range)
                || !clipParameter(dy, clip.maxY() - y1, range)) {
            return null;
        }
        double ax = snapToClip(Math.fma(range.minimum, dx, x1), clip.minX(), clip.maxX());
        double ay = snapToClip(Math.fma(range.minimum, dy, y1), clip.minY(), clip.maxY());
        double bx = snapToClip(Math.fma(range.maximum, dx, x1), clip.minX(), clip.maxX());
        double by = snapToClip(Math.fma(range.maximum, dy, y1), clip.minY(), clip.maxY());
        if (!Double.isFinite(ax)
                || !Double.isFinite(ay)
                || !Double.isFinite(bx)
                || !Double.isFinite(by)) {
            return null;
        }
        double[] result = build.doubles(4);
        result[0] = ax;
        result[1] = ay;
        result[2] = bx;
        result[3] = by;
        return result;
    }

    private static boolean clipParameter(double p, double q, ClipRange range) {
        if (p == 0.0) {
            return q >= 0.0;
        }
        double value = q / p;
        if (!Double.isFinite(value)) {
            throw new NumericUnsafeException();
        }
        if (p < 0.0) {
            if (value > range.maximum) {
                return false;
            }
            if (value > range.minimum) {
                range.minimum = value;
            }
        } else {
            if (value < range.minimum) {
                return false;
            }
            if (value < range.maximum) {
                range.maximum = value;
            }
        }
        return true;
    }

    private static double[] simplifyOpen(double[] values, double tolerance, BuildBudget build) {
        int count = values.length / 2;
        if (count <= 2 || tolerance == 0.0) {
            return build.copy(values, values.length);
        }
        boolean[] keep = build.booleans(count);
        keep[0] = true;
        keep[count - 1] = true;
        int[] firstStack = build.ints(count);
        int[] lastStack = build.ints(count);
        int stack = 0;
        firstStack[stack] = 0;
        lastStack[stack++] = count - 1;
        while (stack > 0) {
            int first = firstStack[--stack];
            int last = lastStack[stack];
            double maximum = -1.0;
            int selected = -1;
            for (int index = first + 1; index < last; index++) {
                double distance = distance(values, index, first, last);
                if (!Double.isFinite(distance)) {
                    throw new NumericUnsafeException();
                }
                if (Double.compare(distance, maximum) > 0) {
                    maximum = distance;
                    selected = index;
                }
            }
            if (selected >= 0 && Double.compare(maximum, tolerance) > 0) {
                keep[selected] = true;
                firstStack[stack] = selected;
                lastStack[stack++] = last;
                firstStack[stack] = first;
                lastStack[stack++] = selected;
            }
        }
        DoubleBuilder result = new DoubleBuilder(build);
        for (int index = 0; index < count; index++) {
            if (keep[index]) {
                result.add(values[index * 2], values[index * 2 + 1]);
            }
        }
        return result.toArray();
    }

    private static double distance(double[] values, int point, int first, int last) {
        double ax = values[first * 2];
        double ay = values[first * 2 + 1];
        double bx = values[last * 2];
        double by = values[last * 2 + 1];
        double px = values[point * 2];
        double py = values[point * 2 + 1];
        double dx = bx - ax;
        double dy = by - ay;
        double qx = px - ax;
        double qy = py - ay;
        double scale =
                Math.max(
                        Math.max(Math.abs(dx), Math.abs(dy)), Math.max(Math.abs(qx), Math.abs(qy)));
        if (scale == 0.0) {
            return 0.0;
        }
        dx /= scale;
        dy /= scale;
        qx /= scale;
        qy /= scale;
        double length = Math.fma(dx, dx, dy * dy);
        double parameter =
                length == 0.0
                        ? 0.0
                        : Math.max(0.0, Math.min(1.0, Math.fma(qx, dx, qy * dy) / length));
        return scale * StrictMath.hypot(qx - parameter * dx, qy - parameter * dy);
    }

    private static CoordinateSequence simplifyClosed(
            CoordinateSequence ring, double tolerance, BuildBudget build) {
        double[] source = removeConsecutiveDuplicates(copyOf(ring, build), true, build);
        if (source.length < 8) {
            return null;
        }
        int unique = source.length / 2 - 1;
        int anchor = 0;
        for (int index = 1; index < unique; index++) {
            int compareX = Double.compare(source[index * 2], source[anchor * 2]);
            if (compareX < 0
                    || (compareX == 0
                            && Double.compare(source[index * 2 + 1], source[anchor * 2 + 1]) < 0)) {
                anchor = index;
            }
        }
        int opposite = anchor;
        double farthest = -1.0;
        for (int offset = 1; offset < unique; offset++) {
            int index = (anchor + offset) % unique;
            double distance =
                    Math.hypot(
                            source[index * 2] - source[anchor * 2],
                            source[index * 2 + 1] - source[anchor * 2 + 1]);
            if (Double.compare(distance, farthest) > 0) {
                farthest = distance;
                opposite = index;
            }
        }
        if (opposite == anchor) {
            return null;
        }
        double[] first = circularChain(source, anchor, opposite, unique, build);
        double[] second = circularChain(source, opposite, anchor, unique, build);
        double[] one = simplifyOpen(first, tolerance, build);
        double[] two = simplifyOpen(second, tolerance, build);
        DoubleBuilder result = new DoubleBuilder(build);
        for (int index = 0; index < one.length / 2; index++) {
            result.add(one[index * 2], one[index * 2 + 1]);
        }
        for (int index = 1; index < two.length / 2 - 1; index++) {
            result.add(two[index * 2], two[index * 2 + 1]);
        }
        result.add(one[0], one[1]);
        if (result.coordinateCount() < 4) {
            return null;
        }
        return sequenceOf(result.toArray(), build);
    }

    private static double[] circularChain(
            double[] values, int first, int last, int unique, BuildBudget build) {
        DoubleBuilder result = new DoubleBuilder(build);
        int index = first;
        while (true) {
            result.add(values[index * 2], values[index * 2 + 1]);
            if (index == last) {
                break;
            }
            index = (index + 1) % unique;
        }
        return result.toArray();
    }

    private static boolean validPolygon(
            PolygonGeometry polygon, TopologyBudget topology, BuildBudget build) {
        if (!validRing(polygon.exterior(), topology, build)) {
            return false;
        }
        for (CoordinateSequence hole : polygon.holes()) {
            if (!validRing(hole, topology, build)
                    || containsStrict(polygon.exterior(), hole.x(0), hole.y(0), topology)
                            != PointRelation.INSIDE) {
                return false;
            }
        }
        for (int first = 0; first < polygon.holes().size(); first++) {
            for (int second = first + 1; second < polygon.holes().size(); second++) {
                topology.charge();
                if (ringsIntersect(
                                polygon.holes().get(first), polygon.holes().get(second), topology)
                        || containsStrict(
                                        polygon.holes().get(first),
                                        polygon.holes().get(second).x(0),
                                        polygon.holes().get(second).y(0),
                                        topology)
                                != PointRelation.OUTSIDE
                        || containsStrict(
                                        polygon.holes().get(second),
                                        polygon.holes().get(first).x(0),
                                        polygon.holes().get(first).y(0),
                                        topology)
                                != PointRelation.OUTSIDE) {
                    return false;
                }
            }
        }
        for (CoordinateSequence hole : polygon.holes()) {
            topology.charge();
            if (ringsIntersect(polygon.exterior(), hole, topology)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validRing(
            CoordinateSequence ring, TopologyBudget topology, BuildBudget build) {
        if (!ring.isClosed()) {
            return false;
        }
        NormalizedVertices normalized = normalizedVertices(ring, topology, build);
        int[] vertices = normalized.indices;
        int count = normalized.count;
        if (count < 3 || ringSign(ring, topology) == Orientation.AMBIGUOUS) {
            return false;
        }
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                topology.charge();
                if (samePoint(
                        ring.x(vertices[first]),
                        ring.y(vertices[first]),
                        ring.x(vertices[second]),
                        ring.y(vertices[second]))) {
                    return false;
                }
            }
        }
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                if (second == first + 1 || (first == 0 && second == count - 1)) {
                    continue;
                }
                topology.charge();
                if (segmentsIntersect(
                        ring,
                        vertices[first],
                        vertices[(first + 1) % count],
                        ring,
                        vertices[second],
                        vertices[(second + 1) % count])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ringsIntersect(
            CoordinateSequence one, CoordinateSequence two, TopologyBudget topology) {
        for (int first = 0; first < one.size() - 1; first++) {
            if (samePoint(one.x(first), one.y(first), one.x(first + 1), one.y(first + 1))) {
                continue;
            }
            for (int second = 0; second < two.size() - 1; second++) {
                if (samePoint(two.x(second), two.y(second), two.x(second + 1), two.y(second + 1))) {
                    continue;
                }
                topology.charge();
                if (segmentsIntersect(one, first, first + 1, two, second, second + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(
            CoordinateSequence one,
            int firstStart,
            int firstEnd,
            CoordinateSequence two,
            int secondStart,
            int secondEnd) {
        double ax = one.x(firstStart), ay = one.y(firstStart);
        double bx = one.x(firstEnd), by = one.y(firstEnd);
        double cx = two.x(secondStart), cy = two.y(secondStart);
        double dx = two.x(secondEnd), dy = two.y(secondEnd);
        if (samePoint(ax, ay, cx, cy)
                || samePoint(ax, ay, dx, dy)
                || samePoint(bx, by, cx, cy)
                || samePoint(bx, by, dx, dy)) {
            return true;
        }
        Orientation o1 = orientation(ax, ay, bx, by, cx, cy);
        Orientation o2 = orientation(ax, ay, bx, by, dx, dy);
        Orientation o3 = orientation(cx, cy, dx, dy, ax, ay);
        Orientation o4 = orientation(cx, cy, dx, dy, bx, by);
        return o1 == Orientation.AMBIGUOUS
                || o2 == Orientation.AMBIGUOUS
                || o3 == Orientation.AMBIGUOUS
                || o4 == Orientation.AMBIGUOUS
                || (o1 != o2 && o3 != o4);
    }

    private static Orientation orientation(
            double ax, double ay, double bx, double by, double cx, double cy) {
        double scale =
                Math.max(
                        Math.max(Math.abs(bx - ax), Math.abs(by - ay)),
                        Math.max(Math.abs(cx - ax), Math.abs(cy - ay)));
        if (scale == 0.0) {
            return Orientation.AMBIGUOUS;
        }
        double ubx = (bx - ax) / scale, uby = (by - ay) / scale;
        double ucx = (cx - ax) / scale, ucy = (cy - ay) / scale;
        double d = Math.fma(ubx, ucy, -uby * ucx);
        double e = 16.0 * Math.ulp(Math.max(1.0, Math.abs(ubx * ucy) + Math.abs(uby * ucx)));
        if (!Double.isFinite(d) || !Double.isFinite(e)) {
            return Orientation.AMBIGUOUS;
        }
        return d > e ? Orientation.POSITIVE : d < -e ? Orientation.NEGATIVE : Orientation.AMBIGUOUS;
    }

    private static boolean strictlyConvex(
            CoordinateSequence ring, TopologyBudget topology, BuildBudget build) {
        Orientation sign = ringSign(ring, topology);
        if (sign == Orientation.AMBIGUOUS) {
            return false;
        }
        NormalizedVertices normalized = normalizedVertices(ring, topology, build);
        if (normalized.count < 3) {
            return false;
        }
        for (int ordinal = 0; ordinal < normalized.count; ordinal++) {
            int index = normalized.indices[ordinal];
            int next = normalized.indices[(ordinal + 1) % normalized.count];
            int after = normalized.indices[(ordinal + 2) % normalized.count];
            topology.charge();
            if (orientation(
                            ring.x(index),
                            ring.y(index),
                            ring.x(next),
                            ring.y(next),
                            ring.x(after),
                            ring.y(after))
                    != sign) {
                return false;
            }
        }
        return true;
    }

    private static PointRelation containsStrict(
            CoordinateSequence ring, double x, double y, TopologyBudget topology) {
        boolean inside = false;
        for (int first = 0; first < ring.size() - 1; first++) {
            int second = first + 1;
            double ax = ring.x(first), ay = ring.y(first);
            double bx = ring.x(second), by = ring.y(second);
            topology.charge();
            if (samePoint(ax, ay, x, y)) {
                return PointRelation.AMBIGUOUS;
            }
            if (samePoint(ax, ay, bx, by)) {
                continue;
            }
            if (Double.compare(ay, by) == 0
                    && Double.compare(y, ay) == 0
                    && x >= Math.min(ax, bx)
                    && x <= Math.max(ax, bx)) {
                return PointRelation.AMBIGUOUS;
            }
            boolean upward = ay <= y && y < by;
            boolean downward = by <= y && y < ay;
            if (!upward && !downward) {
                continue;
            }
            Orientation side = orientation(ax, ay, bx, by, x, y);
            if (side == Orientation.AMBIGUOUS) {
                return PointRelation.AMBIGUOUS;
            }
            if ((upward && side == Orientation.POSITIVE)
                    || (downward && side == Orientation.NEGATIVE)) {
                inside = !inside;
            }
        }
        return inside ? PointRelation.INSIDE : PointRelation.OUTSIDE;
    }

    private static double[] clipRing(double[] ring, Envelope clip, BuildBudget build) {
        double[] result = ring;
        for (int edge = 0; edge < 4; edge++) {
            result = clipRingEdge(result, clip, edge, build);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    private static double[] clipRingEdge(
            double[] ring, Envelope clip, int edge, BuildBudget build) {
        int unique = ring.length / 2 - 1;
        DoubleBuilder output = new DoubleBuilder(build);
        double sx = ring[(unique - 1) * 2], sy = ring[(unique - 1) * 2 + 1];
        boolean sInside = insideEdge(sx, sy, clip, edge);
        for (int index = 0; index < unique; index++) {
            double ex = ring[index * 2], ey = ring[index * 2 + 1];
            boolean eInside = insideEdge(ex, ey, clip, edge);
            if (eInside != sInside) {
                double[] intersection = edgeIntersection(sx, sy, ex, ey, clip, edge, build);
                if (intersection == null) {
                    return null;
                }
                output.add(intersection[0], intersection[1]);
            }
            if (eInside) {
                output.add(ex, ey);
            }
            sx = ex;
            sy = ey;
            sInside = eInside;
        }
        if (output.coordinateCount() < 3) {
            return null;
        }
        output.add(output.firstX(), output.firstY());
        return removeConsecutiveDuplicates(output.toArray(), true, build);
    }

    private static boolean insideEdge(double x, double y, Envelope clip, int edge) {
        return switch (edge) {
            case 0 -> x >= clip.minX();
            case 1 -> x <= clip.maxX();
            case 2 -> y >= clip.minY();
            case 3 -> y <= clip.maxY();
            default -> throw new AssertionError();
        };
    }

    private static double[] edgeIntersection(
            double sx,
            double sy,
            double ex,
            double ey,
            Envelope clip,
            int edge,
            BuildBudget build) {
        double dx = ex - sx, dy = ey - sy;
        double t;
        if (edge < 2) {
            if (dx == 0.0) {
                return null;
            }
            t = ((edge == 0 ? clip.minX() : clip.maxX()) - sx) / dx;
        } else {
            if (dy == 0.0) {
                return null;
            }
            t = ((edge == 2 ? clip.minY() : clip.maxY()) - sy) / dy;
        }
        double x = positiveZero(Math.fma(t, dx, sx)), y = positiveZero(Math.fma(t, dy, sy));
        if (edge < 2) {
            x = edge == 0 ? clip.minX() : clip.maxX();
        } else {
            y = edge == 2 ? clip.minY() : clip.maxY();
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return null;
        }
        double[] result = build.doubles(2);
        result[0] = x;
        result[1] = y;
        return result;
    }

    private static double snapToClip(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return value;
        }
        double scale = Math.max(1.0, Math.max(Math.abs(minimum), Math.abs(maximum)));
        double tolerance = 8.0 * Math.ulp(scale);
        if (Math.abs(value - minimum) <= tolerance) {
            return positiveZero(minimum);
        }
        if (Math.abs(value - maximum) <= tolerance) {
            return positiveZero(maximum);
        }
        return positiveZero(value);
    }

    private static double[] removeConsecutiveDuplicates(
            double[] values, boolean closed, BuildBudget build) {
        DoubleBuilder result = new DoubleBuilder(build);
        int limit = closed ? values.length / 2 - 1 : values.length / 2;
        for (int index = 0; index < limit; index++) {
            double x = positiveZero(values[index * 2]), y = positiveZero(values[index * 2 + 1]);
            if (result.coordinateCount() == 0
                    || Double.compare(result.lastX(), x) != 0
                    || Double.compare(result.lastY(), y) != 0) {
                result.add(x, y);
            }
        }
        if (closed && result.coordinateCount() > 0) {
            result.add(result.firstX(), result.firstY());
        }
        return result.toArray();
    }

    private static boolean same(CoordinateSequence sequence, double[] values) {
        if (values == null || sequence.size() * 2 != values.length) {
            return false;
        }
        for (int index = 0; index < sequence.size(); index++) {
            if (Double.compare(positiveZero(sequence.x(index)), values[index * 2]) != 0
                    || Double.compare(positiveZero(sequence.y(index)), values[index * 2 + 1])
                            != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean distinctEndpoints(double[] values) {
        int last = values.length - 2;
        return Double.compare(values[0], values[last]) != 0
                || Double.compare(values[1], values[last + 1]) != 0;
    }

    private static boolean samePoint(double ax, double ay, double bx, double by) {
        return Double.compare(ax, bx) == 0 && Double.compare(ay, by) == 0;
    }

    private static NormalizedVertices normalizedVertices(
            CoordinateSequence ring, TopologyBudget topology, BuildBudget build) {
        int unique = ring.size() - 1;
        int[] vertices = build.ints(unique);
        int count = 0;
        for (int index = 0; index < unique; index++) {
            topology.charge();
            if (count == 0
                    || !samePoint(
                            ring.x(index),
                            ring.y(index),
                            ring.x(vertices[count - 1]),
                            ring.y(vertices[count - 1]))) {
                vertices[count++] = index;
            }
        }
        if (count > 1
                && samePoint(
                        ring.x(vertices[0]),
                        ring.y(vertices[0]),
                        ring.x(vertices[count - 1]),
                        ring.y(vertices[count - 1]))) {
            count--;
        }
        return new NormalizedVertices(vertices, count);
    }

    private static Orientation ringSign(CoordinateSequence ring, TopologyBudget topology) {
        double sum = 0.0, compensation = 0.0;
        double ox = ring.x(0), oy = ring.y(0);
        double scale = 0.0;
        for (int index = 1; index < ring.size(); index++) {
            topology.charge();
            double dx = ring.x(index) - ox;
            double dy = ring.y(index) - oy;
            if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
                return Orientation.AMBIGUOUS;
            }
            scale = Math.max(scale, Math.max(Math.abs(dx), Math.abs(dy)));
        }
        if (scale == 0.0) {
            return Orientation.AMBIGUOUS;
        }
        for (int index = 0; index < ring.size() - 1; index++) {
            topology.charge();
            double ax = (ring.x(index) - ox) / scale, ay = (ring.y(index) - oy) / scale;
            double bx = (ring.x(index + 1) - ox) / scale, by = (ring.y(index + 1) - oy) / scale;
            double value = Math.fma(ax, by, -ay * bx);
            double next = sum + value;
            compensation +=
                    Math.abs(sum) >= Math.abs(value) ? (sum - next) + value : (value - next) + sum;
            sum = next;
        }
        double normalized = sum + compensation;
        double bound = Math.multiplyExact(64L, ring.size() - 1L) * Math.ulp(1.0);
        if (!Double.isFinite(normalized)
                || !Double.isFinite(bound)
                || Math.abs(normalized) <= bound) {
            return Orientation.AMBIGUOUS;
        }
        return normalized > 0.0 ? Orientation.POSITIVE : Orientation.NEGATIVE;
    }

    private static Envelope polygonEnvelope(PolygonGeometry polygon) {
        Envelope result = polygon.exterior().envelope();
        for (CoordinateSequence hole : polygon.holes()) {
            result = result.union(hole.envelope());
        }
        return result;
    }

    private static boolean contains(Envelope outer, Envelope inner) {
        return outer.minX() <= inner.minX()
                && outer.minY() <= inner.minY()
                && outer.maxX() >= inner.maxX()
                && outer.maxY() >= inner.maxY();
    }

    private static boolean disjoint(Envelope one, Envelope two) {
        return one.maxX() < two.minX()
                || one.minX() > two.maxX()
                || one.maxY() < two.minY()
                || one.minY() > two.maxY();
    }

    private static int coordinateCount(Geometry geometry) {
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof PolygonGeometry polygon) {
            int result = polygon.exterior().size();
            for (CoordinateSequence hole : polygon.holes()) {
                result = Math.addExact(result, hole.size());
            }
            return result;
        }
        return ((MultiPolygonGeometry) geometry).coordinates().size();
    }

    private static int componentCount(Geometry geometry) {
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.partCount();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.polygonCount();
        }
        return 1;
    }

    private static long maximumOutputCoordinateCount(Geometry geometry) {
        if (geometry instanceof LineStringGeometry line) {
            return maximumLineOutputCoordinates(line.coordinates().size());
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            long result = 0;
            for (int part = 0; part < lines.partCount(); part++) {
                result =
                        Math.addExact(
                                result,
                                maximumLineOutputCoordinates(
                                        lines.partOffset(part + 1) - lines.partOffset(part)));
            }
            return result;
        }
        return Math.addExact(
                coordinateCount(geometry), Math.multiplyExact(4L, componentCount(geometry)));
    }

    private static long maximumLineOutputCoordinates(int coordinates) {
        return Math.max(coordinates, Math.multiplyExact(2L, Math.max(0L, coordinates - 1L)));
    }

    private static long completeMultipartResultBuildBytes(
            Geometry geometry, long maximumOutputCoordinates) {
        if (geometry instanceof MultiLineStringGeometry lines) {
            long maximumFragments = 0;
            for (int part = 0; part < lines.partCount(); part++) {
                maximumFragments =
                        Math.addExact(
                                maximumFragments,
                                Math.max(
                                        0L,
                                        (long) lines.partOffset(part + 1)
                                                - lines.partOffset(part)
                                                - 1L));
            }
            return Math.addExact(
                    Math.multiplyExact(maximumOutputCoordinates, 32L),
                    Math.addExact(
                            doubledIntArrayBytes(Math.addExact(maximumFragments, 1L)),
                            doubledIntArrayBytes(Math.addExact((long) lines.partCount(), 1L))));
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return Math.addExact(
                    Math.multiplyExact(maximumOutputCoordinates, 32L),
                    Math.addExact(
                            doubledIntArrayBytes(Math.addExact((long) polygons.ringCount(), 1L)),
                            Math.addExact(
                                    doubledIntArrayBytes(
                                            Math.addExact((long) polygons.polygonCount(), 1L)),
                                    doubledIntArrayBytes(
                                            Math.addExact((long) polygons.polygonCount(), 1L)))));
        }
        return 0L;
    }

    private static long doubledIntArrayBytes(long length) {
        return Math.multiplyExact(Math.multiplyExact(length, Integer.BYTES), 2L);
    }

    private static ScreenGeometryOptimization fallback(Geometry geometry, int components) {
        return new ScreenGeometryOptimization(geometry, components);
    }

    private static ScreenGeometryOptimization result(
            Geometry authoritative,
            Geometry rendering,
            ScreenGeometryOptimizationOutcome outcome,
            int[] offsets,
            BuildBudget build) {
        build.chargeInts(offsets.length);
        return new ScreenGeometryOptimization(authoritative, rendering, outcome, offsets);
    }

    private static CoordinateSequence slice(
            CoordinateSequence source, int first, int last, BuildBudget build) {
        double[] values = build.doubles(Math.multiplyExact(last - first, 2));
        for (int index = first; index < last; index++) {
            values[(index - first) * 2] = source.x(index);
            values[(index - first) * 2 + 1] = source.y(index);
        }
        return sequenceOf(values, build);
    }

    private static double[] copyOf(CoordinateSequence source, BuildBudget build) {
        double[] values = build.doubles(Math.multiplyExact(source.size(), 2));
        for (int index = 0; index < source.size(); index++) {
            values[index * 2] = source.x(index);
            values[index * 2 + 1] = source.y(index);
        }
        return values;
    }

    private static CoordinateSequence sequenceOf(double[] values, BuildBudget build) {
        build.chargeDoubles(values.length);
        return CoordinateSequence.of(values);
    }

    private static MultiLineStringGeometry packLines(
            List<CoordinateSequence> fragments, BuildBudget build) {
        long coordinateCount = 0;
        for (CoordinateSequence fragment : fragments) {
            coordinateCount = Math.addExact(coordinateCount, fragment.size());
        }
        int[] offsets = build.ints(Math.addExact(fragments.size(), 1));
        double[] packed = build.doubles(Math.toIntExact(Math.multiplyExact(coordinateCount, 2L)));
        int coordinate = 0;
        for (int index = 0; index < fragments.size(); index++) {
            CoordinateSequence fragment = fragments.get(index);
            for (int source = 0; source < fragment.size(); source++) {
                packed[coordinate * 2] = fragment.x(source);
                packed[coordinate * 2 + 1] = fragment.y(source);
                coordinate++;
            }
            offsets[index + 1] = coordinate;
        }
        CoordinateSequence sequence = sequenceOf(packed, build);
        build.chargeInts(offsets.length);
        return MultiLineStringGeometry.of(sequence, offsets);
    }

    private static MultiPolygonGeometry packPolygons(
            List<PolygonGeometry> polygons, BuildBudget build) {
        long coordinates = 0;
        int rings = 0;
        for (PolygonGeometry polygon : polygons) {
            rings = Math.addExact(rings, Math.addExact(1, polygon.holes().size()));
            coordinates = Math.addExact(coordinates, polygon.exterior().size());
            for (CoordinateSequence hole : polygon.holes()) {
                coordinates = Math.addExact(coordinates, hole.size());
            }
        }
        int[] ringOffsets = build.ints(Math.addExact(rings, 1));
        int[] polygonOffsets = build.ints(Math.addExact(polygons.size(), 1));
        double[] packed = build.doubles(Math.toIntExact(Math.multiplyExact(coordinates, 2L)));
        int coordinate = 0;
        int ring = 0;
        for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
            PolygonGeometry polygon = polygons.get(polygonIndex);
            coordinate = append(polygon.exterior(), packed, coordinate);
            ringOffsets[++ring] = coordinate;
            for (CoordinateSequence hole : polygon.holes()) {
                coordinate = append(hole, packed, coordinate);
                ringOffsets[++ring] = coordinate;
            }
            polygonOffsets[polygonIndex + 1] = ring;
        }
        CoordinateSequence sequence = sequenceOf(packed, build);
        build.chargeInts(ringOffsets.length);
        build.chargeInts(polygonOffsets.length);
        return MultiPolygonGeometry.of(sequence, ringOffsets, polygonOffsets);
    }

    private static int append(CoordinateSequence source, double[] target, int coordinate) {
        for (int index = 0; index < source.size(); index++) {
            target[coordinate * 2] = source.x(index);
            target[coordinate * 2 + 1] = source.y(index);
            coordinate++;
        }
        return coordinate;
    }

    private static double positiveZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    private enum Orientation {
        NEGATIVE,
        POSITIVE,
        AMBIGUOUS
    }

    private enum PointRelation {
        OUTSIDE,
        INSIDE,
        AMBIGUOUS
    }

    private static final class NormalizedVertices {
        private final int[] indices;
        private final int count;

        private NormalizedVertices(int[] indices, int count) {
            this.indices = indices;
            this.count = count;
        }
    }

    private static final class ClipRange {
        private double minimum;
        private double maximum = 1.0;
    }

    private static final class TopologyBudget {
        private final long maximum;
        private long used;

        private TopologyBudget(long maximum) {
            this.maximum = maximum;
        }

        private void charge() {
            if (used == maximum) {
                throw new TopologyLimitExceededException();
            }
            used++;
        }
    }

    private static final class BuildBudget {
        private final long maximum;
        private long used;

        private BuildBudget(long maximum) {
            this.maximum = maximum;
        }

        private void charge(long bytes) {
            long next = Math.addExact(used, bytes);
            if (next > maximum) {
                throw new BuildLimitExceededException();
            }
            used = next;
        }

        private void chargeDoubles(int length) {
            charge(Math.multiplyExact((long) length, Double.BYTES));
        }

        private void chargeInts(int length) {
            charge(Math.multiplyExact((long) length, Integer.BYTES));
        }

        private double[] doubles(int length) {
            chargeDoubles(length);
            return new double[length];
        }

        private int[] ints(int length) {
            chargeInts(length);
            return new int[length];
        }

        private boolean[] booleans(int length) {
            charge(length);
            return new boolean[length];
        }

        private double[] copy(double[] source, int length) {
            chargeDoubles(length);
            return java.util.Arrays.copyOf(source, length);
        }
    }

    private static final class NumericUnsafeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private NumericUnsafeException() {
            super(null, null, false, false);
        }
    }

    private static final class BuildLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private BuildLimitExceededException() {
            super(null, null, false, false);
        }
    }

    private static final class TopologyLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private TopologyLimitExceededException() {
            super(null, null, false, false);
        }
    }

    private static final class DoubleBuilder {
        private final BuildBudget build;
        private double[] values;
        private int size;

        private DoubleBuilder(BuildBudget build) {
            this.build = build;
            values = build.doubles(16);
        }

        void add(double x, double y) {
            ensure(size + 2);
            values[size++] = positiveZero(x);
            values[size++] = positiveZero(y);
        }

        int coordinateCount() {
            return size / 2;
        }

        double firstX() {
            return values[0];
        }

        double firstY() {
            return values[1];
        }

        double lastX() {
            return values[size - 2];
        }

        double lastY() {
            return values[size - 1];
        }

        double[] toArray() {
            return build.copy(values, size);
        }

        private void ensure(int required) {
            if (required > values.length) {
                int next = Math.max(required, Math.multiplyExact(values.length, 2));
                values = build.copy(values, next);
            }
        }
    }
}
