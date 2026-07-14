package io.github.mundanej.map.io.shapefile.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.io.shapefile.corpus.CorpusExpectations.ExpectedDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusIntegrationTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("shapefile-corpus", "Shapefile corpus");
    private static final Path CORPUS_ROOT = corpusRoot();

    @TempDir Path temporaryDirectory;

    @Test
    void everyManifestDatasetHasItsExactPublicOutcome() throws Exception {
        CorpusManifest manifest = CorpusManifest.loadAndVerify(CORPUS_ROOT);
        for (CorpusManifest.Dataset dataset : manifest.datasets()) {
            CorpusExpectations.Expectation expectation =
                    CorpusExpectations.expectation(dataset.expectationId());
            Path copy = copyDataset(manifest, dataset, "exact");
            try {
                ActualOutcome actual = execute(copy, expectation);
                assertExpected(expectation, actual, dataset.id());
            } finally {
                deleteTree(parent(copy));
            }
            assertFalse(Files.exists(parent(copy)), dataset.id() + " copy must be deletable");
        }
    }

    @Test
    void validIndexesAgreeWithSequentialAccessAndCorruptIndexMatchesItsParent() throws Exception {
        CorpusManifest manifest = CorpusManifest.loadAndVerify(CORPUS_ROOT);
        for (CorpusManifest.Dataset dataset : manifest.datasets()) {
            CorpusExpectations.Expectation expectation =
                    CorpusExpectations.expectation(dataset.expectationId());
            if (!dataset.tags().contains("index-valid") || expectation.success().isEmpty()) {
                continue;
            }
            Path indexed = copyDataset(manifest, dataset, "indexed");
            Path sequential = copyDataset(manifest, dataset, "sequential");
            Files.delete(sidecar(sequential, "shx"));
            try {
                ActualOutcome indexedOutcome = execute(indexed, expectation);
                ActualOutcome sequentialOutcome = execute(sequential, expectation);
                assertEquals(
                        indexedOutcome.withOpeningDiagnostics(List.of()),
                        sequentialOutcome.withOpeningDiagnostics(List.of()),
                        dataset.id() + " indexed/sequential values");
                List<ExpectedDiagnostic> expectedSequential = new ArrayList<>();
                expectedSequential.add(missingShx());
                expectedSequential.addAll(indexedOutcome.openingDiagnostics());
                assertEquals(
                        expectedSequential,
                        sequentialOutcome.openingDiagnostics(),
                        dataset.id() + " sequential opening diagnostics");
            } finally {
                deleteTree(parent(indexed));
                deleteTree(parent(sequential));
            }
        }

        CorpusManifest.Dataset corrupt = manifest.dataset("generated-corrupt-shx");
        CorpusManifest.Dataset parentDataset = manifest.dataset(corrupt.parentId());
        CorpusExpectations.Expectation corruptExpectation =
                CorpusExpectations.expectation(corrupt.expectationId());
        CorpusExpectations.Expectation parentExpectation =
                CorpusExpectations.expectation(parentDataset.expectationId());
        Path corruptCopy = copyDataset(manifest, corrupt, "corrupt");
        Path parentCopy = copyDataset(manifest, parentDataset, "parent");
        Files.delete(sidecar(parentCopy, "shx"));
        try {
            ActualOutcome corruptOutcome = execute(corruptCopy, corruptExpectation);
            ActualOutcome parentOutcome = execute(parentCopy, parentExpectation);
            assertEquals(
                    corruptOutcome.withOpeningDiagnostics(List.of()),
                    parentOutcome.withOpeningDiagnostics(List.of()),
                    "corrupt SHX fallback must equal its clean sequential parent");
            assertEquals(List.of(ignoredShx()), corruptOutcome.openingDiagnostics());
            assertEquals(List.of(missingShx()), parentOutcome.openingDiagnostics());
        } finally {
            deleteTree(parent(corruptCopy));
            deleteTree(parent(parentCopy));
        }
    }

    private Path copyDataset(CorpusManifest manifest, CorpusManifest.Dataset dataset, String suffix)
            throws IOException {
        Path directory = temporaryDirectory.resolve(dataset.id() + '-' + suffix);
        Files.createDirectory(directory);
        for (CorpusManifest.Row row : dataset.rows()) {
            Path source = manifest.root().resolve(row.componentPath());
            Path target =
                    directory.resolve(Objects.requireNonNull(source.getFileName()).toString());
            Files.copy(source, target);
            assertEquals(row.byteLength(), Files.size(target), row.componentPath());
            assertEquals(row.sha256(), CorpusTestSupport.sha256(target), row.componentPath());
        }
        return directory.resolve(dataset.id() + ".shp");
    }

    private static ActualOutcome execute(Path shp, CorpusExpectations.Expectation expectation) {
        FeatureSource source;
        try {
            source = Shapefiles.open(IDENTITY, shp, expectation.options());
        } catch (SourceException failure) {
            return ActualOutcome.failure("open", failure.report());
        }
        try (source) {
            FeatureSourceMetadata metadata = source.metadata();
            DiagnosticReport opening = source.openingDiagnostics();
            List<FeatureRecord> records = new ArrayList<>();
            try (FeatureCursor cursor =
                    source.openCursor(
                            new FeatureQuery(
                                    Optional.empty(), AttributeSelection.ALL, Optional.empty()),
                            CancellationToken.none())) {
                try {
                    while (cursor.advance()) {
                        records.add(cursor.current());
                    }
                } catch (SourceException failure) {
                    return ActualOutcome.failure(
                            metadata, records, opening, "cursor", failure.report());
                }
                return ActualOutcome.success(metadata, records, opening, cursor.diagnostics());
            } catch (SourceException failure) {
                return ActualOutcome.failure("cursor-open", failure.report());
            }
        }
    }

    private static void assertExpected(
            CorpusExpectations.Expectation expectation, ActualOutcome actual, String dataset) {
        if (expectation.failure().isPresent()) {
            CorpusExpectations.ExpectedFailure expected = expectation.failure().orElseThrow();
            assertEquals(expected.phase(), actual.failurePhase(), dataset + " failure phase");
            assertFalse(actual.failureDiagnostics().isEmpty(), dataset + " failure report");
            assertEquals(
                    expected.diagnostics(),
                    actual.failureDiagnostics(),
                    dataset + " failure diagnostics");
            assertEquals(
                    expected.openingDiagnostics(),
                    actual.openingDiagnostics(),
                    dataset + " failure opening diagnostics");
            assertEquals(
                    expected.openingOmittedWarningCount(),
                    actual.openingOmittedWarningCount(),
                    dataset + " failure opening omitted warnings");
            assertEquals(
                    expected.omittedWarningCount(),
                    actual.failureOmittedWarningCount(),
                    dataset + " failure omitted warnings");
            assertTrue(actual.records().isEmpty(), dataset + " has no partial published records");
            return;
        }
        CorpusExpectations.ExpectedSuccess expected = expectation.success().orElseThrow();
        assertTrue(actual.failurePhase().isEmpty(), dataset + " unexpectedly failed");
        FeatureSourceMetadata metadata = actual.metadata();
        assertEquals(Optional.of(expected.extent()), metadata.extent(), dataset + " extent");
        assertTrue(metadata.featureCount().isEmpty(), dataset + " feature count is absent");
        assertEquals(Optional.of(expected.schema()), metadata.schema(), dataset + " schema");
        if (expected.canonicalCrs() == null) {
            assertTrue(metadata.crs().isEmpty(), dataset + " CRS is absent");
        } else {
            var crs = metadata.crs().orElseThrow();
            assertEquals(
                    Optional.of(expected.canonicalCrs()),
                    crs.canonicalIdentifier(),
                    dataset + " CRS");
            assertEquals(
                    Optional.of(expected.retainedCrs()),
                    crs.retainedDefinition(),
                    dataset + " retained PRJ");
        }
        assertEquals(expected.records(), actual.records(), dataset + " records");
        assertEquals(
                expected.openingDiagnostics(),
                actual.openingDiagnostics(),
                dataset + " opening diagnostics");
        assertEquals(
                expected.openingOmittedWarningCount(),
                actual.openingOmittedWarningCount(),
                dataset + " opening omitted warnings");
        assertEquals(
                expected.cursorDiagnostics(),
                actual.cursorDiagnostics(),
                dataset + " cursor diagnostics");
        assertEquals(
                expected.cursorOmittedWarningCount(),
                actual.cursorOmittedWarningCount(),
                dataset + " cursor omitted warnings");
    }

    private static List<ExpectedDiagnostic> normalize(DiagnosticReport report) {
        return report.entries().stream().map(CorpusIntegrationTest::normalize).toList();
    }

    private static ExpectedDiagnostic normalize(SourceDiagnostic diagnostic) {
        DiagnosticLocation location = diagnostic.location().orElse(DiagnosticLocation.empty());
        return new ExpectedDiagnostic(
                diagnostic.code(),
                diagnostic.severity().name(),
                location.component().orElse(""),
                location.recordNumber().orElse(-1),
                location.partIndex().orElse(-1),
                location.fieldIndex().orElse(-1),
                location.fieldName().orElse(""),
                location.byteOffset().orElse(-1),
                diagnostic.context());
    }

    private static ExpectedDiagnostic missingShx() {
        return new ExpectedDiagnostic(
                "SHAPEFILE_SHX_MISSING", "WARNING", "shx", -1, -1, -1, "", -1, Map.of());
    }

    private static ExpectedDiagnostic ignoredShx() {
        return new ExpectedDiagnostic(
                "SHAPEFILE_SHX_IGNORED",
                "WARNING",
                "shx",
                -1,
                -1,
                -1,
                "",
                0,
                Map.of("reason", "header"));
    }

    private static Path sidecar(Path shp, String extension) {
        String name = Objects.requireNonNull(shp.getFileName()).toString();
        return shp.resolveSibling(name.substring(0, name.length() - 3) + extension);
    }

    private static Path parent(Path path) {
        return Objects.requireNonNull(path.getParent());
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Path corpusRoot() {
        String value = System.getProperty("mundane.map.shapefile.corpus.root");
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError("Missing fixed corpus source root");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    record ActualOutcome(
            FeatureSourceMetadata metadata,
            List<FeatureRecord> records,
            List<ExpectedDiagnostic> openingDiagnostics,
            long openingOmittedWarningCount,
            List<ExpectedDiagnostic> cursorDiagnostics,
            long cursorOmittedWarningCount,
            String failurePhase,
            List<ExpectedDiagnostic> failureDiagnostics,
            long failureOmittedWarningCount) {
        static ActualOutcome success(
                FeatureSourceMetadata metadata,
                List<FeatureRecord> records,
                DiagnosticReport opening,
                DiagnosticReport cursor) {
            return new ActualOutcome(
                    metadata,
                    List.copyOf(records),
                    normalize(opening),
                    opening.omittedWarningCount(),
                    normalize(cursor),
                    cursor.omittedWarningCount(),
                    "",
                    List.of(),
                    0);
        }

        static ActualOutcome failure(String phase, DiagnosticReport diagnostics) {
            return new ActualOutcome(
                    null,
                    List.of(),
                    List.of(),
                    0,
                    List.of(),
                    0,
                    phase,
                    normalize(diagnostics),
                    diagnostics.omittedWarningCount());
        }

        static ActualOutcome failure(
                FeatureSourceMetadata metadata,
                List<FeatureRecord> records,
                DiagnosticReport opening,
                String phase,
                DiagnosticReport diagnostics) {
            return new ActualOutcome(
                    metadata,
                    List.copyOf(records),
                    normalize(opening),
                    opening.omittedWarningCount(),
                    List.of(),
                    0,
                    phase,
                    normalize(diagnostics),
                    diagnostics.omittedWarningCount());
        }

        ActualOutcome withOpeningDiagnostics(List<ExpectedDiagnostic> diagnostics) {
            return new ActualOutcome(
                    metadata,
                    records,
                    diagnostics,
                    openingOmittedWarningCount,
                    cursorDiagnostics,
                    cursorOmittedWarningCount,
                    failurePhase,
                    failureDiagnostics,
                    failureOmittedWarningCount);
        }
    }
}
