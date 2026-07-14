package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/** Test-only deterministic public-path mutation runner. */
final class ShapefileMutationHarness {
    private static final SourceIdentity IDENTITY = new SourceIdentity("mutation", "Mutation");
    private static final Set<String> WARNING_CODES =
            Set.of(
                    "SHAPEFILE_SHX_MISSING",
                    "SHAPEFILE_SHX_IGNORED",
                    "SHAPEFILE_DBF_MISSING",
                    "SHAPEFILE_DBF_FIELD_UNSUPPORTED",
                    "SHAPEFILE_DBF_VALUE_INVALID",
                    "SHAPEFILE_CPG_WITHOUT_DBF",
                    "SHAPEFILE_CPG_INVALID",
                    "SHAPEFILE_ENCODING_CONFLICT",
                    "SHAPEFILE_ENCODING_FALLBACK",
                    "SHAPEFILE_PRJ_BLANK",
                    "SHAPEFILE_PRJ_CRS_UNRECOGNIZED",
                    "SHAPEFILE_PRJ_OVERRIDE_USED");
    private static final Set<String> ERROR_CODES =
            Set.of(
                    "SHAPEFILE_HEADER_INVALID",
                    "SHAPEFILE_FILE_LENGTH_MISMATCH",
                    "SHAPEFILE_SHAPE_TYPE_UNSUPPORTED",
                    "SHAPEFILE_RECORD_NUMBER_INVALID",
                    "SHAPEFILE_RECORD_LENGTH_INVALID",
                    "SHAPEFILE_RECORD_TYPE_MISMATCH",
                    "SHAPEFILE_COORDINATE_NON_FINITE",
                    "SHAPEFILE_BOUNDS_MISMATCH",
                    "SHAPEFILE_PART_TABLE_INVALID",
                    "SHAPEFILE_RING_INVALID",
                    "SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS",
                    "SHAPEFILE_DBF_HEADER_INVALID",
                    "SHAPEFILE_DBF_FIELD_INVALID",
                    "SHAPEFILE_DBF_RECORD_MARKER_INVALID",
                    "SHAPEFILE_DBF_RECORD_COUNT_MISMATCH",
                    "SHAPEFILE_PRJ_INVALID",
                    "SHAPEFILE_CRS_CONFLICT",
                    "SOURCE_LIMIT_EXCEEDED");
    private static final Mutation[] MUTATIONS = {
        Mutation.FLIP_BIT,
        Mutation.FLIP_MULTIPLE,
        Mutation.OVERWRITE_BYTE,
        Mutation.OVERWRITE_INT_BIG_ENDIAN,
        Mutation.OVERWRITE_INT_LITTLE_ENDIAN,
        Mutation.OVERWRITE_DOUBLE,
        Mutation.REVERSE_INT,
        Mutation.TRUNCATE,
        Mutation.APPEND,
        Mutation.REPLACE_COUNT,
        Mutation.DELETE_OPTIONAL,
        Mutation.CASE_VARIANT,
        Mutation.CROSS_WIRE
    };

    enum Family {
        SHP(0x5348502D47353038L, 52),
        SHX(0x5348582D47353038L, 51),
        DBF(0x4442462D47353038L, 51),
        CPG(0x4350472D47353038L, 51),
        PRJ(0x50524A2D47353038L, 51);

        private final long seed;
        private final int cases;

        Family(long seed, int cases) {
            this.seed = seed;
            this.cases = cases;
        }

        long seed() {
            return seed;
        }

        int cases() {
            return cases;
        }

        String component() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum Mutation {
        NAMED_WARNING,
        FLIP_BIT,
        FLIP_MULTIPLE,
        OVERWRITE_BYTE,
        OVERWRITE_INT_BIG_ENDIAN,
        OVERWRITE_INT_LITTLE_ENDIAN,
        OVERWRITE_DOUBLE,
        REVERSE_INT,
        TRUNCATE,
        APPEND,
        REPLACE_COUNT,
        DELETE_OPTIONAL,
        CASE_VARIANT,
        CROSS_WIRE
    }

    enum Phase {
        OPENING_FAILURE,
        CURSOR_FAILURE,
        SUCCESS
    }

