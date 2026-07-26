package io.github.mundanej.map.buildlogic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyPublicationRepositoryTest {
    private static final String PACKAGE_ROOT = "io/github/mundanej/map/buildlogic/";
    private static final String FIXTURE = PACKAGE_ROOT + "PublicationSurfaceFixture";

    @TempDir Path temporaryDirectory;

    @Test
    void matchingBinarySourceAndJavadocSurfacesPass() throws Exception {
        PublicationArchives archives = createArchives(true, true);

        assertDoesNotThrow(
                () ->
                        VerifyPublicationRepository.verifyDocumentedSurface(
                                archives.binary(),
                                archives.sources(),
                                archives.javadocs(),
                                PACKAGE_ROOT));
    }

    @Test
    void missingSourceOrVisibleTypeJavadocFails() throws Exception {
        PublicationArchives missingSource = createArchives(false, true);
        GradleException sourceFailure =
                assertThrows(
                        GradleException.class,
                        () ->
                                VerifyPublicationRepository.verifyDocumentedSurface(
                                        missingSource.binary(),
                                        missingSource.sources(),
                                        missingSource.javadocs(),
                                        PACKAGE_ROOT));
        assertTrue(sourceFailure.getMessage().contains("Source surface mismatch"));

        PublicationArchives missingJavadoc = createArchives(true, false);
        GradleException javadocFailure =
                assertThrows(
                        GradleException.class,
                        () ->
                                VerifyPublicationRepository.verifyDocumentedSurface(
                                        missingJavadoc.binary(),
                                        missingJavadoc.sources(),
                                        missingJavadoc.javadocs(),
                                        PACKAGE_ROOT));
        assertTrue(javadocFailure.getMessage().contains("Javadoc surface mismatch"));
        assertTrue(javadocFailure.getMessage().contains("PublicationSurfaceFixture.ProtectedNested"));
    }

    private PublicationArchives createArchives(boolean includeSource, boolean includeProtectedDoc)
            throws Exception {
        Map<String, byte[]> binaryEntries = new LinkedHashMap<>();
        for (Class<?> type :
                new Class<?>[] {
                    PublicationSurfaceFixture.class,
                    PublicationSurfaceFixture.PublicNested.class,
                    PublicationSurfaceFixture.ProtectedNested.class,
                    PublicationSurfaceFixture.PackageNested.class,
                    Class.forName(PublicationSurfaceFixture.class.getName() + "$PrivateNested")
                }) {
            String entry = type.getName().replace('.', '/') + ".class";
            try (InputStream input = type.getClassLoader().getResourceAsStream(entry)) {
                if (input == null) {
                    throw new IOException("Missing fixture class " + entry);
                }
                binaryEntries.put(entry, input.readAllBytes());
            }
        }
        Path binary = writeArchive("fixture.jar", binaryEntries);
        Path sources =
                writeArchive(
                        "fixture-sources.jar",
                        includeSource
                                ? Map.of(FIXTURE + ".java", "fixture".getBytes())
                                : Map.of());
        Map<String, byte[]> javadocEntries = new LinkedHashMap<>();
        javadocEntries.put(FIXTURE + ".html", "outer".getBytes());
        javadocEntries.put(FIXTURE + ".PublicNested.html", "public".getBytes());
        if (includeProtectedDoc) {
            javadocEntries.put(FIXTURE + ".ProtectedNested.html", "protected".getBytes());
        }
        Path javadocs = writeArchive("fixture-javadoc.jar", javadocEntries);
        return new PublicationArchives(binary, sources, javadocs);
    }

    private Path writeArchive(String name, Map<String, byte[]> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return archive;
    }

    private record PublicationArchives(Path binary, Path sources, Path javadocs) {}
}
