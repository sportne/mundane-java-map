package io.github.mundanej.map.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFilesTest {
    private static final String CANONICAL =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <workspace xmlns="urn:mundanej:map:workspace" version="1">
              <view map-crs="EPSG:4326" display-crs="EPSG:3857"
                    center-x="0.0" center-y="-0.0" units-per-pixel="1000.0"/>
              <layers>
                <feature-layer id="roads" name="Roads">
                  <source opener="application.shapefile.v1" id="roads-source" name="Road data" path="data/roads.shp"/>
                  <symbols catalog="application.default" marker="point" line="road" fill="area"/>
                </feature-layer>
                <raster-layer id="image" name="Image" interpolation="BILINEAR" opacity="1.0">
                  <source opener="application.image.v1" id="image-source" name="Image data" path="data/image.png"/>
                </raster-layer>
              </layers>
            </workspace>
            """;

    @TempDir Path temporary;
    private int nextFile;

    @Test
    void readsCanonicalWorkspaceIntoImmutableOrderedValues() throws IOException {
        Path input = write(CANONICAL);

        WorkspaceFile file = WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT);

        assertEquals(temporary.toRealPath(), file.baseDirectory());
        assertEquals("EPSG:4326", file.document().view().mapCrsKey());
        assertEquals("EPSG:3857", file.document().view().displayCrsKey());
        assertEquals(0.0, file.document().view().centerY());
        assertEquals(
                List.of("roads", "image"),
                file.document().layers().stream().map(WorkspaceLayerDefinition::id).toList());
        WorkspaceFeatureLayer feature = (WorkspaceFeatureLayer) file.document().layers().getFirst();
        assertEquals("application.shapefile.v1", feature.source().openerId());
        assertEquals("data/roads.shp", feature.source().path().value());
        assertEquals("road", feature.symbols().lineName());
        WorkspaceRasterLayer raster = (WorkspaceRasterLayer) file.document().layers().getLast();
        assertEquals(RasterInterpolation.BILINEAR, raster.interpolation());
        assertEquals(1.0, raster.opacity());
        assertThrows(
                UnsupportedOperationException.class, () -> file.document().layers().add(raster));
    }

    @Test
    void acceptsPrefixAttributeOrderCommentsBOMAndDecimalVariants() throws IOException {
        String variant =
                """
                <?xml version="1.0"?>
                <w:workspace version="1" xmlns:w="urn:mundanej:map:workspace">
                  <!-- bounded -->
                  <w:view units-per-pixel="1." center-y="-.5e+1" center-x="+2E2"
                          display-crs="EPSG:4326" map-crs="EPSG:3857"/>
                  <w:layers></w:layers>
                </w:workspace>
                """;
        byte[] text = variant.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(text, 0, bytes, 3, text.length);
        Path input = temporary.resolve("variant.mmap.xml");
        Files.write(input, bytes);

        WorkspaceViewState view =
                WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT).document().view();

        assertEquals(200.0, view.centerX());
        assertEquals(-5.0, view.centerY());
        assertEquals(1.0, view.unitsPerPixel());
    }

    @Test
    void acceptsReferencesAndCdataTextInsideCommentsOnly() throws IOException {
        String input =
                CANONICAL
                        .replace("Roads", "Roads &amp; Paths")
                        .replace("marker=\"point\"", "marker=\"&#x70;oint\"")
                        .replace("  <view", "  <!-- literal <![CDATA[ marker -->\n  <view");

        WorkspaceFeatureLayer feature =
                (WorkspaceFeatureLayer)
                        WorkspaceFiles.read(write(input), WorkspaceLimits.DEFAULT)
                                .document()
                                .layers()
                                .getFirst();

        assertEquals("Roads & Paths", feature.name());
        assertEquals("point", feature.symbols().markerName());
    }

    @Test
    void publicValuesDefensivelyCopyAndValidateClosedProfiles() {
        List<WorkspaceLayerDefinition> requested = new ArrayList<>();
        requested.add(feature("one"));
        WorkspaceDocument document =
                new WorkspaceDocument(
                        new WorkspaceViewState("EPSG:4326", "EPSG:3857", -0.0, 0.0, 1), requested);
        requested.clear();
        assertEquals(1, document.layers().size());
        assertEquals(0.0, document.view().centerX());

        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceViewState("WGS84", "EPSG:3857", 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceRelativePath("../escape"));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceRelativePath("C:/escape"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceSourceReference(
                                "Java.Class",
                                new SourceIdentity("id", ""),
                                new WorkspaceRelativePath("a")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceSymbolReferences(
                                "application.symbols", " marker", "line", "fill"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceDocument(
                                document.view(), List.of(feature("same"), feature("same"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceRasterLayer(
                                "r", "", source("r"), RasterInterpolation.NEAREST, 1.1));

        String maximumOpener = "a." + "b".repeat(126);
        String maximumPath = "a".repeat(4_096);
        String maximumSymbol = "s".repeat(256);
        assertEquals(
                maximumOpener,
                new WorkspaceSourceReference(
                                maximumOpener,
                                new SourceIdentity("id", ""),
                                new WorkspaceRelativePath(maximumPath))
                        .openerId());
        assertEquals(
                maximumSymbol,
                new WorkspaceSymbolReferences("a.b", maximumSymbol, maximumSymbol, maximumSymbol)
                        .markerName());
        assertEquals(256, feature("i".repeat(256)).id().length());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceSourceReference(
                                maximumOpener + "b",
                                new SourceIdentity("id", ""),
                                new WorkspaceRelativePath("a")));
        assertThrows(
                IllegalArgumentException.class, () -> new WorkspaceRelativePath(maximumPath + "a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceSymbolReferences("a.b", maximumSymbol + "s", "line", "fill"));
        assertThrows(IllegalArgumentException.class, () -> feature("i".repeat(257)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceFeatureLayer(
                                "id", "\uD800", source("id"), feature("id").symbols()));
    }

    @Test
    void fixedModelAllocationCeilingSucceedsExactlyAndFailsOneCharacterOver() {
        WorkspaceViewState view = new WorkspaceViewState("EPSG:4326", "EPSG:3857", 0, 0, 1);
        List<WorkspaceLayerDefinition> minimum = new ArrayList<>(WorkspaceText.MAX_LAYERS);
        for (int index = 0; index < WorkspaceText.MAX_LAYERS; index++) {
            minimum.add(raster(index, 1));
        }
        long minimumBytes = WorkspaceDocument.logicalModelBytes(view, minimum);
        long difference = WorkspaceText.MAX_MODEL_BYTES - minimumBytes;
        assertEquals(0, difference % 2);
        long remainingCharacters = difference / 2;
        List<WorkspaceLayerDefinition> exact = new ArrayList<>(WorkspaceText.MAX_LAYERS);
        for (int index = 0; index < WorkspaceText.MAX_LAYERS; index++) {
            int additional = (int) Math.min(4_095L, remainingCharacters);
            exact.add(raster(index, 1 + additional));
            remainingCharacters -= additional;
        }
        assertEquals(0, remainingCharacters);
        assertEquals(
                WorkspaceText.MAX_MODEL_BYTES, WorkspaceDocument.logicalModelBytes(view, exact));
        WorkspaceDocument document = new WorkspaceDocument(view, exact);
        assertEquals(WorkspaceText.MAX_LAYERS, document.layers().size());

        List<WorkspaceLayerDefinition> oneOver = new ArrayList<>(exact);
        for (int index = oneOver.size() - 1; index >= 0; index--) {
            WorkspaceRasterLayer layer = (WorkspaceRasterLayer) oneOver.get(index);
            int length = layer.source().path().value().length();
            if (length < 4_096) {
                oneOver.set(index, raster(index, length + 1));
                break;
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceDocument(view, oneOver));
    }

    @Test
    void problemContextIsInsertionOrderedImmutableAndBounded() {
        Map<String, String> requested = new LinkedHashMap<>();
        requested.put("field", "mapCrs");
        requested.put("reason", "grammar");
        WorkspaceProblem problem = new WorkspaceProblem("WORKSPACE_VALUE_INVALID", requested);
        requested.clear();

        assertEquals(List.of("field", "reason"), List.copyOf(problem.context().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> problem.context().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceProblem("raw", Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        problem(
                                "WORKSPACE_VALUE_INVALID",
                                "field",
                                "mapCrs",
                                "secret",
                                "/tmp/token"));
        assertThrows(
                IllegalArgumentException.class,
                () -> problem("WORKSPACE_VALUE_INVALID", "reason", "grammar", "field", "mapCrs"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        problem(
                                "WORKSPACE_LIMIT_EXCEEDED",
                                "limit",
                                "layers",
                                "requested",
                                "01",
                                "maximum",
                                "1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> problem("WORKSPACE_SOURCE_OPEN_FAILED", "kind", "FEATURE"));

        List<WorkspaceProblem> closedShapes =
                List.of(
                        problem("WORKSPACE_IO_FAILED", "phase", "input", "reason", "read"),
                        problem("WORKSPACE_ENCODING_INVALID", "reason", "malformed"),
                        problem("WORKSPACE_XML_INVALID", "reason", "security"),
                        problem("WORKSPACE_VERSION_UNSUPPORTED", "reason", "version"),
                        problem("WORKSPACE_FIELD_UNKNOWN", "field", "element", "layerIndex", "0"),
                        problem(
                                "WORKSPACE_STRUCTURE_INVALID",
                                "reason",
                                "duplicate",
                                "layerIndex",
                                "1"),
                        problem("WORKSPACE_VALUE_INVALID", "field", "opacity", "reason", "range"),
                        problem(
                                "WORKSPACE_LIMIT_EXCEEDED",
                                "limit",
                                "layers",
                                "requested",
                                "2",
                                "maximum",
                                "1"),
                        problem("WORKSPACE_PATH_INVALID", "reason", "escape", "layerIndex", "0"),
                        problem("WORKSPACE_RESOURCE_MISSING", "kind", "primary", "layerIndex", "0"),
                        problem(
                                "WORKSPACE_SOURCE_OPENER_UNREGISTERED",
                                "kind",
                                "FEATURE",
                                "layerIndex",
                                "0"),
                        problem(
                                "WORKSPACE_SOURCE_KIND_MISMATCH",
                                "expected",
                                "FEATURE",
                                "actual",
                                "RASTER",
                                "layerIndex",
                                "0"),
                        problem("WORKSPACE_SOURCE_IDENTITY_MISMATCH", "layerIndex", "0"),
                        problem("WORKSPACE_SYMBOL_CATALOG_UNREGISTERED", "layerIndex", "0"),
                        problem("WORKSPACE_SYMBOL_NOT_FOUND", "role", "marker", "layerIndex", "0"),
                        problem(
                                "WORKSPACE_SYMBOL_ROLE_MISMATCH",
                                "role",
                                "line",
                                "layerIndex",
                                "0"),
                        problem("WORKSPACE_CRS_UNREGISTERED", "field", "mapCrs"),
                        problem(
                                "WORKSPACE_SOURCE_OPEN_FAILED",
                                "kind",
                                "RASTER",
                                "layerIndex",
                                "0"),
                        problem("WORKSPACE_CANCELLED", "phase", "preflight"),
                        problem("WORKSPACE_CANCELLED", "phase", "sourceOpen", "layerIndex", "0"),
                        problem("WORKSPACE_ATOMIC_MOVE_UNSUPPORTED"),
                        problem("WORKSPACE_WRITE_FAILED", "phase", "move", "reason", "io"));
        assertEquals(22, closedShapes.size());
    }

    @Test
    void limitConstructionAndWithersEnforceHardAndRelationalBounds() {
        WorkspaceLimits limits = WorkspaceLimits.DEFAULT.withLayers(2).withDepth(7);
        assertEquals(2, limits.layers());
        assertEquals(7, limits.depth());
        assertEquals(WorkspaceLimits.DEFAULT, WorkspaceLimits.DEFAULT.withLayers(1_024));
        assertThrows(
                IllegalArgumentException.class,
                () -> consume(WorkspaceLimits.DEFAULT.withDepth(17)));
        assertThrows(
                IllegalArgumentException.class,
                () -> consume(WorkspaceLimits.DEFAULT.withLayers(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceLimits(100, 99, 8, 10, 10, 1, 10, 10));

        WorkspaceLimits hardMaximum =
                new WorkspaceLimits(
                        16_777_216L, 67_108_864L, 16, 32_768, 131_072, 4_096, 16_384, 4_194_304L);
        assertEquals(4_096, hardMaximum.layers());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceLimits(
                                16_777_217L,
                                67_108_864L,
                                16,
                                32_768,
                                131_072,
                                4_096,
                                16_384,
                                4_194_304L));
        assertThrows(
                IllegalArgumentException.class,
                () -> consume(hardMaximum.withOperationBytes(67_108_865L)));
        assertThrows(IllegalArgumentException.class, () -> consume(hardMaximum.withDepth(17)));
        assertThrows(
                IllegalArgumentException.class, () -> consume(hardMaximum.withElements(32_769)));
        assertThrows(
                IllegalArgumentException.class, () -> consume(hardMaximum.withAttributes(131_073)));
        assertThrows(IllegalArgumentException.class, () -> consume(hardMaximum.withLayers(4_097)));
        assertThrows(
                IllegalArgumentException.class, () -> consume(hardMaximum.withValueChars(16_385)));
        assertThrows(
                IllegalArgumentException.class,
                () -> consume(hardMaximum.withAggregateChars(4_194_305L)));
    }

    @Test
    void rejectsMalformedEncodingAndForeignBoms() throws IOException {
        Path malformed = temporary.resolve("malformed.mmap.xml");
        Files.write(malformed, new byte[] {(byte) 0xC0, (byte) 0xAF});
        assertProblem("WORKSPACE_ENCODING_INVALID", "reason", "malformed", malformed);

        Path utf16 = temporary.resolve("utf16.mmap.xml");
        Files.write(utf16, new byte[] {(byte) 0xFE, (byte) 0xFF, 0, '<'});
        assertProblem("WORKSPACE_ENCODING_INVALID", "reason", "bom", utf16);

        Path invalidXmlCharacter = temporary.resolve("invalid-character.mmap.xml");
        Files.write(
                invalidXmlCharacter,
                CANONICAL.replace("Roads", "Roads\u0001").getBytes(StandardCharsets.UTF_8));
        assertProblem("WORKSPACE_ENCODING_INVALID", "reason", "xmlCharacter", invalidXmlCharacter);
    }

    @Test
    void rejectsDtdEntitiesProcessingInstructionsAndCdata() throws IOException {
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "security",
                write(
                        """
                        <!DOCTYPE workspace [<!ENTITY x "boom">]>
                        <workspace xmlns="urn:mundanej:map:workspace" version="1">
                          <view map-crs="EPSG:4326" display-crs="EPSG:3857"
                                center-x="0" center-y="0" units-per-pixel="1"/>
                          <layers/>
                        </workspace>
                        """));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "security",
                write(
                        CANONICAL.replace(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                                "<!DOCTYPE workspace SYSTEM \"file:///etc/passwd\">")));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "security",
                write(
                        CANONICAL
                                .replace(
                                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                                        "<!DOCTYPE workspace [<!ENTITY x \"<![CDATA[\">]>")
                                .replace("Roads", "&x;")));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "content",
                write(CANONICAL.replace("  <view", "  <?unsafe x?>\n  <view")));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "content",
                write(CANONICAL.replace("  <view", "  <![CDATA[x]]>\n  <view")));
    }

    @Test
    void rejectsVersionNamespaceUnknownMissingDuplicateAndText() throws IOException {
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "declaration",
                write(CANONICAL.replace("version=\"1.0\"", "version=\"1.1\"")));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "declaration",
                write(CANONICAL.replace("encoding=\"UTF-8\"", "encoding=\"ISO-8859-1\"")));
        assertProblem(
                "WORKSPACE_VERSION_UNSUPPORTED",
                "reason",
                "version",
                write(CANONICAL.replace("version=\"1\"", "version=\"2\"")));
        assertProblem(
                "WORKSPACE_VERSION_UNSUPPORTED",
                "reason",
                "namespace",
                write(CANONICAL.replace(WorkspaceText.NAMESPACE, "urn:other")));
        assertProblem(
                "WORKSPACE_FIELD_UNKNOWN",
                "field",
                "namespace",
                write(CANONICAL.replace("version=\"1\"", "xmlns:x=\"urn:other\" version=\"1\"")));
        assertProblem(
                "WORKSPACE_FIELD_UNKNOWN",
                "field",
                "attribute",
                write(CANONICAL.replace("<layers>", "<layers xml:lang=\"en\">")));
        assertProblem(
                "WORKSPACE_FIELD_UNKNOWN",
                "field",
                "attribute",
                write(CANONICAL.replace("<layers>", "<layers extra=\"x\">")));
        assertProblem(
                "WORKSPACE_FIELD_UNKNOWN",
                "field",
                "element",
                write(CANONICAL.replace("<layers>", "<layers><unknown/>")));
        assertProblem(
                "WORKSPACE_XML_INVALID",
                "reason",
                "wellFormed",
                write(CANONICAL.replace("version=\"1\"", "version=\"1\" version=\"1\"")));
        assertProblem(
                "WORKSPACE_STRUCTURE_INVALID",
                "reason",
                "missing",
                write(CANONICAL.replace(" center-y=\"-0.0\"", "")));
        assertProblem(
                "WORKSPACE_STRUCTURE_INVALID",
                "reason",
                "duplicate",
                write(
                        CANONICAL.replace(
                                "  <layers>",
                                "  <view map-crs=\"EPSG:4326\" display-crs=\"EPSG:4326\""
                                        + " center-x=\"0\" center-y=\"0\""
                                        + " units-per-pixel=\"1\"/>\n  <layers>")));
        assertProblem(
                "WORKSPACE_STRUCTURE_INVALID",
                "reason",
                "duplicate",
                write(
                        CANONICAL.replace(
                                "      <symbols",
                                "      <source opener=\"application.source.v1\" id=\"duplicate\""
                                        + " name=\"\" path=\"data/a\"/>\n      <symbols")));
        assertProblem(
                "WORKSPACE_FIELD_UNKNOWN",
                "field",
                "element",
                write(CANONICAL.replace("/>\n  <layers>", "><bogus/></view>\n  <layers>")));
        assertProblem(
                "WORKSPACE_STRUCTURE_INVALID",
                "reason",
                "text",
                write(CANONICAL.replace("<layers>", "<layers>text")));
    }

    @Test
    void rejectsNumericCrsPathIdentityAndDuplicateLayerValues() throws IOException {
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "centerX",
                write(CANONICAL.replace("center-x=\"0.0\"", "center-x=\"NaN\"")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "unitsPerPixel",
                write(CANONICAL.replace("units-per-pixel=\"1000.0\"", "units-per-pixel=\"0\"")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "mapCrs",
                write(CANONICAL.replace("map-crs=\"EPSG:4326\"", "map-crs=\"CRS:84\"")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "sourcePath",
                write(CANONICAL.replace("data/roads.shp", "../roads.shp")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "sourceOpener",
                write(CANONICAL.replace("application.shapefile.v1", "Java.Class")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "sourceId",
                write(CANONICAL.replace("id=\"roads-source\"", "id=\"\"")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "markerName",
                write(CANONICAL.replace("marker=\"point\"", "marker=\" point\"")));
        assertProblem(
                "WORKSPACE_VALUE_INVALID",
                "field",
                "layerId",
                write(CANONICAL.replace("id=\"image\"", "id=\"roads\"")));
    }

    @Test
    void enforcesInputOperationDepthLayerAndCharacterLimits() throws IOException {
        Path input = write(CANONICAL);
        long size = Files.size(input);
        long modelBytes =
                WorkspaceDocument.logicalModelBytes(
                        WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT).document().view(),
                        WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT).document().layers());
        long operationBytes =
                size + (Files.readString(input, StandardCharsets.UTF_8).length() * 2L) + modelBytes;
        long aggregateCharacters = canonicalAggregateCharacters();
        assertEquals(
                2,
                WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT.withInputOutputBytes(size))
                        .document()
                        .layers()
                        .size());
        assertLimitProblem(
                "inputBytes",
                size,
                size - 1,
                input,
                WorkspaceLimits.DEFAULT.withInputOutputBytes(size - 1));
        assertLimitProblem("layers", 2, 1, input, WorkspaceLimits.DEFAULT.withLayers(1));
        assertLimitProblem("depth", 4, 3, input, WorkspaceLimits.DEFAULT.withDepth(3));
        assertEquals(
                2,
                WorkspaceFiles.read(input, WorkspaceLimits.DEFAULT.withDepth(4))
                        .document()
                        .layers()
                        .size());
        assertEquals(
                2,
                WorkspaceFiles.read(
                                input,
                                WorkspaceLimits.DEFAULT
                                        .withLayers(2)
                                        .withElements(8)
                                        .withAttributes(25)
                                        .withValueChars(26))
                        .document()
                        .layers()
                        .size());
        assertLimitProblem(
                "elements", 8, 7, input, WorkspaceLimits.DEFAULT.withLayers(7).withElements(7));
        assertLimitProblem("attributes", 25, 24, input, WorkspaceLimits.DEFAULT.withAttributes(24));
        assertLimitProblem("valueChars", 26, 25, input, WorkspaceLimits.DEFAULT.withValueChars(25));
        assertEquals(
                2,
                WorkspaceFiles.read(
                                input,
                                WorkspaceLimits.DEFAULT
                                        .withValueChars(26)
                                        .withAggregateChars(aggregateCharacters))
                        .document()
                        .layers()
                        .size());
        assertLimitProblem(
                "aggregateChars",
                aggregateCharacters,
                aggregateCharacters - 1,
                input,
                WorkspaceLimits.DEFAULT
                        .withValueChars(26)
                        .withAggregateChars(aggregateCharacters - 1));
        assertEquals(
                2,
                WorkspaceFiles.read(
                                input,
                                WorkspaceLimits.DEFAULT
                                        .withInputOutputBytes(size)
                                        .withOperationBytes(operationBytes))
                        .document()
                        .layers()
                        .size());
        assertLimitProblem(
                "operationBytes",
                operationBytes,
                operationBytes - 1,
                input,
                WorkspaceLimits.DEFAULT
                        .withInputOutputBytes(size)
                        .withOperationBytes(operationBytes - 1));
    }

    @Test
    void reportsExactlyOneByteBeyondTheInputCeilingWhenTheSnapshotGrows() throws IOException {
        Path input = write(CANONICAL);
        long initialSize = Files.size(input);
        WorkspaceInputAccess growing =
                new DelegatingAccess() {
                    @Override
                    public SeekableByteChannel open(Path path) throws IOException {
                        Files.writeString(
                                path,
                                "growth-beyond-one-buffer",
                                StandardCharsets.UTF_8,
                                StandardOpenOption.APPEND);
                        return super.open(path);
                    }
                };

        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.read(
                                        input,
                                        WorkspaceLimits.DEFAULT.withInputOutputBytes(initialSize),
                                        growing));

        assertEquals(
                problem(
                        "WORKSPACE_LIMIT_EXCEEDED",
                        "limit",
                        "inputBytes",
                        "requested",
                        Long.toString(initialSize + 1),
                        "maximum",
                        Long.toString(initialSize)),
                failure.problem());
    }

    @Test
    void rejectsMissingWrongSuffixDirectoryAndSymbolicLink() throws IOException {
        assertProblem(
                "WORKSPACE_IO_FAILED", "reason", "missing", temporary.resolve("missing.mmap.xml"));
        assertProblem("WORKSPACE_IO_FAILED", "reason", "wrongKind", temporary.resolve("wrong.xml"));
        Path directory = temporary.resolve("directory.mmap.xml");
        Files.createDirectory(directory);
        assertProblem("WORKSPACE_IO_FAILED", "reason", "wrongKind", directory);

        Path target = write(CANONICAL);
        Path link = temporary.resolve("link.mmap.xml");
        Files.createSymbolicLink(link, target.getFileName());
        assertProblem("WORKSPACE_IO_FAILED", "reason", "symlink", link);
    }

    @Test
    void detectsSnapshotMutationAndPreservesReadFailureAcrossCloseCleanup() throws IOException {
        Path input = write(CANONICAL);
        AtomicInteger attributeCalls = new AtomicInteger();
        WorkspaceInputAccess mutation =
                new DelegatingAccess() {
                    @Override
                    public BasicFileAttributes attributes(Path path) throws IOException {
                        BasicFileAttributes actual = super.attributes(path);
                        return attributeCalls.getAndIncrement() == 0
                                ? actual
                                : new ChangedAttributes(actual);
                    }
                };
        assertProblem(
                "WORKSPACE_IO_FAILED",
                "reason",
                "changed",
                input,
                WorkspaceLimits.DEFAULT,
                mutation);

        WorkspaceInputAccess readAndCloseFailure =
                new DelegatingAccess() {
                    @Override
                    public SeekableByteChannel open(Path path) throws IOException {
                        return new FaultChannel(super.open(path), true);
                    }
                };
        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.read(
                                        input, WorkspaceLimits.DEFAULT, readAndCloseFailure));
        assertEquals("read", failure.problem().context().get("reason"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(
                "close",
                ((WorkspaceException) failure.getSuppressed()[0])
                        .problem()
                        .context()
                        .get("reason"));
    }

    @Test
    void mapsAttributeOpenCloseAndRealParentFailuresWithoutLeakingPaths() throws IOException {
        Path input = write(CANONICAL);
        WorkspaceInputAccess attributeFailure =
                new DelegatingAccess() {
                    @Override
                    public BasicFileAttributes attributes(Path path) throws IOException {
                        throw new IOException("injected attributes failure " + path);
                    }
                };
        assertProblem(
                "WORKSPACE_IO_FAILED",
                "reason",
                "size",
                input,
                WorkspaceLimits.DEFAULT,
                attributeFailure);

        WorkspaceInputAccess openFailure =
                new DelegatingAccess() {
                    @Override
                    public SeekableByteChannel open(Path path) throws IOException {
                        throw new IOException("injected open failure " + path);
                    }
                };
        assertProblem(
                "WORKSPACE_IO_FAILED",
                "reason",
                "open",
                input,
                WorkspaceLimits.DEFAULT,
                openFailure);

        WorkspaceInputAccess closeFailure =
                new DelegatingAccess() {
                    @Override
                    public SeekableByteChannel open(Path path) throws IOException {
                        return new FaultChannel(super.open(path), false);
                    }
                };
        assertProblem(
                "WORKSPACE_IO_FAILED",
                "reason",
                "close",
                input,
                WorkspaceLimits.DEFAULT,
                closeFailure);

        WorkspaceInputAccess parentFailure =
                new DelegatingAccess() {
                    @Override
                    public Path realParent(Path path) throws IOException {
                        throw new IOException("injected parent failure " + path);
                    }
                };
        assertProblem(
                "WORKSPACE_IO_FAILED",
                "reason",
                "open",
                input,
                WorkspaceLimits.DEFAULT,
                parentFailure);
    }

    private Path write(String xml) throws IOException {
        Path path = temporary.resolve("workspace-" + nextFile++ + ".mmap.xml");
        Files.writeString(path, xml, StandardCharsets.UTF_8);
        return path;
    }

    private static WorkspaceFeatureLayer feature(String id) {
        return new WorkspaceFeatureLayer(
                id,
                id,
                source(id),
                new WorkspaceSymbolReferences("application.symbols", "marker", "line", "fill"));
    }

    private static WorkspaceSourceReference source(String id) {
        return new WorkspaceSourceReference(
                "application.source.v1",
                new SourceIdentity(id, id),
                new WorkspaceRelativePath("data/" + id));
    }

    private static WorkspaceRasterLayer raster(int index, int pathCharacters) {
        String suffix = String.format(java.util.Locale.ROOT, "%04d", index);
        WorkspaceSourceReference source =
                new WorkspaceSourceReference(
                        "a.b",
                        new SourceIdentity("s" + suffix, ""),
                        new WorkspaceRelativePath("a".repeat(pathCharacters)));
        return new WorkspaceRasterLayer("l" + suffix, "", source, RasterInterpolation.NEAREST, 1.0);
    }

    private static long canonicalAggregateCharacters() {
        return List.of(
                        WorkspaceText.NAMESPACE,
                        "1",
                        "EPSG:4326",
                        "EPSG:3857",
                        "0.0",
                        "-0.0",
                        "1000.0",
                        "roads",
                        "Roads",
                        "application.shapefile.v1",
                        "roads-source",
                        "Road data",
                        "data/roads.shp",
                        "application.default",
                        "point",
                        "road",
                        "area",
                        "image",
                        "Image",
                        "BILINEAR",
                        "1.0",
                        "application.image.v1",
                        "image-source",
                        "Image data",
                        "data/image.png")
                .stream()
                .mapToLong(String::length)
                .sum();
    }

    private static WorkspaceProblem problem(String code, String... pairs) {
        var context = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            context.put(pairs[index], pairs[index + 1]);
        }
        return new WorkspaceProblem(code, context);
    }

    private static void assertProblem(String code, String key, String value, Path input) {
        assertProblem(code, key, value, input, WorkspaceLimits.DEFAULT);
    }

    private static void assertProblem(
            String code, String key, String value, Path input, WorkspaceLimits limits) {
        assertProblem(code, key, value, input, limits, WorkspaceInputAccess.JDK);
    }

    private static void assertProblem(
            String code,
            String key,
            String value,
            Path input,
            WorkspaceLimits limits,
            WorkspaceInputAccess access) {
        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class, () -> WorkspaceFiles.read(input, limits, access));
        assertEquals(code, failure.problem().code());
        assertEquals(value, failure.problem().context().get(key));
        assertFalse(failure.getMessage().contains(input.toString()));
    }

    private static void assertLimitProblem(
            String limit, long requested, long maximum, Path input, WorkspaceLimits limits) {
        WorkspaceException failure =
                assertThrows(WorkspaceException.class, () -> WorkspaceFiles.read(input, limits));
        assertEquals(
                problem(
                        "WORKSPACE_LIMIT_EXCEEDED",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                failure.problem());
    }

    private static void consume(WorkspaceLimits ignored) {}

    private static class DelegatingAccess implements WorkspaceInputAccess {
        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            return WorkspaceInputAccess.JDK.attributes(path);
        }

        @Override
        public SeekableByteChannel open(Path path) throws IOException {
            return WorkspaceInputAccess.JDK.open(path);
        }

        @Override
        public Path realParent(Path path) throws IOException {
            return WorkspaceInputAccess.JDK.realParent(path);
        }
    }

    private record ChangedAttributes(BasicFileAttributes delegate) implements BasicFileAttributes {
        @Override
        public FileTime lastModifiedTime() {
            return FileTime.fromMillis(delegate.lastModifiedTime().toMillis() + 1);
        }

        @Override
        public FileTime lastAccessTime() {
            return delegate.lastAccessTime();
        }

        @Override
        public FileTime creationTime() {
            return delegate.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return delegate.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return delegate.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return delegate.isOther();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public Object fileKey() {
            return delegate.fileKey();
        }
    }

    private static final class FaultChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private final boolean failRead;

        private FaultChannel(SeekableByteChannel delegate, boolean failRead) {
            this.delegate = delegate;
            this.failRead = failRead;
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            if (failRead) {
                throw new IOException("injected read failure");
            }
            return delegate.read(destination);
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long position) throws IOException {
            delegate.position(position);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            IOException delegateFailure = null;
            try {
                delegate.close();
            } catch (IOException failure) {
                delegateFailure = failure;
            }
            IOException injected = new IOException("injected close failure");
            if (delegateFailure != null) {
                injected.addSuppressed(delegateFailure);
            }
            throw injected;
        }
    }
}