    record ReplayDescriptor(
            String family,
            long seed,
            int caseIndex,
            String baseline,
            String component,
            Mutation mutation,
            int firstOffset,
            int secondOffset,
            long operand) {}

    record DiagnosticKey(
            String code,
            DiagnosticSeverity severity,
            Optional<DiagnosticLocation> location,
            Map<String, String> context) {}

    record NormalizedOutcome(
            Phase phase,
            FeatureSourceMetadata metadata,
            List<FeatureRecord> features,
            List<DiagnosticKey> opening,
            long openingOmitted,
            List<DiagnosticKey> cursor,
            long cursorOmitted) {}

    private record Dataset(
            Map<String, byte[]> components,
            ShapefileOpenOptions options,
            Optional<String> uppercaseComponent) {}

    private ShapefileMutationHarness() {}

    static ReplayDescriptor replay(String familyToken, int caseIndex) {
        Family family;
        try {
            family = Family.valueOf(familyToken);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown mutation family: " + familyToken, exception);
        }
        if (caseIndex < 0 || caseIndex >= family.cases()) {
            throw new IllegalArgumentException("Mutation case index is out of range");
        }
        Random random = new Random(family.seed());
        ReplayDescriptor descriptor = null;
        for (int index = 0; index <= caseIndex; index++) {
            descriptor = nextDescriptor(family, index, random);
        }
        return descriptor;
    }

    static NormalizedOutcome run(Path root, ReplayDescriptor descriptor, int repetition)
            throws IOException {
        Family family = Family.valueOf(descriptor.family());
        ReplayDescriptor reconstructed = replay(family.name(), descriptor.caseIndex());
        if (!descriptor.equals(reconstructed)) {
            throw new AssertionError("Replay descriptor changed: " + descriptor);
        }
        Dataset dataset = dataset(family, descriptor);
        Path directory =
                root.resolve(
                        family.name().toLowerCase(java.util.Locale.ROOT)
                                + '-'
                                + descriptor.caseIndex()
                                + '-'
                                + repetition);
        Files.createDirectory(directory);
        Path shp = write(directory, dataset);
        try {
            NormalizedOutcome outcome = execute(shp, dataset.options());
            validate(outcome, descriptor);
            return outcome;
        } finally {
            deleteTree(directory);
        }
    }

    static NormalizedOutcome sentinel(Path root, Family family) throws IOException {
        ReplayDescriptor descriptor =
                new ReplayDescriptor(
                        family.name(),
                        family.seed(),
                        0,
                        "sentinel",
                        family.component(),
                        Mutation.NAMED_WARNING,
                        0,
                        0,
                        0);
        Path directory =
                root.resolve("sentinel-" + family.name().toLowerCase(java.util.Locale.ROOT));
        Files.createDirectory(directory);
        Dataset dataset = new Dataset(pointDatasetCopy(), boundedOptions(), Optional.empty());
        Path shp = write(directory, dataset);
        try {
            NormalizedOutcome outcome = execute(shp, dataset.options());
            validate(outcome, descriptor);
            return outcome;
        } finally {
            deleteTree(directory);
        }
    }

    private static ReplayDescriptor nextDescriptor(Family family, int index, Random random) {
        long first = random.nextLong();
        long second = random.nextLong();
        long operand = random.nextLong();
        Mutation mutation = namedWarning(family, index) ? Mutation.NAMED_WARNING : mutation(first);
        if (family == Family.SHP
                && (mutation == Mutation.DELETE_OPTIONAL || mutation == Mutation.CASE_VARIANT)) {
            mutation = Mutation.TRUNCATE;
        }
        String baseline = index < 32 ? "mutation" : "generated-" + (index - 32);
        return new ReplayDescriptor(
                family.name(),
                family.seed(),
                index,
                baseline,
                family.component(),
                mutation,
                Math.floorMod((int) first, 512),
                Math.floorMod((int) second, 512),
                operand);
    }

    private static Mutation mutation(long selector) {
        return MUTATIONS[Math.floorMod((int) selector, MUTATIONS.length)];
    }

