package io.github.mundanej.map.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.performance.PerformanceEvidenceMain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildLogicTaskBehaviorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void standaloneCoverageVerifierRejectsIncompleteArgumentStructures() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VerifySourceFileCoverage.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VerifySourceFileCoverage.main(
                                new String[] {
                                    "project",
                                    "report.xml",
                                    "0.80",
                                    ":fixture",
                                    "report.csv",
                                    "report.md",
                                    "0",
                                    "0"
                                }));
    }

    @Test
    void offlineVerificationCopiesOnlyRepositorySourcesAndRunsIsolatedBuild()
            throws Exception {
        Path projectSource = Files.createDirectories(temporaryDirectory.resolve("source"));
        Files.writeString(projectSource.resolve("kept.txt"), "kept");
        Files.createDirectories(projectSource.resolve(".git"));
        Files.writeString(projectSource.resolve(".git/ignored.txt"), "ignored");
        Files.createDirectories(projectSource.resolve("build"));
        Files.writeString(projectSource.resolve("build/ignored.txt"), "ignored");
        Files.createDirectories(projectSource.resolve("gradle/wrapper"));
        Files.writeString(
                projectSource.resolve("gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip\n");
        Files.writeString(
                projectSource.resolve("gradlew"),
                "#!/usr/bin/env bash\nprintf 'BUILD SUCCESSFUL\\n'\n");
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path gradleHome = Files.createDirectory(temporaryDirectory.resolve("gradle-home"));
        Path wrapper =
                Files.createDirectories(
                        gradleHome.resolve("wrapper/dists/gradle-9.5.1-bin/hash"));
        Files.writeString(wrapper.resolve("gradle-9.5.1-bin.zip.ok"), "verified");
        Path scratch = temporaryDirectory.resolve("scratch");
        Files.createDirectories(scratch);
        Files.writeString(scratch.resolve("stale.txt"), "stale");

        OfflineBuildVerification task =
                task("offline", OfflineBuildVerification.class);
        task.getSourceDirectory().set(projectSource.toFile());
        task.getRepositoryDirectory().set(repository.toFile());
        task.getGradleUserHome().set(gradleHome.toFile());
        task.getScratchDirectory().set(scratch.toFile());
        task.getJavaHome().set(System.getProperty("java.home"));
        task.getJava21Home().set(System.getProperty("java.home"));

        task.verify();

        assertEquals("kept", Files.readString(scratch.resolve("project/kept.txt")));
        assertFalse(Files.exists(scratch.resolve("project/.git")));
        assertFalse(Files.exists(scratch.resolve("project/build")));
        assertFalse(Files.exists(scratch.resolve("stale.txt")));

        Files.writeString(
                projectSource.resolve("gradle/wrapper/gradle-wrapper.properties"),
                "distributionUrl=unsupported\n");
        GradleException failure = assertThrows(GradleException.class, task::verify);
        assertTrue(failure.getMessage().contains("Unsupported Gradle wrapper"));
    }

    @Test
    void performanceEvidenceStagesInputsRunsChildAndPublishesValidatedReports()
            throws Exception {
        Path fixture = temporaryDirectory.resolve("fixture.txt");
        Files.writeString(fixture, "evidence");
        Path output = temporaryDirectory.resolve("reports");
        RunPerformanceEvidence task = task("performance", RunPerformanceEvidence.class);
        Path testClasses =
                Path.of(
                        PerformanceEvidenceMain.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        task.getRuntimeClasspath().from(testClasses);
        task.getStagedInputFiles().from(fixture.toFile());
        task.getStagedInputs().set(Map.of("fixture.path", fixture.toString()));
        task.getJvmArguments().set(List.of("-Xmx64m"));
        task.getSystemProperties().set(Map.of("performanceScenario", "smoke"));
        task.getProgramArguments().set(List.of("complete"));
        task.getExpectedReports().set(List.of("performance.txt"));
        task.getAllowedScenarios().set(List.of("smoke"));
        task.getScratchPrefix().set("mundane-buildlogic-test-");
        task.getJavaExecutable()
                .set(Path.of(System.getProperty("java.home"), "bin", "java").toFile());
        task.getOutputDirectory().set(output.toFile());

        task.runEvidence();

        assertEquals("evidence:complete", Files.readString(output.resolve("performance.txt")));

        task.getSystemProperties().set(Map.of("performanceScenario", "unknown"));
        GradleException failure = assertThrows(GradleException.class, task::runEvidence);
        assertTrue(failure.getMessage().contains("one exact scenario"));
    }

    @Test
    void offlineRepositoryAssemblyCopiesArtifactsWritesMarkersAndRejectsMissingPlugins()
            throws Exception {
        Path cache = temporaryDirectory.resolve("cache");
        Path implementation = cache.resolve("example/implementation/1");
        Files.createDirectories(implementation.resolve("hash"));
        Files.writeString(
                implementation.resolve("hash/implementation-1.jar"),
                "artifact");
        Path coordinateFile = temporaryDirectory.resolve("coordinates.txt");
        Files.writeString(
                coordinateFile,
                "example:library:1|example:dependency:1\nexample:unlisted:1|\n");
        Files.createDirectories(cache.resolve("example/library/1/hash"));
        Files.writeString(cache.resolve("example/library/1/hash/library-1-shaded.jar"), "library");
        Files.createDirectories(cache.resolve("example/library/1/first-pom"));
        Files.createDirectories(cache.resolve("example/library/1/second-pom"));
        Files.writeString(
                cache.resolve("example/library/1/first-pom/library-1.pom"), "first source POM");
        Files.writeString(
                cache.resolve("example/library/1/second-pom/library-1.pom"),
                "conflicting source POM");
        Files.createDirectories(cache.resolve("example/unlisted/1/hash"));
        Files.writeString(
                cache.resolve("example/unlisted/1/hash/unlisted-1-shaded.jar"), "unlisted");
        Path output = temporaryDirectory.resolve("offline");
        AssembleOfflineRepository task =
                task("assemble", AssembleOfflineRepository.class);
        task.getCoordinates().set(List.of("example:dependency:1"));
        task.getCoordinateFiles().from(coordinateFile.toFile());
        task.getPluginMarkers()
                .set(List.of("example.plugin|example.plugin.gradle.plugin|1|example|implementation"));
        task.getShadedPrimaryAliases().set(List.of("example:library:1"));
        task.getArtifactCache().set(cache.toFile());
        task.getOutputDirectory().set(output.toFile());

        task.assemble();

        assertTrue(Files.isRegularFile(output.resolve("manifest.sha256")));
        assertTrue(
                Files.readString(
                                output.resolve(
                                        "example/plugin/example.plugin.gradle.plugin/1/"
                                                + "example.plugin.gradle.plugin-1.pom"))
                        .contains("implementation"));
        assertTrue(
                Files.readString(output.resolve("example/library/1/library-1.pom"))
                        .contains("<artifactId>dependency</artifactId>"));
        assertEquals("library", Files.readString(output.resolve("example/library/1/library-1.jar")));
        assertTrue(!Files.exists(output.resolve("example/unlisted/1/unlisted-1.jar")));

        task.getPluginMarkers()
                .set(List.of("missing.plugin|missing.plugin.gradle.plugin|1|missing|implementation"));
        GradleException failure = assertThrows(GradleException.class, task::assemble);
        assertTrue(failure.getMessage().contains("missing:implementation:1"));
    }

    private <T extends org.gradle.api.Task> T task(String name, Class<T> type) {
        java.io.File projectDirectory = temporaryDirectory.resolve(name).toFile();
        assertTrue(projectDirectory.mkdirs());
        Project project =
                ProjectBuilder.builder()
                        .withProjectDir(projectDirectory)
                        .build();
        return project.getTasks().create(name, type);
    }
}
