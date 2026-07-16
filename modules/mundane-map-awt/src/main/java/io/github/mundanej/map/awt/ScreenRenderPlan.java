package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.ScreenGeometryOptimization;

/** Paint-call-scoped references to one packed core screen-geometry result. */
final class ScreenRenderPlan {
    private final Geometry authoritative;
    private final Geometry rendering;
    private final ScreenGeometryOptimization optimization;

    ScreenRenderPlan(Geometry authoritative) {
        this.authoritative = authoritative;
        rendering = authoritative;
        optimization = null;
    }

    ScreenRenderPlan(ScreenGeometryOptimization optimization) {
        this.optimization = optimization;
        authoritative = optimization.authoritativeGeometry();
        rendering = optimization.renderingGeometry().orElse(null);
    }

    int sourceComponentCount() {
        return optimization == null
                ? componentCount(authoritative)
                : optimization.sourceComponentCount();
    }

    int renderComponentOffset(int sourceComponentFenceIndex) {
        return optimization == null
                ? sourceComponentFenceIndex
                : optimization.renderComponentOffset(sourceComponentFenceIndex);
    }

    CoordinateSequence authoritativeLineCoordinates() {
        return lineCoordinates(authoritative);
    }

    int authoritativeLineStart(int sourceComponent) {
        return lineStart(authoritative, sourceComponent);
    }

    int authoritativeLineEnd(int sourceComponent) {
        return lineEnd(authoritative, sourceComponent);
    }

    CoordinateSequence renderingLineCoordinates() {
        return lineCoordinates(rendering);
    }

    int renderingLineStart(int renderComponent) {
        return lineStart(rendering, renderComponent);
    }

    int renderingLineEnd(int renderComponent) {
        return lineEnd(rendering, renderComponent);
    }

    int renderingRingCount(int sourceComponent) {
        if (renderComponentOffset(sourceComponent) == renderComponentOffset(sourceComponent + 1)) {
            return 0;
        }
        return polygonRingCount(rendering, renderComponentOffset(sourceComponent));
    }

    CoordinateSequence renderingRingCoordinates(int sourceComponent, int ring) {
        return polygonRingCoordinates(rendering, ring);
    }

    int renderingRingStart(int sourceComponent, int ring) {
        return polygonRingStart(rendering, renderComponentOffset(sourceComponent), ring);
    }

    int renderingRingEnd(int sourceComponent, int ring) {
        return polygonRingEnd(rendering, renderComponentOffset(sourceComponent), ring);
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

    private static CoordinateSequence lineCoordinates(Geometry geometry) {
        return geometry instanceof LineStringGeometry line
                ? line.coordinates()
                : ((MultiLineStringGeometry) geometry).coordinates();
    }

    private static int lineStart(Geometry geometry, int component) {
        return geometry instanceof LineStringGeometry
                ? 0
                : ((MultiLineStringGeometry) geometry).partOffset(component);
    }

    private static int lineEnd(Geometry geometry, int component) {
        return geometry instanceof LineStringGeometry line
                ? line.coordinates().size()
                : ((MultiLineStringGeometry) geometry).partOffset(component + 1);
    }

    private static int polygonRingCount(Geometry geometry, int polygon) {
        return geometry instanceof PolygonGeometry value
                ? value.holes().size() + 1
                : ((MultiPolygonGeometry) geometry).polygonRingOffset(polygon + 1)
                        - ((MultiPolygonGeometry) geometry).polygonRingOffset(polygon);
    }

    private static CoordinateSequence polygonRingCoordinates(Geometry geometry, int ring) {
        if (geometry instanceof PolygonGeometry value) {
            return ring == 0 ? value.exterior() : value.holes().get(ring - 1);
        }
        return ((MultiPolygonGeometry) geometry).coordinates();
    }

    private static int polygonRingStart(Geometry geometry, int polygon, int ring) {
        if (geometry instanceof PolygonGeometry) {
            return 0;
        }
        MultiPolygonGeometry value = (MultiPolygonGeometry) geometry;
        return value.ringOffset(value.polygonRingOffset(polygon) + ring);
    }

    private static int polygonRingEnd(Geometry geometry, int polygon, int ring) {
        if (geometry instanceof PolygonGeometry value) {
            return polygonRingCoordinates(value, ring).size();
        }
        MultiPolygonGeometry value = (MultiPolygonGeometry) geometry;
        return value.ringOffset(value.polygonRingOffset(polygon) + ring + 1);
    }
}