    private static boolean namedWarning(Family family, int index) {
        return (family == Family.SHP && index == 0)
                || (family == Family.SHX && index < 2)
                || (family == Family.DBF && index < 3)
                || (family == Family.CPG && index < 4)
                || (family == Family.PRJ && index < 3);
    }

    private static Dataset dataset(Family family, ReplayDescriptor descriptor) {
        Dataset special = namedDataset(family, descriptor.caseIndex());
        if (special != null) {
            return special;
        }
        Map<String, byte[]> components =
                descriptor.caseIndex() < 32
                        ? pointDatasetCopy()
                        : copyComponents(
                                ShapefileAdversarialFixtures.shapeDataset(
                                        descriptor.caseIndex() - 32));
        if (family == Family.DBF || family == Family.CPG) {
            Map<String, byte[]> point = ShapefileAdversarialFixtures.pointDataset();
            components.put("dbf", ShapefileAdversarialFixtures.copy(point.get("dbf")));
            components.put("cpg", ShapefileAdversarialFixtures.copy(point.get("cpg")));
        }
        if (family == Family.PRJ) {
            components.put("prj", PrjFixtures.utf8(PrjFixtures.EPSG_4326));
        }
        return mutate(components, boundedOptions(), descriptor);
    }

    private static Dataset namedDataset(Family family, int index) {
        Map<String, byte[]> components;
        ShapefileOpenOptions options = boundedOptions();
        if (family == Family.SHP && index == 0) {
            components = ShapefileAdversarialFixtures.shapeDataset(1);
            components.remove("shx");
            return new Dataset(components, options, Optional.empty());
        }
        if (family == Family.SHX && index < 2) {
            components = pointDatasetCopy();
            if (index == 0) {
                components.remove("shx");
            } else {
                components.put("shx", new byte[] {0});
            }
            return new Dataset(components, options, Optional.empty());
        }
        if (family == Family.DBF && index < 3) {
            components =
                    index == 1
                            ? copyComponents(ShapefileAdversarialFixtures.unsupportedDbfDataset())
                            : index == 2
                                    ? copyComponents(
                                            ShapefileAdversarialFixtures.invalidDbfValueDataset())
                                    : pointDatasetCopy();
            if (index == 0) {
                components.remove("dbf");
                components.remove("cpg");
            }
            return new Dataset(components, options, Optional.empty());
        }
        if (family == Family.CPG && index < 4) {
            components = pointDatasetCopy();
            if (index == 0) {
                components.remove("dbf");
            } else if (index == 1) {
                components.put("cpg", new byte[] {'?', '?'});
            } else if (index == 2) {
                options = options.withDbfEncodingOverride(DbfEncoding.WINDOWS_1252);
            } else {
                components.remove("cpg");
            }
            return new Dataset(components, options, Optional.empty());
        }
        if (family == Family.PRJ && index < 3) {
            components = pointDatasetCopy();
            components.put(
                    "prj",
                    index == 0
                            ? new byte[] {' ', '\n'}
                            : PrjFixtures.utf8("LOCAL_CS[\"bounded-unknown\"]"));
            if (index == 2) {
                options = options.withCrsOverride(CrsDefinitions.EPSG_4326);
            }
            return new Dataset(components, options, Optional.empty());
        }
        return null;
    }

