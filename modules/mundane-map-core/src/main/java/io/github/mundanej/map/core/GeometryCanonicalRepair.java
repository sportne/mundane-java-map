package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.GeometryLimits;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit canonical repair for a frozen, deliberately narrow polygon-defect set. */
@SuppressWarnings("StringConcatToTextBlock")
public final class GeometryCanonicalRepair {
    private GeometryCanonicalRepair() {}

    /** Defects that callers may explicitly select for canonical repair. */
    public enum Defect {
        /** Remove adjacent duplicate x/y ring positions, retaining the first full position. */
        DUPLICATE_RING_POSITIONS,
        /** Orient exterior rings counter-clockwise and holes clockwise in x/y. */
        RING_ORIENTATION
    }

    /**
     * Repairs only the selected defects using default prospective limits.
     *
     * <p>Empty values, non-polygon values, collection nesting, and collection order are preserved.
     * Z and M ordinates remain attached to their vertex when rings are reversed. No repair happens
     * unless its defect is present in {@code defects}; this method is not called by parsers or
     * renderers.
     *
     * @param geometry immutable input
     * @param defects explicitly selected frozen defect set
     * @return canonical repaired value, or the input when no selected change applies
     */
    public static Geometry repair(Geometry geometry, Collection<Defect> defects) {
        return repair(geometry, defects, GeometryTopologyLimits.DEFAULT);
    }

