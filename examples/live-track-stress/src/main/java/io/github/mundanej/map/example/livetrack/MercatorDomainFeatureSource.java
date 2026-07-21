package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Example-local decorator that clips Natural Earth polygons to the Web Mercator latitude domain.
 */
final class MercatorDomainFeatureSource implements FeatureSource {
    private static final double MINIMUM_LATITUDE = -WebMercatorProjection.MAX_LATITUDE;
    private static final double MAXIMUM_LATITUDE = WebMercatorProjection.MAX_LATITUDE;
    private static final double MINIMUM_LONGITUDE = -180.0;
    private static final double MAXIMUM_LONGITUDE = 180.0;

    private final FeatureSource delegate;
    private final Path stagedDirectory;
    private final Consumer<Path> cleanup;
    private final FeatureSourceMetadata metadata;
    private boolean closed;

    MercatorDomainFeatureSource(
            FeatureSource delegate, Path stagedDirectory, Consumer<Path> cleanup) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.stagedDirectory = Objects.requireNonNull(stagedDirectory, "stagedDirectory");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        FeatureSourceMetadata original = delegate.metadata();
        this.metadata =
                new FeatureSourceMetadata(
                        original.identity(),
                        original.extent().flatMap(MercatorDomainFeatureSource::clipExtent),
                        original.featureCount(),
                        original.schema(),
                        original.crs());
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return delegate.limits();
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return delegate.openingDiagnostics();
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        requireOpen();
        return new ClippingCursor(delegate.openCursor(query, cancellation));
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable primary = null;
        try {
            delegate.close();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            cleanup.accept(stagedDirectory);
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                primary = failure;
            } else {
                primary.addSuppressed(failure);
            }
        }
        if (primary instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (primary instanceof Error error) {
            throw error;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Natural Earth source is closed");
        }
    }

    private static Optional<Envelope> clipExtent(Envelope extent) {
        double minimumX = Math.max(extent.minX(), MINIMUM_LONGITUDE);
        double maximumX = Math.min(extent.maxX(), MAXIMUM_LONGITUDE);
        double minimum = Math.max(extent.minY(), MINIMUM_LATITUDE);
        double maximum = Math.min(extent.maxY(), MAXIMUM_LATITUDE);
        if (minimumX > maximumX || minimum > maximum) {
            return Optional.empty();
        }
        return Optional.of(new Envelope(minimumX, minimum, maximumX, maximum));
    }

    private static Optional<Geometry> clipGeometry(Geometry geometry) {
        if (geometry instanceof PolygonGeometry polygon) {
            return clipPolygon(polygon).map(value -> (Geometry) value);
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            List<PolygonGeometry> retained = new ArrayList<>();
            for (int polygonIndex = 0; polygonIndex < polygons.polygonCount(); polygonIndex++) {
                int firstRing = polygons.polygonRingOffset(polygonIndex);
                int afterLastRing = polygons.polygonRingOffset(polygonIndex + 1);
                PolygonGeometry polygon = polygon(polygons, firstRing, afterLastRing);
                clipPolygon(polygon).ifPresent(retained::add);
            }
            if (retained.isEmpty()) {
                return Optional.empty();
            }
            if (retained.size() == 1) {
                return Optional.of(retained.getFirst());
            }
            return Optional.of(MultiPolygonGeometry.ofPolygons(retained));
        }
        throw new IllegalStateException(
                "Natural Earth land contained unsupported geometry "
                        + geometry.getClass().getSimpleName());
    }

    private static PolygonGeometry polygon(
            MultiPolygonGeometry polygons, int firstRing, int afterLastRing) {
        CoordinateSequence exterior = ring(polygons, firstRing);
        List<CoordinateSequence> holes = new ArrayList<>();
        for (int ringIndex = firstRing + 1; ringIndex < afterLastRing; ringIndex++) {
            holes.add(ring(polygons, ringIndex));
        }
        return new PolygonGeometry(exterior, holes);
    }

    private static CoordinateSequence ring(MultiPolygonGeometry polygons, int ringIndex) {
        int start = polygons.ringOffset(ringIndex);
        int end = polygons.ringOffset(ringIndex + 1);
        double[] values = new double[(end - start) * 2];
        CoordinateSequence coordinates = polygons.coordinates();
        for (int index = start; index < end; index++) {
            int target = (index - start) * 2;
            values[target] = coordinates.x(index);
            values[target + 1] = coordinates.y(index);
        }
        return CoordinateSequence.of(values);
    }

    private static Optional<PolygonGeometry> clipPolygon(PolygonGeometry polygon) {
        Optional<CoordinateSequence> exterior = clipRing(polygon.exterior());
        if (exterior.isEmpty()) {
            return Optional.empty();
        }
        List<CoordinateSequence> holes = new ArrayList<>();
        for (CoordinateSequence hole : polygon.holes()) {
            clipRing(hole).ifPresent(holes::add);
        }
        return Optional.of(new PolygonGeometry(exterior.orElseThrow(), holes));
    }

    private static Optional<CoordinateSequence> clipRing(CoordinateSequence ring) {
        List<Vertex> vertices = new ArrayList<>(ring.size() - 1);
        for (int index = 0; index < ring.size() - 1; index++) {
            appendDistinct(vertices, new Vertex(ring.x(index), ring.y(index)));
        }
        vertices = clipBoundary(vertices, Axis.X, MINIMUM_LONGITUDE, true);
        vertices = clipBoundary(vertices, Axis.X, MAXIMUM_LONGITUDE, false);
        vertices = clipBoundary(vertices, Axis.Y, MINIMUM_LATITUDE, true);
        vertices = clipBoundary(vertices, Axis.Y, MAXIMUM_LATITUDE, false);
        if (vertices.size() < 3) {
            return Optional.empty();
        }
        double[] values = new double[(vertices.size() + 1) * 2];
        for (int index = 0; index < vertices.size(); index++) {
            Vertex vertex = vertices.get(index);
            values[index * 2] = vertex.x();
            values[index * 2 + 1] = vertex.y();
        }
        values[values.length - 2] = vertices.getFirst().x();
        values[values.length - 1] = vertices.getFirst().y();
        return Optional.of(CoordinateSequence.of(values));
    }

    private static List<Vertex> clipBoundary(
            List<Vertex> input, Axis axis, double boundary, boolean retainGreater) {
        if (input.isEmpty()) {
            return List.of();
        }
        List<Vertex> output = new ArrayList<>();
        Vertex previous = input.getLast();
        boolean previousInside = inside(previous, axis, boundary, retainGreater);
        for (Vertex current : input) {
            boolean currentInside = inside(current, axis, boundary, retainGreater);
            if (currentInside != previousInside) {
                appendDistinct(output, intersection(previous, current, axis, boundary));
            }
            if (currentInside) {
                appendDistinct(output, current);
            }
            previous = current;
            previousInside = currentInside;
        }
        if (output.size() > 1 && output.getFirst().equals(output.getLast())) {
            output.removeLast();
        }
        return output;
    }

    private static boolean inside(
            Vertex vertex, Axis axis, double boundary, boolean retainGreater) {
        double value = axis == Axis.X ? vertex.x() : vertex.y();
        return retainGreater ? value >= boundary : value <= boundary;
    }

    private static Vertex intersection(Vertex first, Vertex second, Axis axis, double boundary) {
        if (axis == Axis.X) {
            double fraction = (boundary - first.x()) / (second.x() - first.x());
            return new Vertex(boundary, first.y() + fraction * (second.y() - first.y()));
        }
        double fraction = (boundary - first.y()) / (second.y() - first.y());
        return new Vertex(first.x() + fraction * (second.x() - first.x()), boundary);
    }

    private static void appendDistinct(List<Vertex> vertices, Vertex candidate) {
        if (vertices.isEmpty() || !vertices.getLast().equals(candidate)) {
            vertices.add(candidate);
        }
    }

    private record Vertex(double x, double y) {}

    private enum Axis {
        X,
        Y
    }

    private static final class ClippingCursor implements FeatureCursor {
        private final FeatureCursor delegate;
        private FeatureRecord current;

        private ClippingCursor(FeatureCursor delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public boolean advance() {
            current = null;
            while (delegate.advance()) {
                FeatureRecord candidate = delegate.current();
                Optional<Geometry> geometry = clipGeometry(candidate.geometry());
                if (geometry.isPresent()) {
                    current =
                            new FeatureRecord(
                                    candidate.id(),
                                    candidate.name(),
                                    geometry.orElseThrow(),
                                    candidate.attributes());
                    return true;
                }
            }
            return false;
        }

        @Override
        public FeatureRecord current() {
            if (current == null) {
                throw new IllegalStateException("No current Natural Earth feature");
            }
            return current;
        }

        @Override
        public DiagnosticReport diagnostics() {
            return delegate.diagnostics();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
            current = null;
        }
    }
}