    private static Dataset mutate(
            Map<String, byte[]> components,
            ShapefileOpenOptions options,
            ReplayDescriptor descriptor) {
        String component = descriptor.component();
        byte[] original = components.get(component);
        if (original == null) {
            original = new byte[] {0};
        }
        byte[] changed = ShapefileAdversarialFixtures.copy(original);
        int first = changed.length == 0 ? 0 : descriptor.firstOffset() % changed.length;
        int second = changed.length == 0 ? 0 : descriptor.secondOffset() % changed.length;
        Optional<String> uppercase = Optional.empty();
        switch (descriptor.mutation()) {
            case NAMED_WARNING -> {}
            case FLIP_BIT -> {
                if (changed.length > 0) {
                    changed[first] ^= (byte) (1 << Math.floorMod((int) descriptor.operand(), 8));
                }
            }
            case FLIP_MULTIPLE -> {
                if (changed.length > 0) {
                    changed[first] ^= 0x55;
                    changed[second] ^= (byte) 0xaa;
                }
            }
            case OVERWRITE_BYTE -> {
                if (changed.length > 0) {
                    changed[first] = (byte) descriptor.operand();
                }
            }
            case OVERWRITE_INT_BIG_ENDIAN ->
                    ShapefileAdversarialFixtures.putInt(
                            changed,
                            first,
                            boundaryInt(descriptor.operand()),
                            ByteOrder.BIG_ENDIAN);
            case OVERWRITE_INT_LITTLE_ENDIAN ->
                    ShapefileAdversarialFixtures.putInt(
                            changed,
                            first,
                            boundaryInt(descriptor.operand()),
                            ByteOrder.LITTLE_ENDIAN);
            case OVERWRITE_DOUBLE ->
                    ShapefileAdversarialFixtures.putDouble(
                            changed, first, boundaryDouble(descriptor.operand()));
            case REVERSE_INT -> ShapefileAdversarialFixtures.reverseInt(changed, first);
            case TRUNCATE ->
                    changed =
                            ShapefileAdversarialFixtures.truncate(
                                    changed,
                                    Math.floorMod(descriptor.secondOffset(), changed.length + 1));
            case APPEND ->
                    changed =
                            ShapefileAdversarialFixtures.append(
                                    changed,
                                    1 + Math.floorMod(descriptor.secondOffset(), 16),
                                    (byte) descriptor.operand());
            case REPLACE_COUNT ->
                    ShapefileAdversarialFixtures.putInt(
                            changed,
                            first,
                            boundaryInt(descriptor.operand()),
                            component.equals("shp") || component.equals("shx")
                                    ? ByteOrder.BIG_ENDIAN
                                    : ByteOrder.LITTLE_ENDIAN);
            case DELETE_OPTIONAL -> components.remove(component);
            case CASE_VARIANT -> uppercase = Optional.of(component);
            case CROSS_WIRE -> changed = crossWire(component);
        }
        if (descriptor.mutation() != Mutation.DELETE_OPTIONAL) {
            components.put(component, changed);
        }
        return new Dataset(components, options, uppercase);
    }

    private static byte[] crossWire(String component) {
        return switch (component) {
            case "shp" -> ShapefileAdversarialFixtures.shapeDataset(3).get("shp");
            case "shx" -> ShapefileAdversarialFixtures.shapeDataset(3).get("shx");
            case "dbf" -> ShapefileAdversarialFixtures.twoRowDbfDataset().get("dbf");
            case "cpg" -> new byte[] {'1', '2', '5', '2'};
            case "prj" -> PrjFixtures.utf8(PrjFixtures.EPSG_3857);
            default -> throw new IllegalArgumentException("Unknown component: " + component);
        };
    }

    private static int boundaryInt(long selector) {
        return switch (Math.floorMod((int) selector, 4)) {
            case 0 -> 0;
            case 1 -> -1;
            case 2 -> Integer.MAX_VALUE;
            default -> Integer.MIN_VALUE;
        };
    }

    private static double boundaryDouble(long selector) {
        return switch (Math.floorMod((int) selector, 4)) {
            case 0 -> Double.NaN;
            case 1 -> Double.POSITIVE_INFINITY;
            case 2 -> Double.NEGATIVE_INFINITY;
            default -> -0.0;
        };
    }

    private static ShapefileOpenOptions boundedOptions() {
        ShapefileLimits limits =
                ShapefileLimits.defaults()
                        .withMaximumComponentBytes(65_536)
                        .withMaximumPhysicalRecords(64)
                        .withMaximumRecordBytes(65_536)
                        .withMaximumParts(64)
                        .withMaximumPoints(4_096)
                        .withMaximumTopologyComparisons(20_000)
                        .withMaximumDbfFields(64)
                        .withMaximumDbfFieldWidth(254)
                        .withMaximumCpgBytes(65_536)
                        .withMaximumPrjBytes(65_536)
                        .withMaximumDecodedTextCharacters(4_096)
                        .withMaximumParserAllocationBytes(1_048_576);
        return ShapefileOpenOptions.defaults().withShapefileLimits(limits);
    }