    /**
     * Repairs only the selected defects under explicit prospective limits.
     *
     * @param geometry immutable input
     * @param defects explicitly selected frozen defect set
     * @param limits prospective limits
     * @return complete canonical repaired value
     * @throws GeometryTopologyException when a limit is exceeded before publication
     * @throws IllegalArgumentException when duplicate removal would leave an invalid ring
     */
    public static Geometry repair(
            Geometry geometry, Collection<Defect> defects, GeometryTopologyLimits limits) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(defects, "defects");
        Set<Defect> selected = defects.isEmpty() ? Set.of() : EnumSet.copyOf(defects);
        RepairWork work = new RepairWork(Objects.requireNonNull(limits, "limits"));
        return repairGeometry(geometry, selected, work);
    }

    private static Geometry repairGeometry(
            Geometry geometry, Set<Defect> defects, RepairWork work) {
        if (geometry.isEmpty() || defects.isEmpty()) {
            return geometry;
        }
        if (geometry instanceof GeometryCollection collection) {
            List<Geometry> repaired = new ArrayList<>(collection.geometries().size());
            boolean changed = false;
            for (Geometry child : collection.geometries()) {
                Geometry value = repairGeometry(child, defects, work);
                repaired.add(value);
                changed |= value != child;
            }
            return changed ? GeometryCollection.of(repaired) : geometry;
        }
        if (geometry.kind() != GeometryKind.POLYGON
                && geometry.kind() != GeometryKind.MULTI_POLYGON) {
            return geometry;
        }
        GeometryValidity.Shape shape = GeometryValidity.Shape.of(geometry, work.topology);
        List<List<List<double[]>>> polygons = new ArrayList<>(shape.polygons.size());
        boolean changed = false;
        for (List<GeometryValidity.Part> polygon : shape.polygons) {
            List<List<double[]>> rings = new ArrayList<>(polygon.size());
            for (int ringIndex = 0; ringIndex < polygon.size(); ringIndex++) {
                List<double[]> ring = positions(polygon.get(ringIndex));
                if (defects.contains(Defect.DUPLICATE_RING_POSITIONS)) {
                    List<double[]> deduplicated = removeDuplicates(ring);
                    changed |= deduplicated.size() != ring.size();
                    ring = deduplicated;
                }
                if (ring.size() < 4) {
                    throw new IllegalArgumentException(
                            "Canonical duplicate removal would leave an invalid polygon ring");
                }
                if (defects.contains(Defect.RING_ORIENTATION)) {
                    boolean counterClockwise = signedArea(ring) > 0.0;
                    boolean wantedCounterClockwise = ringIndex == 0;
                    if (counterClockwise != wantedCounterClockwise) {
                        ring = reverseClosed(ring);
                        changed = true;
                    }
                }
                rings.add(ring);
            }
            polygons.add(rings);
        }
        if (!changed) {
            return geometry;
        }
        List<List<double[]>> rings = polygons.stream().flatMap(List::stream).toList();
        int outputCount = rings.stream().mapToInt(List::size).sum();
        work.output(outputCount);
        GeometryDimension dimension = shape.coordinates.dimension();
        CoordinateSequence coordinates = sequence(dimension, rings);
        int[] ringOffsets = offsets(rings);
        int[] polygonOffsets = new int[polygons.size() + 1];
        for (int index = 0; index < polygons.size(); index++) {
            polygonOffsets[index + 1] = polygonOffsets[index] + polygons.get(index).size();
        }
        if (geometry instanceof DimensionalGeometry) {
            return geometry.kind() == GeometryKind.POLYGON
                    ? DimensionalGeometry.polygon(coordinates, ringOffsets)
                    : DimensionalGeometry.multiPolygon(
                            coordinates, ringOffsets, polygonOffsets, GeometryLimits.DEFAULT);
        }
        if (geometry.kind() == GeometryKind.POLYGON) {
            return polygon(rings);
        }
        List<PolygonGeometry> values = new ArrayList<>(polygons.size());
        for (List<List<double[]>> polygon : polygons) {
            values.add(polygon(polygon));
        }
        return MultiPolygonGeometry.ofPolygons(values);
    }

    private static List<double[]> positions(GeometryValidity.Part part) {
        List<double[]> result = new ArrayList<>(part.size());
        for (int index = 0; index < part.size(); index++) {
            result.add(position(part.coordinates, part.start + index));
        }
        return result;
    }

    private static List<double[]> removeDuplicates(List<double[]> ring) {
        List<double[]> result = new ArrayList<>(ring.size());
        for (double[] position : ring) {
            if (result.isEmpty() || !sameXy(result.getLast(), position)) {
                result.add(position);
            }
        }
        if (!sameXy(result.getFirst(), result.getLast())) {
            result.add(result.getFirst().clone());
        } else if (!sameAll(result.getFirst(), result.getLast())) {
            result.set(result.size() - 1, result.getFirst().clone());
        }
        return result;
    }

    private static List<double[]> reverseClosed(List<double[]> ring) {
        List<double[]> result = new ArrayList<>(ring.size());
        result.add(ring.getFirst());
        for (int index = ring.size() - 2; index > 0; index--) {
            result.add(ring.get(index));
        }
        result.add(ring.getFirst().clone());
        return result;
    }

    private static PolygonGeometry polygon(List<List<double[]>> rings) {
        CoordinateSequence exterior = sequence(GeometryDimension.XY, List.of(rings.getFirst()));
        List<CoordinateSequence> holes = new ArrayList<>();
        for (int index = 1; index < rings.size(); index++) {
            holes.add(sequence(GeometryDimension.XY, List.of(rings.get(index))));
        }
        return new PolygonGeometry(exterior, holes);
    }

    private static CoordinateSequence sequence(
            GeometryDimension dimension, List<? extends List<double[]>> rings) {
        int count = rings.stream().mapToInt(List::size).sum();
        int stride = dimension.stride();
        double[] packed = new double[Math.multiplyExact(count, stride)];
        int target = 0;
        for (List<double[]> ring : rings) {
            for (double[] position : ring) {
                System.arraycopy(position, 0, packed, target, stride);
                target += stride;
            }
        }
        return CoordinateSequence.of(dimension, packed);
    }

    private static int[] offsets(List<? extends List<double[]>> rings) {
        int[] values = new int[rings.size() + 1];
        for (int index = 0; index < rings.size(); index++) {
            values[index + 1] = values[index] + rings.get(index).size();
        }
        return values;
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

    private static double signedArea(List<double[]> ring) {
        double result = 0.0;
        for (int index = 1; index < ring.size(); index++) {
            result +=
                    ring.get(index - 1)[0] * ring.get(index)[1]
                            - ring.get(index)[0] * ring.get(index - 1)[1];
        }
        return result / 2.0;
    }

    private static boolean sameXy(double[] first, double[] second) {
        return Double.compare(first[0], second[0]) == 0 && Double.compare(first[1], second[1]) == 0;
    }

    private static boolean sameAll(double[] first, double[] second) {
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

    private static final class RepairWork {
        private final GeometryTopologyLimits limits;
        private final GeometryValidity.Work topology;
        private int output;

        private RepairWork(GeometryTopologyLimits limits) {
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
