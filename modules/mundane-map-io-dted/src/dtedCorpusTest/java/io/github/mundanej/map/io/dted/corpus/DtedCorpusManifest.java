package io.github.mundanej.map.io.dted.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DtedCorpusManifest {
    static final int MAXIMUM_DATASETS = 6;
    static final long MAXIMUM_DATASET_BYTES = 5L * 1024L * 1024L;
    static final long MAXIMUM_CORPUS_BYTES = 6L * 1024L * 1024L;
    static final String MANIFEST_SHA256 =
            "6343eaad6b92f317b6fbef25426eae9287f4ecf46c54258bd662a4e75cd99a82";

    private static final String HEADER =
            "datasetId\trole\tfilePath\toriginalFilename\tbyteLength\tsha256\torigin\t"
                    + "toolAndVersion\tlicenseId\tlicensePath\tparentId\tderivation\t"
                    + "coverageTags\texpectationId";
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern LICENSE =
            Pattern.compile("(?:[A-Za-z0-9.-]+|LicenseRef-[A-Za-z0-9.-]+)");
    private static final Pattern DECIMAL = Pattern.compile("(?:0|[1-9][0-9]*)");
    static final Map<String, String> APPROVED_AUXILIARY_SHA256 =
            Map.of(
                    "licenses/BSD-3-Clause.txt",
                    "bdb8a64e8b9d8b172c29ac55927b3659b7f44f21093a4a6880d85dd9a8dcae91",
                    "licenses/GDAL-MIT.txt",
                    "248ef06377cf0679b9ae3c80ab04f6b73fd78bf66d3d81f7d7d934addb212844",
                    "recipes/gdal-3.13.0-zone-v.sh",
                    "7112e2dd57437356170446108970fbc08c0da9b9ac384584f4ffef6e7187c7c7");

    private static final Set<String> ROLES = Set.of("GENERATED");
    private static final Set<String> TAGS =
            Set.of(
                    "checksum",
                    "complete",
                    "level0",
                    "level1",
                    "level2",
                    "negative-elevation",
                    "partial",
                    "positive-elevation",
                    "southern-cell",
                    "void",
                    "western-cell",
                    "zero-elevation",
                    "zone-v");
    private static final String TOOL =
            "GDAL 3.13.0; ghcr.io/osgeo/gdal:ubuntu-full-3.13.0; "
                    + "linux/amd64@sha256:fd205102ddfaa537e18dac37a9f648e79989e99a4e6f6a2375e5f7e0e511616c";
    private static final String RECIPE = "recipes/gdal-3.13.0-zone-v.sh";

    private final Path root;
    private final List<Row> rows;
    private final Map<String, Row> datasets;
    private final long resourceBytes;

    private DtedCorpusManifest(
            Path root, List<Row> rows, Map<String, Row> datasets, long resourceBytes) {
        this.root = root;
        this.rows = List.copyOf(rows);
        this.datasets = Map.copyOf(datasets);
        this.resourceBytes = resourceBytes;
    }

    static DtedCorpusManifest loadAndVerify(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(normalizedRoot), "missing DTED corpus source root");
        Path manifest = normalizedRoot.resolve("manifest.tsv");
        byte[] bytes = Files.readAllBytes(manifest);
        String text = decodeUtf8(bytes);
        assertFalse(text.contains("\r"), "manifest must use LF line endings");
        assertTrue(text.endsWith("\n"), "manifest must end with LF");
        List<String> lines = text.lines().toList();
        assertFalse(lines.isEmpty(), "manifest is empty");
        assertEquals(HEADER, lines.getFirst(), "manifest header");

        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            rows.add(parse(lines.get(index), index + 1));
        }
        assertFalse(rows.isEmpty(), "manifest has no datasets");
        assertEquals(
                rows.stream().sorted(Comparator.comparing(Row::datasetId)).toList(),
                rows,
                "manifest rows must be sorted");

        Map<String, Row> datasets = new LinkedHashMap<>();
        Set<String> filePaths = new HashSet<>();
        Set<String> expectationIds = new HashSet<>();
        for (Row row : rows) {
            assertTrue(filePaths.add(row.filePath()), "duplicate file path: " + row.filePath());
            assertTrue(
                    expectationIds.add(row.expectationId()),
                    "duplicate expectation ID: " + row.expectationId());
            assertTrue(datasets.put(row.datasetId(), row) == null, "duplicate dataset ID");
            verifyReferences(normalizedRoot, row);
        }
        assertDatasetCount(datasets.size());
        assertEquals(DtedCorpusExpectations.ids(), expectationIds, "expectation inventory");
        assertEquals(
                TAGS,
                rows.stream()
                        .flatMap(row -> row.coverageTags().stream())
                        .collect(Collectors.toSet()),
                "coverage vocabulary completeness");
        verifyRolesAndParents(datasets);
        verifyRecipe(normalizedRoot.resolve(RECIPE));
        for (Map.Entry<String, String> approved : APPROVED_AUXILIARY_SHA256.entrySet()) {
            assertEquals(
                    approved.getValue(),
                    DtedCorpusTestSupport.sha256(resolve(normalizedRoot, approved.getKey())),
                    approved.getKey() + " approved SHA-256");
        }

        Set<String> expected = new HashSet<>();
        expected.add("manifest.tsv");
        expected.add("licenses/GDAL-MIT.txt");
        for (Row row : rows) {
            expected.add(row.filePath());
            expected.add(row.licensePath());
            if (!row.derivation().equals("none")) {
                expected.add(row.derivation().substring(row.derivation().indexOf(':') + 1));
            }
        }
        Set<String> actual = new HashSet<>();
        long total = 0;
        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                assertFalse(
                        Files.isSymbolicLink(path), "corpus resource must not be a symbolic link");
                actual.add(toPortable(normalizedRoot.relativize(path)));
                total = Math.addExact(total, Files.size(path));
            }
        }
        assertEquals(expected, actual, "corpus file inventory");
        assertAtMost(total, MAXIMUM_CORPUS_BYTES, "complete corpus resource-tree byte ceiling");
        return new DtedCorpusManifest(normalizedRoot, rows, datasets, total);
    }

    Path root() {
        return root;
    }

    List<Row> rows() {
        return rows;
    }

    List<Row> datasets() {
        return datasets.values().stream().sorted(Comparator.comparing(Row::datasetId)).toList();
    }

    long resourceBytes() {
        return resourceBytes;
    }

    static void assertAtMost(long value, long maximum, String label) {
        assertTrue(value <= maximum, label + ": " + value + " > " + maximum);
    }

    static void assertDatasetCount(int count) {
        assertTrue(count <= MAXIMUM_DATASETS, "corpus dataset ceiling");
    }

    private static Row parse(String line, int lineNumber) {
        String[] values = line.split("\t", -1);
        assertEquals(14, values.length, "manifest columns at line " + lineNumber);
        for (int index = 0; index < values.length; index++) {
            if (index != 10) {
                assertFalse(values[index].isEmpty(), "empty field at line " + lineNumber);
            }
            assertTrue(
                    values[index].codePoints().noneMatch(Character::isISOControl),
                    "control in manifest field at line " + lineNumber);
        }
        assertTrue(ID.matcher(values[0]).matches(), "dataset ID at line " + lineNumber);
        assertTrue(ROLES.contains(values[1]), "role at line " + lineNumber);
        verifyRelativePath(values[2], "filePath");
        assertTrue(values[2].startsWith("data/" + values[0] + "/"), "dataset path root");
        assertEquals(Path.of(values[2]).getFileName().toString(), values[3], "original filename");
        assertTrue(DECIMAL.matcher(values[4]).matches(), "canonical byte length");
        long length = Long.parseLong(values[4]);
        assertTrue(length > 0, "positive byte length");
        assertAtMost(length, MAXIMUM_DATASET_BYTES, values[0] + " byte ceiling");
        assertTrue(SHA_256.matcher(values[5]).matches(), "SHA-256 at line " + lineNumber);
        assertEquals(TOOL, values[7], "tool/version at line " + lineNumber);
        assertTrue(LICENSE.matcher(values[8]).matches(), "license ID at line " + lineNumber);
        verifyRelativePath(values[9], "licensePath");
        assertTrue(values[9].startsWith("licenses/"), "license path root");
        if (!values[10].isEmpty()) {
            assertTrue(ID.matcher(values[10]).matches(), "parent ID at line " + lineNumber);
        }
        if (!values[11].equals("none")) {
            int separator = values[11].indexOf(':');
            assertTrue(separator > 0, "derivation grammar at line " + lineNumber);
            verifyRelativePath(values[11].substring(separator + 1), "derivation recipe");
            assertTrue(
                    values[11].substring(separator + 1).startsWith("recipes/"), "recipe path root");
        }
        List<String> tags = List.of(values[12].split(",", -1));
        assertEquals(tags.stream().sorted().toList(), tags, "sorted tags at line " + lineNumber);
        assertEquals(tags.size(), new HashSet<>(tags).size(), "unique tags at line " + lineNumber);
        assertTrue(TAGS.containsAll(tags), "unknown tag at line " + lineNumber);
        assertTrue(ID.matcher(values[13]).matches(), "expectation ID at line " + lineNumber);
        return new Row(
                values[0],
                values[1],
                values[2],
                values[3],
                length,
                values[5],
                values[6],
                values[7],
                values[8],
                values[9],
                values[10],
                values[11],
                Set.copyOf(tags),
                values[13]);
    }

    private static void verifyReferences(Path root, Row row) throws IOException {
        Path file = resolve(root, row.filePath());
        assertTrue(Files.isRegularFile(file), "missing data file: " + row.filePath());
        assertEquals(row.byteLength(), Files.size(file), row.filePath() + " byte length");
        assertEquals(row.sha256(), DtedCorpusTestSupport.sha256(file), row.filePath() + " SHA-256");
        assertTrue(Files.isRegularFile(resolve(root, row.licensePath())), "missing license");
        if (!row.derivation().equals("none")) {
            String recipe = row.derivation().substring(row.derivation().indexOf(':') + 1);
            assertTrue(Files.isRegularFile(resolve(root, recipe)), "missing recipe");
        }
        assertTrue(
                DtedCorpusExpectations.ids().contains(row.expectationId()),
                "unknown expectation: " + row.expectationId());
    }

    private static void verifyRolesAndParents(Map<String, Row> datasets) {
        for (Row row : datasets.values()) {
            assertEquals("GENERATED", row.role(), "initial corpus role");
            assertTrue(row.parentId().isEmpty(), "generated parent: " + row.datasetId());
            assertTrue(row.derivation().startsWith("generate:"), "generated derivation");
        }
    }

    private static void verifyRecipe(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, String> expected = new HashMap<>();
        expected.put("IMAGE_TAG", "ghcr.io/osgeo/gdal:ubuntu-full-3.13.0");
        expected.put(
                "IMAGE_MANIFEST_DIGEST",
                "sha256:fd205102ddfaa537e18dac37a9f648e79989e99a4e6f6a2375e5f7e0e511616c");
        expected.put(
                "IMAGE_CONFIG_DIGEST",
                "sha256:be85b2a4b798f1d2f10bb9b724336976ce1bf1b0791298b8ad8379fc012d3138");
        expected.put("IMAGE_PLATFORM", "linux/amd64");
        expected.put("TOOL_LICENSE_ID", "MIT");
        expected.put("TOOL_LICENSE_PATH", "licenses/GDAL-MIT.txt");
        expected.put("DATA_LICENSE_ID", "BSD-3-Clause");
        expected.put("DATA_LICENSE_PATH", "licenses/BSD-3-Clause.txt");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertTrue(
                    text.contains(entry.getKey() + "='" + entry.getValue() + "'"),
                    "recipe constant " + entry.getKey());
        }
        assertTrue(text.contains("GDAL 3.13.0"), "recipe GDAL version");
    }

    private static Path resolve(Path root, String relative) {
        Path path = root.resolve(relative).normalize();
        assertTrue(path.startsWith(root), "resource escapes corpus root");
        return path;
    }

    private static void verifyRelativePath(String value, String label) {
        assertFalse(value.isBlank(), label + " is blank");
        assertFalse(value.startsWith("/") || value.startsWith("\\"), label + " is absolute");
        assertFalse(value.contains("\\"), label + " contains backslash");
        assertFalse(value.contains(":"), label + " contains URI or drive prefix");
        List<String> segments = List.of(value.split("/", -1));
        assertTrue(
                segments.stream()
                        .noneMatch(
                                segment ->
                                        segment.isEmpty()
                                                || segment.equals(".")
                                                || segment.equals("..")),
                label + " contains an unsafe segment");
    }

    private static String toPortable(Path value) {
        return value.toString().replace('\\', '/');
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new AssertionError("manifest must be well-formed UTF-8", exception);
        }
    }

    record Row(
            String datasetId,
            String role,
            String filePath,
            String originalFilename,
            long byteLength,
            String sha256,
            String origin,
            String toolAndVersion,
            String licenseId,
            String licensePath,
            String parentId,
            String derivation,
            Set<String> coverageTags,
            String expectationId) {
        Row {
            coverageTags = Set.copyOf(coverageTags);
        }
    }
}