    private static Map<String, byte[]> pointDatasetCopy() {
        return copyComponents(ShapefileAdversarialFixtures.pointDataset());
    }

    private static Map<String, byte[]> copyComponents(Map<String, byte[]> source) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        source.forEach(
                (component, bytes) ->
                        result.put(component, ShapefileAdversarialFixtures.copy(bytes)));
        return result;
    }

    private static Path write(Path directory, Dataset dataset) throws IOException {
        String stem = "case";
        for (Map.Entry<String, byte[]> entry : dataset.components().entrySet()) {
            String extension =
                    dataset.uppercaseComponent().filter(entry.getKey()::equals).isPresent()
                            ? entry.getKey().toUpperCase(java.util.Locale.ROOT)
                            : entry.getKey();
            Files.write(directory.resolve(stem + '.' + extension), entry.getValue());
        }
        return directory.resolve(stem + ".shp");
    }

    private static NormalizedOutcome execute(Path shp, ShapefileOpenOptions options) {
        try (FeatureSource source =
                Shapefiles.open(IDENTITY, shp, options, CancellationToken.none())) {
            FeatureSourceMetadata metadata = source.metadata();
            DiagnosticReport opening = source.openingDiagnostics();
            List<FeatureRecord> features = new ArrayList<>();
            try (FeatureCursor cursor =
                    source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
                while (cursor.advance()) {
                    features.add(cursor.current());
                }
                DiagnosticReport report = cursor.diagnostics();
                return new NormalizedOutcome(
                        Phase.SUCCESS,
                        metadata,
                        List.copyOf(features),
                        diagnostics(opening),
                        opening.omittedWarningCount(),
                        diagnostics(report),
                        report.omittedWarningCount());
            } catch (SourceException failure) {
                return new NormalizedOutcome(
                        Phase.CURSOR_FAILURE,
                        metadata,
                        List.copyOf(features),
                        diagnostics(opening),
                        opening.omittedWarningCount(),
                        diagnostics(failure.report()),
                        failure.report().omittedWarningCount());
            }
        } catch (SourceException failure) {
            return new NormalizedOutcome(
                    Phase.OPENING_FAILURE,
                    null,
                    List.of(),
                    diagnostics(failure.report()),
                    failure.report().omittedWarningCount(),
                    List.of(),
                    0);
        }
    }

    private static List<DiagnosticKey> diagnostics(DiagnosticReport report) {
        return report.entries().stream()
                .map(
                        diagnostic ->
                                new DiagnosticKey(
                                        diagnostic.code(),
                                        diagnostic.severity(),
                                        diagnostic.location(),
                                        diagnostic.context()))
                .toList();
    }

    private static void validate(NormalizedOutcome outcome, ReplayDescriptor descriptor) {
        validateDiagnostics(
                outcome.opening(), outcome.phase() == Phase.OPENING_FAILURE, descriptor);
        validateDiagnostics(outcome.cursor(), outcome.phase() == Phase.CURSOR_FAILURE, descriptor);
        if (outcome.phase() == Phase.SUCCESS) {
            if (outcome.metadata() == null) {
                throw new AssertionError("Success lacks metadata: " + descriptor);
            }
            if (outcome.openingOmitted() < 0 || outcome.cursorOmitted() < 0) {
                throw new AssertionError("Negative omitted-warning count: " + descriptor);
            }
            AttributeSchema schema =
                    outcome.metadata().schema().orElseGet(() -> new AttributeSchema(List.of()));
            List<String> schemaNames = schema.fields().stream().map(field -> field.name()).toList();
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            long previousOrdinal = 0;
            long coordinateCount = 0;
            long attributeCount = 0;
            long textCharacters = 0;
            for (FeatureRecord feature : outcome.features()) {
                if (!ids.add(feature.id())) {
                    throw new AssertionError("Duplicate feature ID: " + descriptor);
                }
                long ordinal = recordOrdinal(feature.id(), descriptor);
                if (ordinal <= previousOrdinal) {
                    throw new AssertionError("Feature IDs are not in source order: " + descriptor);
                }
                previousOrdinal = ordinal;
                int coordinates = validateFinite(feature.geometry(), descriptor);
                coordinateCount = Math.addExact(coordinateCount, coordinates);
                attributeCount = Math.addExact(attributeCount, feature.attributes().size());
                if (!List.copyOf(feature.attributes().keySet()).equals(schemaNames)) {
                    throw new AssertionError(
                            "Feature attributes disagree with schema: " + descriptor);
                }
                for (var field : schema.fields()) {
                    Object value = feature.attributes().get(field.name());
                    if (!field.accepts(value)) {
                        throw new AssertionError(
                                "Feature value disagrees with schema: " + descriptor);
                    }
                    if (value instanceof Double number && !Double.isFinite(number)) {
                        throw new AssertionError("Non-finite attribute: " + descriptor);
                    }
                    if (value instanceof String text) {
                        textCharacters = Math.addExact(textCharacters, text.length());
                    }
                }
            }
            if (outcome.features().size() > 64
                    || coordinateCount > 4_096
                    || attributeCount > 4_096
                    || textCharacters > 4_096) {
                throw new AssertionError(
                        "Generated dataset exceeded its fixed ceilings: " + descriptor);
            }
        }
    }

