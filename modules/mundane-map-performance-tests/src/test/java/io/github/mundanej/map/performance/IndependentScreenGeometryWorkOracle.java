package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.ScreenGeometryOptimizationLimits;
import io.github.mundanej.map.core.ScreenGeometryOptimizationOutcome;
import io.github.mundanej.map.core.ScreenGeometryOptimizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent fixture-to-core derivation of the fixed G7-003 work facts. */
final class IndependentScreenGeometryWorkOracle {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double PADDING = 24.0;
    private static final double TOLERANCE = 0.25;
    private static final double LINE_MARGIN = 2.0;
    private static final double FILL_MARGIN = 1.0;

    private IndependentScreenGeometryWorkOracle() {}

    static Map<String, Map<String, Long>> derive(EvidenceConfiguration.Profile profile) {
        List<FeatureRecord> dense = FixtureCatalog.vectorRecords(profile);
        List<FeatureRecord> small =
                dense.stream()
                        .filter(
                                record ->
                                        record.id().equals("line:000")
                                                || record.id().equals("polygon:000"))
                        .toList();
        LinkedHashMap<String, Map<String, Long>> result = new LinkedHashMap<>();
        result.put("dense-vector-render", source(profile, dense, Trace.FRAME, false));
        result.put("symbol-heavy-render", symbols(profile));
        result.put("vector-pan-sequence", source(profile, dense, Trace.PAN, false));
        result.put("vector-zoom-sequence", source(profile, dense, Trace.ZOOM, false));
        result.put("dense-vector-render-indexed", result.get("dense-vector-render"));
        result.put("vector-pan-sequence-indexed", result.get("vector-pan-sequence"));
        result.put("vector-zoom-sequence-indexed", result.get("vector-zoom-sequence"));
        result.put("small-vector-render-unoptimized", source(profile, small, Trace.FRAME, false));
        result.put("small-vector-render-optimized", source(profile, small, Trace.FRAME, true));
        result.put("dense-vector-render-optimized", source(profile, dense, Trace.FRAME, true));
        result.put("vector-pan-sequence-optimized", source(profile, dense, Trace.PAN, true));
        result.put("vector-zoom-sequence-optimized", source(profile, dense, Trace.ZOOM, true));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, Long> symbols(EvidenceConfiguration.Profile profile) {
        List<InMemoryLayer> layers = FixtureCatalog.symbolLayers(profile);
        long features = layers.stream().mapToLong(layer -> layer.features().size()).sum();
        Work work = new Work();
        for (InMemoryLayer layer : layers) {
            for (Feature feature : layer.features()) {
                if (isPath(feature.geometry())) {
                    work.addDisabled(feature.geometry());
                }
            }
        }
        return work.counters(1, features);
    }

    private static Map<String, Long> source(
            EvidenceConfiguration.Profile profile,
            List<FeatureRecord> records,
            Trace trace,
            boolean optimized) {
        int frames = trace.frames(profile);
        MapViewport viewport = MapViewport.fit(WIDTH, HEIGHT, envelope(records), PADDING);
        Work work = new Work();
        for (int index = 0; index < frames; index++) {
            viewport = trace.next(viewport, index, frames);
            Envelope visible = viewport.visibleWorldEnvelope();
            for (FeatureRecord record : records) {
                Geometry geometry = record.geometry();
                if (intersects(geometry.envelope(), visible) && isPath(geometry)) {
                    if (optimized) {
                        work.addOptimized(toScreen(geometry, viewport), margin(geometry));
                    } else {
                        work.addDisabled(geometry);
                    }
                }
            }
        }
        return work.counters(frames, records.size());
    }

    private static Envelope envelope(List<FeatureRecord> records) {
        Envelope result = records.getFirst().geometry().envelope();
        for (int index = 1; index < records.size(); index++) {
            result = result.union(records.get(index).geometry().envelope());
        }
        return result;
    }

    private static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private static double margin(Geometry geometry) {
        return geometry instanceof LineStringGeometry || geometry instanceof MultiLineStringGeometry
                ? LINE_MARGIN
                : FILL_MARGIN;
    }

    private static boolean isPath(Geometry geometry) {
        return geometry instanceof LineStringGeometry
                || geometry instanceof MultiLineStringGeometry
                || geometry instanceof PolygonGeometry
                || geometry instanceof MultiPolygonGeometry;
    }

    private static Geometry toScreen(Geometry geometry, MapViewport viewport) {
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(toScreen(line.coordinates(), viewport));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    toScreen(lines.coordinates(), viewport), lines.partOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            return new PolygonGeometry(
                    toScreen(polygon.exterior(), viewport),
                    polygon.holes().stream().map(ring -> toScreen(ring, viewport)).toList());
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        return MultiPolygonGeometry.of(
                toScreen(polygons.coordinates(), viewport),
                polygons.ringOffsets(),
                polygons.polygonRingOffsets());
    }

    private static CoordinateSequence toScreen(CoordinateSequence source, MapViewport viewport) {
        double[] result = new double[Math.multiplyExact(source.size(), 2)];
        for (int index = 0; index < source.size(); index++) {
            Coordinate screen = viewport.worldToScreen(source.coordinate(index));
            result[index * 2] = screen.x();
            result[index * 2 + 1] = screen.y();
        }
        return CoordinateSequence.of(result);
    }

    private static long coordinateCount(Geometry geometry) {
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof PolygonGeometry polygon) {
            long result = polygon.exterior().size();
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

    private static long logicalBytes(Geometry geometry) {
        long result = Math.multiplyExact(coordinateCount(geometry), 16L);
        if (geometry instanceof MultiLineStringGeometry lines) {
            return Math.addExact(result, Math.multiplyExact((long) lines.partCount() + 1L, 4L));
        }
        if (geometry instanceof PolygonGeometry polygon) {
            return Math.addExact(
                    result, Math.multiplyExact((long) polygon.holes().size() + 2L, 4L));
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            result =
                    Math.addExact(result, Math.multiplyExact((long) polygons.ringCount() + 1L, 4L));
            return Math.addExact(
                    result, Math.multiplyExact((long) polygons.polygonCount() + 1L, 4L));
        }
        return result;
    }

    private enum Trace {
        FRAME,
        PAN,
        ZOOM;

        int frames(EvidenceConfiguration.Profile profile) {
            if (this == FRAME) {
                return 1;
            }
            if (profile == EvidenceConfiguration.Profile.SMOKE) {
                return 4;
            }
            return this == PAN ? 16 : 12;
        }

        MapViewport next(MapViewport viewport, int index, int frames) {
            if (this == FRAME) {
                return viewport;
            }
            if (this == ZOOM) {
                return viewport.zoomAt(400, 300, index % 2 == 0 ? 1.25 : 0.8);
            }
            int[][] deltas = {{12, 0}, {0, 12}, {-12, 0}, {0, -12}};
            int[] delta = deltas[index / (frames / 4)];
            return viewport.panByPixels(delta[0], delta[1]);
        }
    }

    private static final class Work {
        private long input;
        private long rendered;
        private long fragments;
        private long culled;
        private long fallbacks;
        private long retainedBytes;

        void addDisabled(Geometry geometry) {
            long coordinates = coordinateCount(geometry);
            input = Math.addExact(input, coordinates);
            rendered = Math.addExact(rendered, coordinates);
            if (geometry instanceof LineStringGeometry
                    || geometry instanceof MultiLineStringGeometry) {
                fragments = Math.addExact(fragments, componentCount(geometry));
            }
        }

        void addOptimized(Geometry screenGeometry, double margin) {
            var optimization =
                    ScreenGeometryOptimizer.optimize(
                            screenGeometry,
                            new Envelope(-margin, -margin, WIDTH + margin, HEIGHT + margin),
                            TOLERANCE,
                            ScreenGeometryOptimizationLimits.defaults());
            input = Math.addExact(input, coordinateCount(screenGeometry));
            Geometry rendering = optimization.renderingGeometry().orElse(null);
            if (rendering != null) {
                rendered = Math.addExact(rendered, coordinateCount(rendering));
                if (rendering != screenGeometry) {
                    retainedBytes = Math.addExact(retainedBytes, logicalBytes(rendering));
                }
            }
            if (screenGeometry instanceof LineStringGeometry
                    || screenGeometry instanceof MultiLineStringGeometry) {
                fragments = Math.addExact(fragments, optimization.renderComponentCount());
            }
            for (int component = 0; component < optimization.sourceComponentCount(); component++) {
                if (optimization.renderComponentOffset(component)
                        == optimization.renderComponentOffset(component + 1)) {
                    culled = Math.addExact(culled, 1);
                }
            }
            if (optimization.outcome() == ScreenGeometryOptimizationOutcome.FALLBACK) {
                fallbacks = Math.addExact(fallbacks, 1);
            }
        }

        Map<String, Long> counters(long frames, long features) {
            LinkedHashMap<String, Long> result = new LinkedHashMap<>();
            result.put("frames", frames);
            result.put("features", features);
            result.put("portableInvariants", 6L);
            result.put("inputCoordinates", input);
            result.put("projectedCoordinates", input);
            result.put("renderCoordinates", rendered);
            result.put("lineFragments", fragments);
            result.put("culledPaths", culled);
            result.put("fallbackPlans", fallbacks);
            result.put("retainedRenderGeometryBytes", retainedBytes);
            return java.util.Collections.unmodifiableMap(result);
        }
    }
}
