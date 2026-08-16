package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded x/y predicates for immutable geometry values. */
public final class GeometryPredicates {
    private GeometryPredicates() {}

    /**
     * Returns whether two geometries share any x/y point using default limits.
     *
     * <p>Empty values never intersect. Z and M ordinates do not participate. Collections are
     * evaluated in encounter order. Boundary contact counts as intersection.
     *
     * @param first first geometry
     * @param second second geometry
     * @return whether the geometries intersect
     */
    public static boolean intersects(Geometry first, Geometry second) {
        return intersects(first, second, GeometryTopologyLimits.DEFAULT);
    }

    /**
     * Returns whether two geometries share any x/y point under explicit prospective limits.
     *
     * @param first first geometry
     * @param second second geometry
     * @param limits work limits
     * @return whether the geometries intersect
     * @throws GeometryTopologyException when a prospective limit is exceeded
     */
    public static boolean intersects(
            Geometry first, Geometry second, GeometryTopologyLimits limits) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        GeometryValidity.Work work =
                new GeometryValidity.Work(Objects.requireNonNull(limits, "limits"));
        List<Geometry> firstLeaves = leaves(first);
        List<Geometry> secondLeaves = leaves(second);
        List<GeometryValidity.Shape> firstShapes = shapes(firstLeaves, work);
        List<GeometryValidity.Shape> secondShapes = shapes(secondLeaves, work);
        for (GeometryValidity.Shape a : firstShapes) {
            for (GeometryValidity.Shape b : secondShapes) {
                if (intersects(a, b, work)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<GeometryValidity.Shape> shapes(
            List<Geometry> geometries, GeometryValidity.Work work) {
        List<GeometryValidity.Shape> shapes = new ArrayList<>(geometries.size());
        for (Geometry geometry : geometries) {
            shapes.add(GeometryValidity.Shape.of(geometry, work));
        }
        return shapes;
    }

    private static List<Geometry> leaves(Geometry geometry) {
        List<Geometry> result = new ArrayList<>();
        addLeaves(geometry, result);
        return result;
    }

    private static void addLeaves(Geometry geometry, List<Geometry> target) {
        if (geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (Geometry child : collection.geometries()) {
                addLeaves(child, target);
            }
        } else {
            target.add(geometry);
        }
    }

    private static boolean intersects(
            GeometryValidity.Shape first,
            GeometryValidity.Shape second,
            GeometryValidity.Work work) {
        if (isPoint(first.kind)) {
            for (int index = 0; index < first.coordinates.size(); index++) {
                if (containsPoint(
                        second, first.coordinates.x(index), first.coordinates.y(index), work)) {
                    return true;
                }
            }
            return false;
        }
        if (isPoint(second.kind)) {
            return intersects(second, first, work);
        }
        if (isLine(first.kind) && isLine(second.kind)) {
            return lineBoundariesIntersect(first.parts, second.parts, work);
        }
        if (isLine(first.kind) && isPolygon(second.kind)) {
            return linePolygonIntersects(first, second, work);
        }
        if (isPolygon(first.kind) && isLine(second.kind)) {
            return linePolygonIntersects(second, first, work);
        }
        return polygonIntersects(first, second, work);
    }

    private static boolean containsPoint(
            GeometryValidity.Shape shape, double x, double y, GeometryValidity.Work work) {
        if (isPoint(shape.kind)) {
            for (int index = 0; index < shape.coordinates.size(); index++) {
                work.comparison();
                if (Double.compare(x, shape.coordinates.x(index)) == 0
                        && Double.compare(y, shape.coordinates.y(index)) == 0) {
                    return true;
                }
            }
            return false;
        }
        if (isLine(shape.kind)) {
            for (GeometryValidity.Part part : shape.parts) {
                for (int segment = 0; segment < part.size() - 1; segment++) {
                    work.comparison();
                    if (GeometryValidity.cross(
                                            part.x(segment),
                                            part.y(segment),
                                            part.x(segment + 1),
                                            part.y(segment + 1),
                                            x,
                                            y)
                                    == 0.0
                            && GeometryValidity.onSegment(
                                    part.x(segment),
                                    part.y(segment),
                                    part.x(segment + 1),
                                    part.y(segment + 1),
                                    x,
                                    y)) {
                        return true;
                    }
                }
            }
            return false;
        }
        for (List<GeometryValidity.Part> polygon : shape.polygons) {
            GeometryValidity.Location shell =
                    GeometryValidity.pointInRing(x, y, polygon.getFirst(), work);
            if (shell == GeometryValidity.Location.EXTERIOR) {
                continue;
            }
            if (shell == GeometryValidity.Location.BOUNDARY) {
                return true;
            }
            boolean inHole = false;
            for (int hole = 1; hole < polygon.size(); hole++) {
                GeometryValidity.Location location =
                        GeometryValidity.pointInRing(x, y, polygon.get(hole), work);
                if (location == GeometryValidity.Location.BOUNDARY) {
                    return true;
                }
                inHole |= location == GeometryValidity.Location.INTERIOR;
            }
            if (!inHole) {
                return true;
            }
        }
        return false;
    }

    private static boolean lineBoundariesIntersect(
            List<GeometryValidity.Part> first,
            List<GeometryValidity.Part> second,
            GeometryValidity.Work work) {
        for (GeometryValidity.Part a : first) {
            for (GeometryValidity.Part b : second) {
                for (int ai = 0; ai < a.size() - 1; ai++) {
                    for (int bi = 0; bi < b.size() - 1; bi++) {
                        work.comparison();
                        if (GeometryValidity.segmentIntersection(a, ai, b, bi).isPresent()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean linePolygonIntersects(
            GeometryValidity.Shape lines,
            GeometryValidity.Shape polygons,
            GeometryValidity.Work work) {
        List<GeometryValidity.Part> rings =
                polygons.polygons.stream().flatMap(List::stream).toList();
        if (lineBoundariesIntersect(lines.parts, rings, work)) {
            return true;
        }
        for (GeometryValidity.Part line : lines.parts) {
            if (containsPoint(polygons, line.x(0), line.y(0), work)) {
                return true;
            }
        }
        return false;
    }

    private static boolean polygonIntersects(
            GeometryValidity.Shape first,
            GeometryValidity.Shape second,
            GeometryValidity.Work work) {
        List<GeometryValidity.Part> firstRings =
                first.polygons.stream().flatMap(List::stream).toList();
        List<GeometryValidity.Part> secondRings =
                second.polygons.stream().flatMap(List::stream).toList();
        if (lineBoundariesIntersect(firstRings, secondRings, work)) {
            return true;
        }
        for (List<GeometryValidity.Part> polygon : first.polygons) {
            GeometryValidity.Part shell = polygon.getFirst();
            if (containsPoint(second, shell.x(0), shell.y(0), work)) {
                return true;
            }
        }
        for (List<GeometryValidity.Part> polygon : second.polygons) {
            GeometryValidity.Part shell = polygon.getFirst();
            if (containsPoint(first, shell.x(0), shell.y(0), work)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPoint(GeometryKind kind) {
        return kind == GeometryKind.POINT || kind == GeometryKind.MULTI_POINT;
    }

    private static boolean isLine(GeometryKind kind) {
        return kind == GeometryKind.LINE_STRING || kind == GeometryKind.MULTI_LINE_STRING;
    }

    private static boolean isPolygon(GeometryKind kind) {
        return kind == GeometryKind.POLYGON || kind == GeometryKind.MULTI_POLYGON;
    }
}
