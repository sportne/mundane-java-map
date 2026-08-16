package io.github.mundanej.map.architecture;

import static io.github.mundanej.map.architecture.ApiSignatureSnapshots.ChangeKind.ADDITION;
import static io.github.mundanej.map.architecture.ApiSignatureSnapshots.ChangeKind.BREAKING;
import static io.github.mundanej.map.architecture.ApiSignatureSnapshots.ChangeKind.NONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.architecture.ApiSignatureSnapshots.SemanticVersion;
import io.github.mundanej.map.architecture.ApiSignatureSnapshots.VersionPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.base.BaseReporter;
import org.revapi.java.JavaApiAnalyzer;

@SuppressWarnings("try")
class ReleasedApiGovernanceTest {
    @TempDir Path scratch;

    @Test
    void everyPublishedArtifactMatchesItsReviewedBaseline() throws Exception {
        ApiSignatureSnapshots.verifyAll();
    }

    @Test
    void revapiPinsBinaryAndSourceClassificationForSyntheticJars() throws Exception {
        Path oldJar =
                compileJar(
                        "old",
                        "fixture.Sample",
                        """
                        package fixture;
                        public class Sample {
                            public Number retained() { return 1; }
                            public void removed() { }
                        }
                        """);
        Path incompatibleJar =
                compileJar(
                        "incompatible",
                        "fixture.Sample",
                        """
                        package fixture;
                        public final class Sample {
                            public Integer retained() { return 1; }
                        }
                        """);
        List<Difference> incompatible = analyze(oldJar, incompatibleJar);
        Set<String> codes =
                incompatible.stream()
                        .map(difference -> difference.code)
                        .collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.contains("java.method.removed"), codes::toString);
        assertTrue(codes.contains("java.class.nowFinal"), codes::toString);
        assertTrue(
                incompatible.stream()
                        .anyMatch(
                                difference ->
                                        difference.classification.values().stream()
                                                .anyMatch(
                                                        severity ->
                                                                severity
                                                                        == DifferenceSeverity
                                                                                .BREAKING)),
                incompatible::toString);

