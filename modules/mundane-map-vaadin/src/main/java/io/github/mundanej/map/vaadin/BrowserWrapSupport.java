package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import io.github.mundanej.map.core.MapViewport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared checked browser repetition helpers over the core horizontal-wrap policy. */
final class BrowserWrapSupport {
    private BrowserWrapSupport() {}

    static HorizontalWrapPlan validate(
            HorizontalWrap profile, CrsDefinition displayCrs, MapViewport viewport) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(displayCrs, "displayCrs");
        Objects.requireNonNull(viewport, "viewport");
        Envelope domain = displayCrs.coordinateDomain();
        if (Double.compare(profile.canonicalMinimumX(), domain.minX()) != 0
                || Double.compare(profile.canonicalMaximumX(), domain.maxX()) != 0
                || (profile.equals(HorizontalWrap.webMercator())
                        && !displayCrs.equals(CrsDefinitions.EPSG_3857))) {
            throw new IllegalArgumentException(
                    "horizontal wrap must exactly match the display CRS domain");
        }
        Envelope visible = viewport.visibleWorldEnvelope();
        return profile.plan(visible.minX(), visible.maxX(), viewport.worldUnitsPerPixel());
    }

    static void validateRepeatingRaster(
            RasterSourceMetadata metadata, CrsDefinition displayCrs, HorizontalWrap profile) {
        CrsDefinition sourceCrs = metadata.crs().flatMap(value -> value.definition()).orElse(null);
        if (!displayCrs.equals(sourceCrs)) {
            throw incompatibleRaster("crs");
        }
        RasterGridPlacement placement = metadata.gridPlacement().orElse(null);
        if (placement == null) {
            throw incompatibleRaster("extent");
        }
        if (placement.kind() == RasterGridPlacement.Kind.AFFINE) {
            RasterAffineTransform transform = placement.affineTransform().orElseThrow();
            if (transform.d() != 0.0) {
                throw incompatibleRaster("rotation");
            }
            if (transform.b() != 0.0) {
                throw incompatibleRaster("shear");
            }
        }
        Envelope bounds = metadata.mapBounds().orElse(null);
        if (bounds == null
                || !edgeMatches(bounds.minX(), profile.canonicalMinimumX(), profile.period())
                || !edgeMatches(bounds.maxX(), profile.canonicalMaximumX(), profile.period())) {
            throw incompatibleRaster("extent");
        }
    }

    static void validateRepeatingEnvelope(
            Envelope bounds,
            Optional<CrsDefinition> sourceCrs,
            CrsDefinition displayCrs,
            HorizontalWrap wrap) {
        if (sourceCrs.isEmpty() || !sourceCrs.orElseThrow().equals(displayCrs)) {
            throw incompatibleRaster("crs");
        }
        if (!edgeMatches(bounds.minX(), wrap.canonicalMinimumX(), wrap.period())
                || !edgeMatches(bounds.maxX(), wrap.canonicalMaximumX(), wrap.period())) {
            throw incompatibleRaster("extent");
        }
    }

    static Layer wrappedLayer(
            String id,
            String name,
            List<Feature> features,
            List<String> logicalIds,
            List<Long> copies) {
        return new WrappedLayer(id, name, features, logicalIds, copies);
    }

    static long coordinateCount(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 1L;
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
        return ((MultiPolygonGeometry) geometry).coordinates().size();
    }

    static Geometry translate(Geometry geometry, double offset) {
        if (!Double.isFinite(offset)) {
            throw new IllegalArgumentException("wrapped offset must be finite");
        }
        if (geometry instanceof PointGeometry point) {
            return new PointGeometry(
                    new Coordinate(point.coordinate().x() + offset, point.coordinate().y()));
        }
        if (geometry instanceof MultiPointGeometry points) {
            return new MultiPointGeometry(translate(points.coordinates(), offset));
        }
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(translate(line.coordinates(), offset));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    translate(lines.coordinates(), offset), lines.partOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            return new PolygonGeometry(
                    translate(polygon.exterior(), offset),
                    polygon.holes().stream().map(hole -> translate(hole, offset)).toList());
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        return MultiPolygonGeometry.of(
                translate(polygons.coordinates(), offset),
                polygons.ringOffsets(),
                polygons.polygonRingOffsets());
    }

    static double copyOffset(HorizontalWrap profile, long copyIndex) {
        return profile.translate(profile.canonicalMinimumX(), copyIndex)
                - profile.canonicalMinimumX();
    }

    static Envelope translate(Envelope bounds, double offset) {
        return new Envelope(
                bounds.minX() + offset, bounds.minY(), bounds.maxX() + offset, bounds.maxY());
    }

    static Envelope normalizeCanonicalEdges(Envelope bounds, HorizontalWrap profile) {
        double minimum =
                edgeMatches(bounds.minX(), profile.canonicalMinimumX(), profile.period())
                        ? profile.canonicalMinimumX()
                        : bounds.minX();
        double maximum =
                edgeMatches(bounds.maxX(), profile.canonicalMaximumX(), profile.period())
                        ? profile.canonicalMaximumX()
                        : bounds.maxX();
        return new Envelope(minimum, bounds.minY(), maximum, bounds.maxY());
    }

    static String displayId(int featureIndex, long copyIndex, int fragmentIndex) {
        return "__wrap_" + featureIndex + "_" + copyIndex + "_" + fragmentIndex;
    }

    static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private static CoordinateSequence translate(CoordinateSequence source, double offset) {
        double[] target = source.toArray();
        for (int index = 0; index < target.length; index += 2) {
            target[index] += offset;
            if (!Double.isFinite(target[index])) {
                throw new ArithmeticException("wrapped coordinate must remain finite");
            }
        }
        return CoordinateSequence.of(target);
    }

    private static boolean edgeMatches(double actual, double expected, double period) {
        double tolerance = Math.max(8.0 * Math.ulp(expected), period * 1.0e-12);
        return Math.abs(actual - expected) <= tolerance;
    }

    private static MundaneMapException incompatibleRaster(String reason) {
        return new MundaneMapException(
                MundaneMapException.WORLD_WRAP_RASTER_INCOMPATIBLE,
                "Raster is incompatible with horizontal world repetition",
                Map.of("reason", reason));
    }

    private record WrappedLayer(
            String id,
            String name,
            List<Feature> features,
            List<String> logicalIds,
            List<Long> copies)
            implements BrowserLogicalLayer {
        private WrappedLayer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            features = List.copyOf(features);
            logicalIds = List.copyOf(logicalIds);
            copies = List.copyOf(copies);
            if (features.size() != logicalIds.size() || features.size() != copies.size()) {
                throw new IllegalArgumentException("wrapped metadata must match features");
            }
        }

        @Override
        public String logicalFeatureId(int featureIndex) {
            return logicalIds.get(featureIndex);
        }

        @Override
        public long copyIndex(int featureIndex) {
            return copies.get(featureIndex);
        }

        @Override
        public Optional<Envelope> envelope() {
            Envelope aggregate = null;
            for (Feature feature : features) {
                aggregate =
                        aggregate == null
                                ? feature.geometry().envelope()
                                : aggregate.union(feature.geometry().envelope());
            }
            return Optional.ofNullable(aggregate);
        }
    }
}
