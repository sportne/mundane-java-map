package io.github.mundanej.map.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, ordered, heterogeneous, and explicitly bounded geometry collection. */
public final class GeometryCollection implements Geometry {
    private final List<Geometry> geometries;
    private final GeometryDimension emptyDimension;
    private final Optional<Envelope> bounds;
    private final GeometryDimension dimension;

    private GeometryCollection(
            List<Geometry> geometries, GeometryDimension emptyDimension, GeometryLimits limits) {
        this.geometries = List.copyOf(Objects.requireNonNull(geometries, "geometries"));
        this.emptyDimension = Objects.requireNonNull(emptyDimension, "emptyDimension");
        Summary summary = summarize(Objects.requireNonNull(limits, "limits"));
        this.bounds = summary.bounds();
        this.dimension = summary.dimension();
    }

    /**
     * Creates a collection under default safety limits.
     *
     * @param geometries ordered child values
     * @return immutable collection
     */
    public static GeometryCollection of(List<? extends Geometry> geometries) {
        return of(geometries, GeometryLimits.DEFAULT);
    }

    /**
     * Creates a collection under explicit safety limits.
     *
     * @param geometries ordered child values
     * @param limits construction and nesting limits
     * @return immutable collection
     */
    public static GeometryCollection of(
            List<? extends Geometry> geometries, GeometryLimits limits) {
        Objects.requireNonNull(geometries, "geometries");
        return new GeometryCollection(List.copyOf(geometries), GeometryDimension.XY, limits);
    }

    /**
     * Creates an empty collection retaining an explicit dimensional model.
     *
     * @param dimension retained model
     * @return immutable empty collection
     */
    public static GeometryCollection empty(GeometryDimension dimension) {
        return new GeometryCollection(List.of(), dimension, GeometryLimits.DEFAULT);
    }

    /**
     * Returns immutable ordered child geometries.
     *
     * @return child values
     */
    public List<Geometry> geometries() {
        return geometries;
    }

    @Override
    public GeometryKind kind() {
        return GeometryKind.GEOMETRY_COLLECTION;
    }

    @Override
    public GeometryDimension dimension() {
        return dimension;
    }

    @Override
    public boolean isEmpty() {
        return bounds.isEmpty();
    }

    @Override
    public Optional<Envelope> bounds() {
        return bounds;
    }

    @Override
    public Envelope envelope() {
        return bounds.orElseThrow(() -> GeometryException.emptyEnvelope(kind()));
    }

    private Summary summarize(GeometryLimits limits) {
        Deque<Node> pending = new ArrayDeque<>();
        for (int index = geometries.size() - 1; index >= 0; index--) {
            pending.push(new Node(Objects.requireNonNull(geometries.get(index), "geometry"), 1));
        }
        long coordinateCount = 0;
        long partCount = 0;
        long elementCount = geometries.size();
        Optional<Envelope> combined = Optional.empty();
        GeometryDimension combinedDimension = emptyDimension;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node.depth() > limits.maxDepth()) {
                throw GeometryException.limit("maxDepth", node.depth(), limits.maxDepth());
            }
            Geometry geometry = node.geometry();
            combinedDimension = combinedDimension.union(geometry.dimension());
            if (geometry instanceof GeometryCollection collection) {
                elementCount = Math.addExact(elementCount, collection.geometries.size());
                for (int index = collection.geometries.size() - 1; index >= 0; index--) {
                    pending.push(new Node(collection.geometries.get(index), node.depth() + 1));
                }
            } else {
                coordinateCount = Math.addExact(coordinateCount, coordinateCount(geometry));
                partCount = Math.addExact(partCount, partCount(geometry));
            }
            if (geometry.bounds().isPresent()) {
                Envelope envelope = geometry.bounds().orElseThrow();
                combined =
                        combined.isEmpty()
                                ? Optional.of(envelope)
                                : Optional.of(combined.orElseThrow().union(envelope));
            }
        }
        checkLimit("maxCoordinates", coordinateCount, limits.maxCoordinates());
        checkLimit("maxParts", partCount, limits.maxParts());
        checkLimit("maxCollectionElements", elementCount, limits.maxCollectionElements());
        return new Summary(combined, combinedDimension);
    }

    private static long coordinateCount(Geometry geometry) {
        if (geometry instanceof EmptyGeometry) {
            return 0;
        }
        if (geometry instanceof DimensionalGeometry dimensional) {
            return dimensional.coordinates().size();
        }
        if (geometry instanceof PointGeometry) {
            return 1;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return points.coordinates().size();
        }
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof PolygonGeometry polygon) {
            long count = polygon.exterior().size();
            for (CoordinateSequence hole : polygon.holes()) {
                count = Math.addExact(count, hole.size());
            }
            return count;
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.coordinates().size();
        }
        throw new IllegalArgumentException("Unsupported geometry implementation");
    }

    private static long partCount(Geometry geometry) {
        if (geometry instanceof EmptyGeometry) {
            return 0;
        }
        if (geometry instanceof DimensionalGeometry dimensional) {
            return Math.max(1, dimensional.partCount());
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.partCount();
        }
        if (geometry instanceof PolygonGeometry polygon) {
            return 1L + polygon.holes().size();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.ringCount();
        }
        return 1;
    }

    private static void checkLimit(String name, long actual, long maximum) {
        if (actual > maximum) {
            throw GeometryException.limit(name, actual, maximum);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GeometryCollection collection
                && geometries.equals(collection.geometries)
                && emptyDimension == collection.emptyDimension;
    }

    @Override
    public int hashCode() {
        return 31 * geometries.hashCode() + emptyDimension.hashCode();
    }

    @Override
    public String toString() {
        return "GeometryCollection[geometries="
                + geometries
                + ", emptyDimension="
                + emptyDimension
                + "]";
    }

    private record Node(Geometry geometry, int depth) {}

    private record Summary(Optional<Envelope> bounds, GeometryDimension dimension) {}
}