        Path compatibleBaseline =
                compileJar(
                        "compatible-baseline",
                        "fixture.Sample",
                        """
                        package fixture;
                        public class Sample {
                            public Number retained() { return 1; }
                        }
                        """);
        Path compatibleJar =
                compileJar(
                        "compatible",
                        "fixture.Sample",
                        """
                        package fixture;
                        public class Sample {
                            public Number retained() { return 1; }
                            public static class Added { }
                        }
                        """);
        List<Difference> compatible = analyze(compatibleBaseline, compatibleJar);
        assertFalse(
                compatible.stream()
                        .filter(difference -> difference.code.equals("java.class.added"))
                        .flatMap(difference -> difference.classification.values().stream())
                        .anyMatch(severity -> severity == DifferenceSeverity.BREAKING),
                compatible::toString);
    }

    @Test
    void stricterLanguageShapeMatrixRequiresReviewForEveryNamedConstruct() {
        for (StrictShapeChange change : EnumSet.allOf(StrictShapeChange.class)) {
            assertEquals(BREAKING, change.classification(), change.name());
        }
        assertEquals(
                Set.of(
                        StrictShapeChange.ENUM_CONSTANT_ADDED,
                        StrictShapeChange.SEALED_SUBTYPE_ADDED,
                        StrictShapeChange.RECORD_COMPONENT_CHANGED,
                        StrictShapeChange.OVERLOAD_AMBIGUITY,
                        StrictShapeChange.GENERIC_SIGNATURE_CHANGED,
                        StrictShapeChange.CHECKED_EXCEPTION_WIDENED,
                        StrictShapeChange.NULLNESS_NARROWED,
                        StrictShapeChange.ANNOTATION_CHANGED,
                        StrictShapeChange.CONSTANT_CHANGED,
                        StrictShapeChange.LEAKED_PUBLIC_TYPE),
                EnumSet.allOf(StrictShapeChange.class));
    }

    @Test
    void signatureClassifierDistinguishesNoChangeAdditionAndRemoval() {
        assertEquals(NONE, ApiSignatureSnapshots.classify("class A\n", "class A\n"));
        assertEquals(
                ADDITION, ApiSignatureSnapshots.classify("class A\n", "class A\nclass Added\n"));
        assertEquals(
                BREAKING,
                ApiSignatureSnapshots.classify("class A\nmethod old\n", "class A\nmethod new\n"));
    }

    @Test
    void twoPhaseSemverPolicyRejectsInsufficientAndReusedVersions() {
        SemanticVersion pre = SemanticVersion.parse("0.1.0");
        assertFalse(
                VersionPolicy.sufficient(pre, SemanticVersion.parse("0.1.0-SNAPSHOT"), ADDITION));
        assertTrue(
                VersionPolicy.sufficient(pre, SemanticVersion.parse("0.1.1-SNAPSHOT"), ADDITION));
        assertFalse(VersionPolicy.sufficient(pre, SemanticVersion.parse("0.1.1"), BREAKING));
        assertTrue(
                VersionPolicy.sufficient(pre, SemanticVersion.parse("0.2.0-SNAPSHOT"), BREAKING));
        assertFalse(
                VersionPolicy.sufficient(
                        SemanticVersion.parse("1.4.2"), SemanticVersion.parse("1.5.0"), BREAKING));
        assertTrue(
                VersionPolicy.sufficient(
                        SemanticVersion.parse("1.4.2"), SemanticVersion.parse("2.0.0"), BREAKING));
        assertTrue(
                VersionPolicy.sufficient(
                        SemanticVersion.parse("1.4.2"), SemanticVersion.parse("1.5.0"), ADDITION));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("01.0.0"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.0.0-rc1"));
    }

    @Test
    void exceptionDeclarationsAreExactCompleteAndExpiring() {
        String valid =
                "artifact\tdifferenceCode\telement\texpiresAfter\trationale\tmigration\treleaseNote\tapproval\n"
                        + "mundane-map-api\tjava.method.removed\tio.example.Type#m()\t0.2.0\t"
                        + "critical correctness fix\tuse n()\tdocs/releases/0.2.0.md#api\tmaintainer-2026-08-16\n";
        assertDoesNotThrow(
                () ->
                        ApiSignatureSnapshots.validateExceptions(
                                valid, SemanticVersion.parse("0.1.0")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApiSignatureSnapshots.validateExceptions(
                                valid.replace("io.example.Type#m()", "*"),
                                SemanticVersion.parse("0.1.0")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApiSignatureSnapshots.validateExceptions(
                                valid.replace("0.2.0", "0.1.0"), SemanticVersion.parse("0.1.0")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApiSignatureSnapshots.validateExceptions(
                                valid.replace("use n()", ""), SemanticVersion.parse("0.1.0")));
    }

    @Test
    void manifestsRejectMissingCorruptSubstitutedAndFalseReleaseProvenance() throws Exception {
        Path missing = scratch.resolve("missing.properties");
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiSignatureSnapshots.parseManifest(missing, "mundane-map-api"));
        Path manifest = scratch.resolve("mundane-map-api.properties");
        Files.writeString(
                manifest,
                "artifact=mundane-map-api\n"
                        + "candidateVersion=0.1.0-SNAPSHOT\n"
                        + "coordinate=io.github.mundanej:mundane-map-api:0.1.0\n"
                        + "format=public-protected-java21-v1\n"
                        + "jarSha256=UNPUBLISHED\n"
                        + "kind=PROVISIONAL\n"
                        + "moduleMetadataSha256=UNPUBLISHED\n"
                        + "pomSha256=UNPUBLISHED\n"
                        + "provenance=reviewed-source-snapshot:G19-190\n"
                        + "signature=mundane-map-api.sig\n"
                        + "signatureSha256="
                        + "0".repeat(64)
                        + "\n",
                StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiSignatureSnapshots.parseManifest(manifest, "mundane-map-api"));
        Files.writeString(
                manifest,
                Files.readString(manifest)
                        .replace("kind=PROVISIONAL", "kind=RELEASE")
                        .replace("jarSha256=UNPUBLISHED", "jarSha256=bad"),
                StandardCharsets.UTF_8);
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiSignatureSnapshots.parseManifest(manifest, "mundane-map-api"));
    }

    private List<Difference> analyze(Path oldJar, Path newJar) throws Exception {
        CollectingReporter.begin();
        var revapi =
                org.revapi.Revapi.builder()
                        .withAnalyzers(JavaApiAnalyzer.class)
                        .withReporters(CollectingReporter.class)
                        .build();
        AnalysisContext context =
                AnalysisContext.builder(revapi)
                        .withOldAPI(API.of(new PathArchive(oldJar)).build())
                        .withNewAPI(API.of(new PathArchive(newJar)).build())
                        .build();
        try (var result = revapi.analyze(context)) {
            result.throwIfFailed();
        }
        return CollectingReporter.finish();
    }

    private Path compileJar(String name, String className, String source) throws IOException {
        Path sourceRoot = scratch.resolve(name + "-source");
        Path classes = scratch.resolve(name + "-classes");
        Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(Objects.requireNonNull(sourceFile.getParent()));
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        int result =
                Objects.requireNonNull(ToolProvider.getSystemJavaCompiler())
                        .run(
                                null,
                                null,
                                null,
                                "--release",
                                "21",
                                "-d",
                                classes.toString(),
                                sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("synthetic API fixture compilation failed");
        }
        Path jar = scratch.resolve(name + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                JarEntry entry =
                        new JarEntry(classes.relativize(path).toString().replace('\\', '/'));
                entry.setTime(0L);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private enum StrictShapeChange {
        ENUM_CONSTANT_ADDED,
        SEALED_SUBTYPE_ADDED,
        RECORD_COMPONENT_CHANGED,
        OVERLOAD_AMBIGUITY,
        GENERIC_SIGNATURE_CHANGED,
        CHECKED_EXCEPTION_WIDENED,
        NULLNESS_NARROWED,
        ANNOTATION_CHANGED,
        CONSTANT_CHANGED,
        LEAKED_PUBLIC_TYPE;

        ApiSignatureSnapshots.ChangeKind classification() {
            return BREAKING;
        }
    }

    private record PathArchive(Path path) implements Archive {
        @Override
        public String getName() {
            return Objects.requireNonNull(path.getFileName()).toString();
        }

        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(path);
        }
    }

    /**
     * Revapi creates reporters reflectively, so the fixture collector uses scoped thread-local
     * state.
     */
    public static final class CollectingReporter extends BaseReporter {
        private static final ThreadLocal<List<Difference>> DIFFERENCES = new ThreadLocal<>();

        static void begin() {
            DIFFERENCES.set(new ArrayList<>());
        }

        static List<Difference> finish() {
            List<Difference> result = List.copyOf(DIFFERENCES.get());
            DIFFERENCES.remove();
            return result;
        }

        @Override
        public String getExtensionId() {
            return "mundane-map-test-reporter";
        }

        @Override
        public Reader getJSONSchema() {
            return null;
        }

        @Override
        public void report(Report report) {
            DIFFERENCES.get().addAll(report.getDifferences());
        }
    }
}
