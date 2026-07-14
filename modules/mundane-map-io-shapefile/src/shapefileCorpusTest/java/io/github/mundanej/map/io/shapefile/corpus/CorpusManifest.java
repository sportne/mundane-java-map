package io.github.mundanej.map.io.shapefile.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class CorpusManifest {
    private static final String HEADER =
            "datasetId\trole\tcomponentPath\tbyteLength\tsha256\torigin\ttoolAndVersion\t"
                    + "licenseId\tlicensePath\tparentId\tcoverageTags\texpectationId";
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern EXPECTATION = Pattern.compile("E_[A-Z0-9_]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LICENSE =
            Pattern.compile("(?:[A-Za-z0-9.-]+|LicenseRef-[A-Za-z0-9.-]+)");
    private static final Set<String> EXTENSIONS = Set.of("cpg", "dbf", "prj", "shp", "shx");
    private static final Set<String> TAGS =
            Set.of(
                    "crs-epsg-3857",
                    "crs-epsg-4326",
                    "crs-explicit-override",
                    "crs-unknown-retained",
                    "dbf-corrupt-terminal",
                    "dbf-date",
                    "dbf-decimal",
                    "dbf-floating",
                    "dbf-integer",
                    "dbf-logical",
                    "dbf-text",
                    "encoding-cpg",
                    "encoding-explicit-override",
                    "encoding-fallback",
                    "encoding-ibm437",
                    "encoding-ibm850",
                    "encoding-iso88591",
                    "encoding-ldid",
                    "encoding-utf8",
                    "encoding-windows1252",
                    "index-corrupt-ignored",
                    "index-valid",
                    "record-deleted",
                    "shape-hole",
                    "shape-multipart",
                    "shape-multipoint",
                    "shape-null",
                    "shape-point",
                    "shape-polygon",
                    "shape-polyline",
                    "shape-zm-rejected");
    private static final long MAXIMUM_DATASET_BYTES = 512L * 1024L;
    private static final long MAXIMUM_CORPUS_BYTES = 4L * 1024L * 1024L;

    private final Path root;
    private final List<Row> rows;
    private final Map<String, Dataset> datasets;

    private CorpusManifest(Path root, List<Row> rows, Map<String, Dataset> datasets) {
        this.root = root;
        this.rows = List.copyOf(rows);
        this.datasets = Map.copyOf(datasets);
    }

    static CorpusManifest loadAndVerify(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(normalizedRoot), "missing corpus source root");
        byte[] bytes = Files.readAllBytes(normalizedRoot.resolve("manifest.tsv"));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(text.contains("\r"), "manifest must use LF line endings");
        assertTrue(text.endsWith("\n"), "manifest must end with LF");
        List<String> lines = text.lines().toList();
        assertFalse(lines.isEmpty(), "manifest is empty");
        assertEquals(HEADER, lines.getFirst(), "manifest header");

        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            rows.add(parse(lines.get(index), index + 1));
        }
        assertFalse(rows.isEmpty(), "manifest has no component rows");
        List<Row> sorted =
                rows.stream()
                        .sorted(
                                Comparator.comparing(Row::datasetId)
                                        .thenComparing(Row::componentPath))
                        .toList();
        assertEquals(sorted, rows, "manifest rows must be sorted");

        LinkedHashMap<String, List<Row>> grouped = new LinkedHashMap<>();
        Set<String> paths = new HashSet<>();
        long corpusBytes = 0;
        for (Row row : rows) {
            assertTrue(paths.add(row.componentPath()), "duplicate component path");
            grouped.computeIfAbsent(row.datasetId(), ignored -> new ArrayList<>()).add(row);
            corpusBytes = Math.addExact(corpusBytes, row.byteLength());
            verifyBytes(normalizedRoot, row);
        }
        assertTrue(grouped.size() <= 16, "corpus dataset ceiling");
        assertTrue(corpusBytes <= MAXIMUM_CORPUS_BYTES, "corpus byte ceiling");

        LinkedHashMap<String, Dataset> datasets = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> entry : grouped.entrySet()) {
            Dataset dataset = verifyDataset(entry.getKey(), entry.getValue());
            datasets.put(entry.getKey(), dataset);
        }
        verifyParents(datasets);
        verifyInventory(normalizedRoot, rows);
        assertEquals(CorpusExpectations.ids(), expectationIds(datasets), "expectation inventory");
        assertEquals(
                TAGS,
                datasets.values().stream()
                        .flatMap(v -> v.tags().stream())
                        .collect(java.util.stream.Collectors.toSet()),
                "coverage vocabulary completeness");
        for (Dataset dataset : datasets.values()) {
            assertEquals(
                    dataset.tags(),
                    CorpusExpectations.coveredTags(dataset.expectationId()),
                    dataset.id() + " expectation-tag coverage");
        }
        return new CorpusManifest(normalizedRoot, rows, datasets);
    }

    Path root() {
        return root;
    }

    List<Row> rows() {
        return rows;
    }

    List<Dataset> datasets() {
        return datasets.values().stream().sorted(Comparator.comparing(Dataset::id)).toList();
    }

    Dataset dataset(String id) {
        Dataset value = datasets.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown corpus dataset: " + id);
        }
        return value;
    }

    private static Row parse(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        assertEquals(12, values.length, "manifest columns at line " + lineNumber);
        for (int index = 0; index < values.length; index++) {
            if (index != 9) {
                assertFalse(values[index].isEmpty(), "empty field at line " + lineNumber);
            }
            assertTrue(
                    values[index].codePoints().noneMatch(Character::isISOControl),
                    "control in manifest field at line " + lineNumber);
        }
        assertTrue(ID.matcher(values[0]).matches(), "dataset ID at line " + lineNumber);
        assertTrue(
                values[1].equals("CURATED") || values[1].equals("GENERATED"),
                "role at line " + lineNumber);
        verifyRelativePath(values[2], "componentPath");
        verifyRelativePath(values[8], "licensePath");
        assertTrue(values[8].startsWith("licenses/"), "license path root at line " + lineNumber);
        assertTrue(LICENSE.matcher(values[7]).matches(), "license ID at line " + lineNumber);
        if (!values[9].isEmpty()) {
            assertTrue(ID.matcher(values[9]).matches(), "parent ID at line " + lineNumber);
        }
        assertTrue(
                EXPECTATION.matcher(values[11]).matches(), "expectation ID at line " + lineNumber);
        long byteLength = Long.parseLong(values[3]);
        assertTrue(byteLength > 0, "positive byte length at line " + lineNumber);
        assertTrue(SHA_256.matcher(values[4]).matches(), "SHA-256 at line " + lineNumber);
        List<String> tags = List.of(values[10].split(",", -1));
        assertEquals(tags.stream().sorted().toList(), tags, "sorted tags at line " + lineNumber);
        assertEquals(tags.size(), new HashSet<>(tags).size(), "unique tags at line " + lineNumber);
        assertTrue(TAGS.containsAll(tags), "unknown tag at line " + lineNumber);
        return new Row(
                values[0],
                values[1],
                values[2],
                byteLength,
                values[4],
                values[5],
                values[6],
                values[7],
                values[8],
                values[9],
                Set.copyOf(tags),
                values[11]);
    }

    private static Dataset verifyDataset(String id, List<Row> rows) {
        Row first = rows.getFirst();
        long bytes = 0;
        Set<String> extensions = new LinkedHashSet<>();
        for (Row row : rows) {
            assertEquals(id, row.datasetId());
            assertEquals(first.role(), row.role(), id + " role");
            assertEquals(first.origin(), row.origin(), id + " origin");
            assertEquals(first.toolAndVersion(), row.toolAndVersion(), id + " tool/version");
            assertEquals(first.licenseId(), row.licenseId(), id + " license ID");
            assertEquals(first.licensePath(), row.licensePath(), id + " license path");
            assertEquals(first.parentId(), row.parentId(), id + " parent");
            assertEquals(first.tags(), row.tags(), id + " tags");
            assertEquals(first.expectationId(), row.expectationId(), id + " expectation");
            String expectedPrefix = "data/" + id + "/" + id + ".";
            assertTrue(row.componentPath().startsWith(expectedPrefix), id + " component basename");
            String extension = row.componentPath().substring(expectedPrefix.length());
            assertTrue(EXTENSIONS.contains(extension), id + " component extension");
            assertTrue(extensions.add(extension), id + " duplicate component extension");
            bytes = Math.addExact(bytes, row.byteLength());
        }
        assertTrue(extensions.contains("shp"), id + " requires SHP");
        assertTrue(bytes <= MAXIMUM_DATASET_BYTES, id + " dataset byte ceiling");
        return new Dataset(
                id,
                first.role(),
                first.origin(),
                first.toolAndVersion(),
                first.licenseId(),
                first.licensePath(),
                first.parentId(),
                first.tags(),
                first.expectationId(),
                List.copyOf(rows));
    }

    private static void verifyParents(Map<String, Dataset> datasets) {
        for (Dataset dataset : datasets.values()) {
            if (dataset.parentId().isEmpty()) {
                continue;
            }
            assertFalse(dataset.parentId().equals(dataset.id()), "self parent: " + dataset.id());
            Dataset parent = datasets.get(dataset.parentId());
            assertTrue(parent != null, "unknown parent: " + dataset.id());
            assertTrue(dataset.origin().startsWith("derive:"), "derived origin: " + dataset.id());
            assertEquals(
                    parent.licenseId(), dataset.licenseId(), dataset.id() + " parent license ID");
            assertEquals(
                    parent.licensePath(),
                    dataset.licensePath(),
                    dataset.id() + " parent license path");
        }
        for (Dataset dataset : datasets.values()) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> chain = new ArrayDeque<>();
            chain.add(dataset.id());
            while (!chain.isEmpty()) {
                String id = chain.removeFirst();
                assertTrue(visited.add(id), "parent cycle from " + dataset.id());
                String parent = datasets.get(id).parentId();
                if (!parent.isEmpty()) {
                    chain.add(parent);
                }
            }
        }
    }

    private static void verifyInventory(Path root, List<Row> rows) throws IOException {
        Set<String> expected = new HashSet<>();
        expected.add("manifest.tsv");
        for (Row row : rows) {
            expected.add(row.componentPath());
            expected.add(row.licensePath());
        }
        Set<String> actual = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .forEach(actual::add);
        }
        assertEquals(expected, actual, "corpus file inventory");
    }

    private static void verifyBytes(Path root, Row row) throws IOException {
        Path file = root.resolve(row.componentPath()).normalize();
        assertTrue(file.startsWith(root), "component escapes corpus root");
        assertTrue(Files.isRegularFile(file), "missing component: " + row.componentPath());
        assertEquals(row.byteLength(), Files.size(file), row.componentPath() + " byte length");
        assertEquals(row.sha256(), sha256(file), row.componentPath() + " SHA-256");
        Path license = root.resolve(row.licensePath()).normalize();
        assertTrue(license.startsWith(root), "license escapes corpus root");
        assertTrue(Files.isRegularFile(license), "missing license: " + row.licensePath());
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (var input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("JDK lacks SHA-256", exception);
        }
    }

    private static void verifyRelativePath(String value, String field) {
        assertFalse(value.startsWith("/"), field + " starts with slash");
        assertFalse(value.contains("\\"), field + " contains backslash");
        assertFalse(value.contains(":"), field + " contains scheme/drive separator");
        String[] segments = value.split("/", -1);
        assertTrue(segments.length > 1, field + " must have a root directory");
        for (String segment : segments) {
            assertFalse(
                    segment.isEmpty() || segment.equals(".") || segment.equals(".."),
                    field + " unsafe segment");
        }
    }

    private static Set<String> expectationIds(Map<String, Dataset> datasets) {
        Set<String> ids = new HashSet<>();
        for (Dataset dataset : datasets.values()) {
            assertTrue(ids.add(dataset.expectationId()), "duplicate expectation ID");
        }
        return Set.copyOf(ids);
    }

    record Row(
            String datasetId,
            String role,
            String componentPath,
            long byteLength,
            String sha256,
            String origin,
            String toolAndVersion,
            String licenseId,
            String licensePath,
            String parentId,
            Set<String> tags,
            String expectationId) {}

    record Dataset(
            String id,
            String role,
            String origin,
            String toolAndVersion,
            String licenseId,
            String licensePath,
            String parentId,
            Set<String> tags,
            String expectationId,
            List<Row> rows) {
        Path sourcePath(Path root, String extension) {
            return root.resolve("data").resolve(id).resolve(id + "." + extension);
        }

        boolean has(String extension) {
            return rows.stream().anyMatch(row -> row.componentPath().endsWith("." + extension));
        }
    }
}
