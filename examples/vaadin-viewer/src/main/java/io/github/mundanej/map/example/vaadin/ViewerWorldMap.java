package io.github.mundanej.map.example.vaadin;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureIndexLimits;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.vaadin.BrowserFeatureLayerPlacement;
import io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode;
import io.github.mundanej.map.vaadin.FeatureSourceBinding;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Opens the viewer's small, provenance-verified, offline Natural Earth land background. */
final class ViewerWorldMap {
    private static final double MINIMUM_LATITUDE = -WebMercatorProjection.MAX_LATITUDE;
    private static final double MAXIMUM_LATITUDE = WebMercatorProjection.MAX_LATITUDE;
    private static final double MINIMUM_LONGITUDE = -180;
    private static final double MAXIMUM_LONGITUDE = 180;
    private static final WebMercatorProjection PROJECTION = new WebMercatorProjection();
    private static final String RESOURCE_ROOT =
            "/io/github/mundanej/map/example/vaadin/naturalearth/";
    private static final String SOURCE_ID = "viewer-natural-earth";
    private static final String LAYER_ID = "world-land";
    private static final Rgba LAND = Rgba.rgb(222, 226, 214);
    private static final Rgba COAST = Rgba.rgb(111, 126, 119);
    private static final FeatureQueryLimits QUERY_LIMITS =
            new FeatureQueryLimits(10_000, 10_000, 1_000_000, 10_000, 1_000_000, 32_000_000, 64);
    private static final List<ManifestEntry> MANIFEST =
            List.of(
                    new ManifestEntry(
                            "ne_110m_land.shp",
                            89_504,
                            "8689e6932b8e370e2ca4587cf3ba21e460b1235db37b6ed3c172c35b4a6088de"),
                    new ManifestEntry(
                            "ne_110m_land.shx",
                            1_116,
                            "2719254764a70262a34333581d582d503b8af5d6626e6da4eb2b5f86e7316faa"),
                    new ManifestEntry(
                            "ne_110m_land.dbf",
                            3_431,
                            "db7cf6d2de2811df09bd7fcc6f243ab78a715b83571a0cb7b36b4e2af3297caa"),
                    new ManifestEntry(
                            "ne_110m_land.prj",
                            147,
                            "3259f0e55290a82b1350646f604e8a7ee1e2136c0320a40fad838ab40819fff8"),
                    new ManifestEntry(
                            "ne_110m_land.cpg",
                            5,
                            "3ad3031f5503a4404af825262ee8232cc04d4ea6683d42c5dd0a2f2a27ac9824"));

    private ViewerWorldMap() {}

    static List<ManifestEntry> manifest() {
        return MANIFEST;
    }

    static Envelope displayExtent() {
        double limit = WebMercatorProjection.WORLD_LIMIT;
        return new Envelope(-limit, -limit, limit, limit);
    }

    static FeatureSourceBinding openBinding() {
        return openBinding(openSource());
    }

