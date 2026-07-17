package io.github.mundanej.map.io.dted.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtedCorpusIntegrationTest {
    private static final Path ROOT =
            Path.of(System.getProperty("mundane.map.dted.corpus.root"))
                    .toAbsolutePath()
                    .normalize();

    @TempDir Path temporaryDirectory;

    @Test
    void opensVerifiedFilesThroughThePublicDefaultsAndReleasesEveryHandle() throws Exception {
        DtedCorpusManifest manifest = DtedCorpusManifest.loadAndVerify(ROOT);
        for (DtedCorpusManifest.Row row : manifest.datasets()) {
            DtedCorpusExpectations.Expectation expectation =
                    DtedCorpusExpectations.get(row.expectationId());
            Path workspace = temporaryDirectory.resolve(row.datasetId());
            Files.createDirectories(workspace);
            Path file = workspace.resolve(row.originalFilename());
            Files.copy(manifest.root().resolve(row.filePath()), file);

            try (ElevationSource source =
                    DtedFiles.open(
                            new SourceIdentity(row.datasetId(), row.datasetId()),
                            file,
                            DtedOpenOptions.defaults())) {
                assertEquals(expectation.columns(), source.metadata().columnCount());
                assertEquals(expectation.rows(), source.metadata().rowCount());
                assertEquals(new Envelope(-1, -81, 0, -80), source.metadata().sampleBounds());
                assertEquals(
                        "EPSG:4326", source.metadata().crs().canonicalIdentifier().orElseThrow());
                assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
                assertEquals(1.0 / (expectation.columns() - 1), source.metadata().columnSpacing());
                assertEquals(1.0 / (expectation.rows() - 1), source.metadata().rowSpacing());
                assertEquals(-1.0, source.metadata().sampleCoordinate(0, 0).x());
                assertEquals(-80.0, source.metadata().sampleCoordinate(0, 0).y());
                assertEquals(
                        0.0,
                        source.metadata()
                                .sampleCoordinate(expectation.columns() - 1, expectation.rows() - 1)
                                .x());
                assertEquals(
                        -81.0,
                        source.metadata()
                                .sampleCoordinate(expectation.columns() - 1, expectation.rows() - 1)
                                .y());
                assertTrue(source.openingDiagnostics().entries().isEmpty());
                assertEquals(0, source.openingDiagnostics().omittedWarningCount());
                for (DtedCorpusExpectations.Post post : expectation.finitePosts()) {
                    assertEquals(
                            post.value(),
                            source.sample(post.column(), post.row()).orElseThrow(),
                            row.datasetId() + " post " + post.column() + ',' + post.row());
                }
                for (DtedCorpusExpectations.Index post : expectation.voidPosts()) {
                    assertTrue(
                            source.sample(post.column(), post.row()).isEmpty(),
                            row.datasetId() + " void " + post.column() + ',' + post.row());
                }
            }
            deleteTree(workspace);
            assertFalse(Files.exists(workspace), "DTED source retained a file handle");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