    private static void validateDiagnostics(
            List<DiagnosticKey> diagnostics,
            boolean terminalFailurePhase,
            ReplayDescriptor descriptor) {
        for (int index = 0; index < diagnostics.size(); index++) {
            DiagnosticKey diagnostic = diagnostics.get(index);
            boolean terminal = terminalFailurePhase && index == diagnostics.size() - 1;
            boolean accepted =
                    terminal
                            ? diagnostic.severity() == DiagnosticSeverity.ERROR
                                    && ERROR_CODES.contains(diagnostic.code())
                            : diagnostic.severity() == DiagnosticSeverity.WARNING
                                    && WARNING_CODES.contains(diagnostic.code());
            if (!accepted) {
                throw new AssertionError(
                        "Unexpected diagnostic " + diagnostic.code() + ": " + descriptor);
            }
        }
        if (terminalFailurePhase
                && (diagnostics.isEmpty()
                        || diagnostics.get(diagnostics.size() - 1).severity()
                                != DiagnosticSeverity.ERROR)) {
            throw new AssertionError("Failure phase lacks a terminal error: " + descriptor);
        }
    }

    private static long recordOrdinal(String id, ReplayDescriptor descriptor) {
        if (!id.startsWith("record:")) {
            throw new AssertionError("Unexpected feature ID: " + descriptor);
        }
        try {
            return Long.parseLong(id.substring("record:".length()));
        } catch (NumberFormatException exception) {
            throw new AssertionError("Unexpected feature ID: " + descriptor, exception);
        }
    }

    private static int validateFinite(Geometry geometry, ReplayDescriptor descriptor) {
        return switch (geometry) {
            case PointGeometry point -> {
                requireFinite(point.coordinate().x(), point.coordinate().y(), descriptor);
                yield 1;
            }
            case LineStringGeometry line -> validateFinite(line.coordinates(), descriptor);
            case MultiPointGeometry points -> validateFinite(points.coordinates(), descriptor);
            case MultiLineStringGeometry lines -> validateFinite(lines.coordinates(), descriptor);
            case MultiPolygonGeometry polygons ->
                    validateFinite(polygons.coordinates(), descriptor);
            case PolygonGeometry polygon -> {
                int count = validateFinite(polygon.exterior(), descriptor);
                for (CoordinateSequence hole : polygon.holes()) {
                    count = Math.addExact(count, validateFinite(hole, descriptor));
                }
                yield count;
            }
        };
    }

    private static int validateFinite(CoordinateSequence coordinates, ReplayDescriptor descriptor) {
        for (int index = 0; index < coordinates.size(); index++) {
            requireFinite(coordinates.x(index), coordinates.y(index), descriptor);
        }
        return coordinates.size();
    }

    private static void requireFinite(double x, double y, ReplayDescriptor descriptor) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new AssertionError("Non-finite geometry: " + descriptor);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