    static FeatureSourceBinding openBinding(FeatureSource source) {
        Objects.requireNonNull(source, "source");
        try {
            SolidLineSymbol coast =
                    SolidLineSymbol.of(
                            new SymbolStroke(
                                    COAST, new SymbolLength(0.75, SymbolUnit.SCREEN_PIXEL)),
                            1);
            FeaturePortrayal portrayal =
                    FeaturePortrayal.fixed(
                            BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, LAND, 3, 1),
                            coast,
                            SolidFillSymbol.of(LAND, Optional.of(coast), 1));
            FeatureSourceBinding binding =
                    FeatureSourceBinding.owned(
                            LAYER_ID,
                            "Natural Earth land",
                            source,
                            portrayal,
                            Optional.of(QUERY_LIMITS));
            binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
            binding.setLayerPlacement(BrowserFeatureLayerPlacement.BASEMAP);
            return binding;
        } catch (RuntimeException | Error failure) {
            closeSuppressing(source, failure);
            throw failure;
        }
    }

    static FeatureSource openSource() {
        return WorldDataHolder.DATA.openSource();
    }

    static FeatureSource openSource(ResourceLoader resources) {
        return loadData(resources).openSource();
    }

    private static WorldData loadData(ResourceLoader resources) {
        Objects.requireNonNull(resources, "resources");
        Path directory = createTemporaryDirectory();
        FeatureSource opened = null;
        try {
            for (ManifestEntry entry : MANIFEST) {
                copyVerified(resources, directory, entry);
            }
            opened =
                    Shapefiles.open(
                            new SourceIdentity(SOURCE_ID, "Natural Earth 1:110m land"),
                            directory.resolve("ne_110m_land.shp"),
                            ShapefileOpenOptions.defaults()
                                    .withFeatureSourceLimits(FeatureSourceLimits.LEVEL_1)
                                    .withCrsOverride(CrsDefinitions.EPSG_4326));
            requireWgs84(opened.metadata());
            FeatureSourceMetadata original = opened.metadata();
            List<FeatureRecord> records = materialize(opened);
            FeatureSourceLimits limits = opened.limits();
            opened.close();
            opened = null;
            deleteTree(directory);
            return new WorldData(original.identity(), records, original.schema(), limits);
        } catch (RuntimeException | Error failure) {
            closeSuppressing(opened, failure);
            cleanupSuppressing(directory, failure);
            throw failure;
        }
    }

    private static List<FeatureRecord> materialize(FeatureSource source) {
        List<FeatureRecord> records = new ArrayList<>();
        try (FeatureCursor cursor =
                source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                FeatureRecord candidate = cursor.current();
                clipGeometry(candidate.geometry())
                        .ifPresent(
                                geometry ->
                                        records.add(
                                                new FeatureRecord(
                                                        candidate.id(),
                                                        candidate.name(),
                                                        projectGeometry(geometry),
                                                        candidate.attributes())));
            }
        }
        return List.copyOf(records);
    }

    private static Geometry projectGeometry(Geometry geometry) {
        if (geometry instanceof PolygonGeometry polygon) {
            return projectPolygon(polygon);
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        return MultiPolygonGeometry.of(
                project(polygons.coordinates()),
                polygons.ringOffsets(),
                polygons.polygonRingOffsets());
    }

    private static PolygonGeometry projectPolygon(PolygonGeometry polygon) {
        List<CoordinateSequence> holes = new ArrayList<>(polygon.holes().size());
        for (CoordinateSequence hole : polygon.holes()) {
            holes.add(project(hole));
        }
        return new PolygonGeometry(project(polygon.exterior()), holes);
    }

    private static CoordinateSequence project(CoordinateSequence coordinates) {
        double[] values = new double[coordinates.size() * 2];
        for (int index = 0; index < coordinates.size(); index++) {
            Coordinate projected =
                    PROJECTION.project(new Coordinate(coordinates.x(index), coordinates.y(index)));
            values[index * 2] = projected.x();
            values[index * 2 + 1] = projected.y();
        }
        return CoordinateSequence.of(values);
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

    private static InputStream openResource(String name) throws IOException {
        InputStream stream = ViewerWorldMap.class.getResourceAsStream(RESOURCE_ROOT + name);
        if (stream == null) {
            throw new IOException("resource absent");
        }
        return stream;
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("mundane-map-vaadin-world-");
        } catch (IOException failure) {
            throw failure("WORLD_MAP_TEMP_CREATE_FAILED", "Unable to stage world map", failure);
        }
    }

    private static void copyVerified(
            ResourceLoader resources, Path directory, ManifestEntry entry) {
        MessageDigest digest = sha256();
        long total = 0;
        Path target = directory.resolve(entry.name());
        try (InputStream input = resources.open(entry.name());
                OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total = Math.addExact(total, count);
                if (total > entry.size()) {
                    throw failure(
                            "WORLD_MAP_RESOURCE_SIZE_MISMATCH",
                            "Bundled world-map resource has an unexpected size",
                            null);
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        } catch (WorldMapResourceException failure) {
            throw failure;
        } catch (IOException | ArithmeticException failure) {
            throw failure(
                    "WORLD_MAP_RESOURCE_READ_FAILED",
                    "Bundled world-map resource could not be read",
                    failure);
        }
        if (total != entry.size()
                || !HexFormat.of().formatHex(digest.digest()).equals(entry.sha256())) {
            throw failure(
                    total != entry.size()
                            ? "WORLD_MAP_RESOURCE_SIZE_MISMATCH"
                            : "WORLD_MAP_RESOURCE_HASH_MISMATCH",
                    "Bundled world-map resource failed integrity verification",
                    null);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by Java 21", failure);
        }
    }

    private static void requireWgs84(FeatureSourceMetadata metadata) {
        String identifier = metadata.crs().flatMap(value -> value.canonicalIdentifier()).orElse("");
        if (!identifier.equals(CrsDefinitions.EPSG_4326.canonicalIdentifier())) {
            throw failure(
                    "WORLD_MAP_CRS_UNRECOGNIZED",
                    "Bundled world-map CRS is not recognized as EPSG:4326",
                    null);
        }
    }

    private static WorldMapResourceException failure(String code, String message, Throwable cause) {
        return new WorldMapResourceException(code, message, cause);
    }

    private static void deleteTree(Path directory) {
        IOException primary = null;
        for (ManifestEntry entry : MANIFEST.reversed()) {
            try {
                Files.deleteIfExists(directory.resolve(entry.name()));
            } catch (IOException failure) {
                primary = suppress(primary, failure);
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            primary = suppress(primary, failure);
        }
        if (primary != null) {
            throw failure(
                    "WORLD_MAP_CLEANUP_FAILED",
                    "Unable to remove staged world-map resources",
                    primary);
        }
    }

    private static void cleanupSuppressing(Path directory, Throwable primary) {
        try {
            deleteTree(directory);
        } catch (RuntimeException cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception | Error cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private static IOException suppress(IOException primary, IOException failure) {
        if (primary == null) {
            return failure;
        }
        primary.addSuppressed(failure);
        return primary;
    }

    record ManifestEntry(String name, long size, String sha256) {
        ManifestEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    @SuppressWarnings("serial")
    static final class WorldMapResourceException extends IllegalStateException {
        private final String code;

        WorldMapResourceException(String code, String message, Throwable cause) {
            super(code + ": " + message, cause);
            this.code = Objects.requireNonNull(code, "code");
        }

        String code() {
            return code;
        }
    }

    @FunctionalInterface
    interface ResourceLoader {
        InputStream open(String name) throws IOException;
    }

    private static final class WorldDataHolder {
        private static final WorldData DATA = loadData(ViewerWorldMap::openResource);

        private WorldDataHolder() {}
    }

    private record WorldData(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            FeatureSourceLimits limits) {
        private WorldData {
            Objects.requireNonNull(identity, "identity");
            records = List.copyOf(Objects.requireNonNull(records, "records"));
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(limits, "limits");
        }

        private FeatureSource openSource() {
            return InMemoryFeatureSource.openIndexed(
                    identity,
                    records,
                    schema,
                    Optional.of(
                            CrsMetadata.recognized(
                                    CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty())),
                    limits,
                    FeatureIndexLimits.LEVEL_1);
        }
    }

    private record Vertex(double x, double y) {}

    private enum Axis {
        X,
        Y
    }
}
