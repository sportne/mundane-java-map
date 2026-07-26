package io.github.mundanej.map.buildlogic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
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

    @Test
    void completeRepositoryVerificationChecksPayloadsAndWritesSortedManifest() throws Exception {
        PublicationArchives archives = createArchives(true, true);
        Path version = temporaryDirectory.resolve("repository/io/github/mundanej/fixture/1");
        Files.createDirectories(version);
        Path binary = Files.copy(archives.binary(), version.resolve("fixture-1.jar"));
        Path sources = Files.copy(archives.sources(), version.resolve("fixture-1-sources.jar"));
        Path javadocs = Files.copy(archives.javadocs(), version.resolve("fixture-1-javadoc.jar"));
        Path pom = version.resolve("fixture-1.pom");
        Files.writeString(
                pom,
                """
                <project>
                  <groupId>io.github.mundanej</groupId>
                  <artifactId>fixture</artifactId>
                  <version>1</version>
                  <licenses><license><name>BSD 3-Clause License</name></license></licenses>
                </project>
                """);
        Path metadata = version.resolve("fixture-1.module");
        Files.writeString(metadata, "{}");
        for (Path payload : new Path[] {binary, sources, javadocs, pom, metadata}) {
            Files.writeString(
                    payload.resolveSibling(payload.getFileName() + ".sha256"),
                    sha256(payload));
        }
        Path license = temporaryDirectory.resolve("LICENSE");
        Files.writeString(license, "fixture license\n");
        Path projectDirectory = Files.createDirectory(temporaryDirectory.resolve("project"));
        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build();
        VerifyPublicationRepository task =
                project.getTasks()
                        .create("verifyRepository", VerifyPublicationRepository.class);
        task.getSurfaceClasspath().from();
        task.getRepositoryDirectory()
                .set(temporaryDirectory.resolve("repository").toFile());
        task.getReleaseContract().set(List.of("fixture||||" + PACKAGE_ROOT));
        task.getPublicationVersion().set("1");
        task.getLicenseFile().set(license.toFile());
        Path manifest = temporaryDirectory.resolve("artifact-manifest.txt");
        task.getArtifactManifest().set(manifest.toFile());

        task.verify();

        assertTrue(Files.readString(manifest).contains("fixture-1.jar"));
    }

    private PublicationArchives createArchives(boolean includeSource, boolean includeProtectedDoc)
            throws Exception {
        Map<String, byte[]> binaryEntries = new LinkedHashMap<>();
        binaryEntries.put("META-INF/LICENSE", "fixture license\n".getBytes());
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
                                ? Map.of(
                                        FIXTURE + ".java",
                                        "fixture".getBytes(),
                                        "META-INF/LICENSE",
                                        "fixture license\n".getBytes())
                                : Map.of("META-INF/LICENSE", "fixture license\n".getBytes()));
        Map<String, byte[]> javadocEntries = new LinkedHashMap<>();
        javadocEntries.put("META-INF/LICENSE", "fixture license\n".getBytes());
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

    private static String sha256(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private record PublicationArchives(Path binary, Path sources, Path javadocs) {}
}
