package io.github.mundanej.map.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildConfigurationTest {
    private static final Path ROOT = Path.of(System.getProperty("map.rootDir"));
    private static final Path GRADLE_USER_HOME = Path.of(System.getProperty("map.gradleUserHome"));
    private static final Path JAVA_21_HOME = Path.of(System.getProperty("map.java21Home"));
    private static final String TEST_JAVA_VERSION = System.getProperty("map.testJavaVersion");

    @TempDir Path temporaryDirectory;

    @Test
    void actualBuildPassesQualityGateWithOnlyAnIsolatedOfflineRepository() throws Exception {
        Assumptions.assumeFalse(
                "true".equals(System.getenv("MAP_OFFLINE_VERIFICATION_CHILD")),
                "the isolated child must not recursively launch another isolated child");
        Path provisioningProject = temporaryDirectory.resolve("provisioning-project");
        copyProjectSources(ROOT, provisioningProject);
        provisionActualBuildDependencies(provisioningProject);

        Path repository = temporaryDirectory.resolve("offline-repository");
        createVerifiedMavenRepository(repository);

        Path project = temporaryDirectory.resolve("actual-project");
        copyProjectSources(ROOT, project);
        Path isolatedGradleHome = temporaryDirectory.resolve("gradle-home");
        preprovisionVerifiedWrapper(isolatedGradleHome);

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                                "bash",
                                project.resolve("gradlew").toString(),
                                "qualityGate",
                                "--console=plain",
                                "--offline",
                                "--no-daemon",
                                "-Pmap.offlineRepo=" + repository,
                                "-Dorg.gradle.java.installations.auto-download=false",
                                "-Dorg.gradle.java.installations.auto-detect=false",
                                "-Dorg.gradle.java.installations.paths="
                                        + JAVA_21_HOME
                                        + ","
                                        + System.getProperty("java.home"),
                                "-Pmap.testJavaVersion=" + TEST_JAVA_VERSION)
                        .directory(project.toFile())
                        .redirectErrorStream(true);
        processBuilder.environment().put("GRADLE_USER_HOME", isolatedGradleHome.toString());
        processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        processBuilder.environment().put("MAP_OFFLINE_VERIFICATION_CHILD", "true");
        assertBuildPasses(processBuilder, "isolated offline build");
    }

    private static void provisionActualBuildDependencies(Path project) throws Exception {
        ProcessBuilder processBuilder =
                new ProcessBuilder(
                                "bash",
                                project.resolve("gradlew").toString(),
                                "qualityGate",
                                "--console=plain",
                                "--no-daemon",
                                "-Pmap.testJavaVersion=" + TEST_JAVA_VERSION)
                        .directory(project.toFile())
                        .redirectErrorStream(true);
        processBuilder.environment().put("GRADLE_USER_HOME", GRADLE_USER_HOME.toString());
        processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        processBuilder.environment().put("MAP_OFFLINE_VERIFICATION_CHILD", "true");
        assertBuildPasses(processBuilder, "dependency-provisioning build");
    }

    private static void assertBuildPasses(ProcessBuilder processBuilder, String description)
            throws Exception {
        Process process = processBuilder.start();
        CompletableFuture<String> output =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return new String(
                                        process.getInputStream().readAllBytes(),
                                        StandardCharsets.UTF_8);
                            } catch (IOException exception) {
                                throw new IllegalStateException(exception);
                            }
                        });
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }
        String buildOutput = output.get(30, TimeUnit.SECONDS);
        assertTrue(finished, () -> description + " timed out:\n" + buildOutput);
        assertEquals(0, process.exitValue(), () -> description + " failed:\n" + buildOutput);
        assertTrue(buildOutput.contains("BUILD SUCCESSFUL"), buildOutput);
    }

    @Test
    void normalRepositoryModeSelectsOnlyPublicRepositories() throws Exception {
        Path project = createRepositoryFixture();

        BuildResult result = runner(project, "repositoryPolicy").build();

        assertTrue(
                result.getOutput()
                        .contains(
                                "PLUGIN_REPOSITORIES=Gradle Central Plugin Repository,MavenRepo"));
        assertTrue(result.getOutput().contains("DEPENDENCY_REPOSITORIES=MavenRepo"));
    }

    @Test
    void absoluteOfflineRepositoryIsTheOnlyRepository() throws Exception {
        Path project = createRepositoryFixture();
        Path repository = Files.createDirectory(temporaryDirectory.resolve("offline-repository"));

        BuildResult result =
                runner(project, "repositoryPolicy", "-Pmap.offlineRepo=" + repository).build();

        assertTrue(result.getOutput().contains("PLUGIN_REPOSITORIES=offline"));
        assertTrue(result.getOutput().contains("DEPENDENCY_REPOSITORIES=offline"));
        assertTrue(result.getOutput().contains("OFFLINE_REPOSITORY=" + repository.toUri()));
    }

    @Test
    void fileUriOfflineRepositoryIsAccepted() throws Exception {
        Path project = createRepositoryFixture();
        Path repository = Files.createDirectory(temporaryDirectory.resolve("offline-file-uri"));

        BuildResult result =
                runner(project, "repositoryPolicy", "-Pmap.offlineRepo=" + repository.toUri())
                        .build();

        assertTrue(result.getOutput().contains("PLUGIN_REPOSITORIES=offline"));
        assertTrue(result.getOutput().contains("DEPENDENCY_REPOSITORIES=offline"));
    }

    @Test
    void offlineRepositoryRejectsRelativeBlankAndNonNormalizedValues() throws Exception {
        Path project = createRepositoryFixture();

        UnexpectedBuildFailure relative =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () ->
                                runner(project, "help", "-Pmap.offlineRepo=relative/repository")
                                        .build());
        assertTrue(relative.getMessage().contains("absolute normalized path or file URI"));

        UnexpectedBuildFailure blank =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () -> runner(project, "help", "-Pmap.offlineRepo= ").build());
        assertTrue(blank.getMessage().contains("map.offlineRepo must not be blank"));

        Path nonNormalized =
                temporaryDirectory.resolve("parent").resolve("..").resolve("repository");
        UnexpectedBuildFailure normalized =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () ->
                                runner(project, "help", "-Pmap.offlineRepo=" + nonNormalized)
                                        .build());
        assertTrue(normalized.getMessage().contains("absolute normalized path or file URI"));
    }

    @Test
    void missingOfflinePluginNamesCoordinateAndRepository() throws Exception {
        Path project = createPluginResolutionFixture();
        Path repository = Files.createDirectory(temporaryDirectory.resolve("missing-plugin"));

        UnexpectedBuildFailure failure =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () ->
                                runner(
                                                project,
                                                "help",
                                                "-Pmap.offlineRepo=" + repository,
                                                "--offline")
                                        .build());

        assertTrue(
                failure.getMessage().contains("org.gradle.toolchains.foojay-resolver-convention"));
        assertTrue(failure.getMessage().contains(repository.toUri().toString()));
        assertTrue(!failure.getMessage().contains("Gradle Central Plugin Repository"));
    }

    @Test
    void missingOfflineDependencyNamesCoordinateAndRepository() throws Exception {
        Path project = createDependencyResolutionFixture();
        Path repository = Files.createDirectory(temporaryDirectory.resolve("missing-dependency"));

        UnexpectedBuildFailure failure =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () ->
                                runner(
                                                project,
                                                "resolveProbe",
                                                "-Pmap.offlineRepo=" + repository,
                                                "--offline")
                                        .build());

        assertTrue(failure.getMessage().contains("com.example:missing-artifact:1.0"));
        assertTrue(failure.getMessage().contains(repository.toUri().toString()));
        assertTrue(!failure.getMessage().contains("MavenRepo"));
    }

    @Test
    void javaConventionPinsReleaseAndRejectsOverride() throws Exception {
        Path project = createJavaConventionFixture();

        BuildResult result = pluginRunner(project, "compileJava", "printJavaBaseline").build();
        assertTrue(result.getOutput().contains("JAVA_RELEASE=21"));
        assertTrue(result.getOutput().contains("TEST_JAVA_VERSION=21"));
        byte[] classBytes =
                Files.readAllBytes(project.resolve("build/classes/java/main/example/Sample.class"));
        int majorVersion = ((classBytes[6] & 0xff) << 8) | (classBytes[7] & 0xff);
        assertEquals(65, majorVersion);

        UnexpectedBuildFailure failure =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () -> pluginRunner(project, "help", "-Pmap.javaRelease=25").build());
        assertTrue(failure.getMessage().contains("map.javaRelease is fixed at 21; received 25"));
    }

    private Path createRepositoryFixture() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("repository-fixture"));
        Files.writeString(project.resolve("settings.gradle"), repositorySettings(false));
        Files.writeString(
                project.resolve("build.gradle"),
                """
                tasks.register('repositoryPolicy') {
                    doLast {
                        println "PLUGIN_REPOSITORIES=" + gradle.settings.pluginManagement.repositories*.name.join(',')
                        println "DEPENDENCY_REPOSITORIES=" + gradle.settings.dependencyResolutionManagement.repositories*.name.join(',')
                        def offline = providers.gradleProperty('map.offlineRepo').orNull
                        if (offline != null) {
                            println "OFFLINE_REPOSITORY=" + file(offline).toPath().toUri()
                        }
                    }
                }
                """);
        return project;
    }

    private Path createPluginResolutionFixture() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("plugin-fixture"));
        Files.writeString(project.resolve("settings.gradle"), repositorySettings(true));
        Files.writeString(project.resolve("build.gradle"), "");
        return project;
    }

    private Path createDependencyResolutionFixture() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("dependency-fixture"));
        String actualIncludedBuildSettings =
                Files.readString(ROOT.resolve("build-logic/settings.gradle"))
                        .replace(
                                "files('../gradle/libs.versions.toml')",
                                "files('"
                                        + ROOT.resolve("gradle/libs.versions.toml").toUri()
                                        + "')");
        Files.writeString(project.resolve("settings.gradle"), actualIncludedBuildSettings);
        Files.writeString(
                project.resolve("build.gradle"),
                """
                configurations { probe }
                dependencies { probe 'com.example:missing-artifact:1.0' }
                tasks.register('resolveProbe') {
                    doLast { configurations.probe.files }
                }
                """);
        return project;
    }

    private Path createJavaConventionFixture() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("java-fixture"));
        Files.writeString(
                project.resolve("settings.gradle"),
                """
                dependencyResolutionManagement {
                    repositories { mavenCentral() }
                    versionCatalogs {
                        libs { from(files('%s')) }
                    }
                }
                rootProject.name = 'java-fixture'
                """
                        .formatted(ROOT.resolve("gradle/libs.versions.toml").toUri()));
        Files.writeString(
                project.resolve("build.gradle"),
                """
                plugins { id 'mundane-map.java-library-conventions' }
                tasks.register('printJavaBaseline') {
                    doLast {
                        println "JAVA_RELEASE=" + tasks.compileJava.options.release.get()
                        println "TEST_JAVA_VERSION=" + tasks.test.javaLauncher.get().metadata.languageVersion
                    }
                }
                """);
        Path source = Files.createDirectories(project.resolve("src/main/java/example"));
        Files.writeString(
                source.resolve("Sample.java"),
                "package example;\n\npublic final class Sample { private Sample() {} }\n");
        return project;
    }

    private static String repositorySettings(boolean applyFoojayPlugin) {
        String pluginBlock =
                applyFoojayPlugin
                        ? "plugins { id 'org.gradle.toolchains.foojay-resolver-convention' version"
                                + " '1.0.0' }"
                        : "";
        return """
        pluginManagement {
            String configured = providers.gradleProperty('map.offlineRepo').orNull
            URI offline = null
            if (configured != null) {
                if (configured.isBlank()) throw new GradleException('map.offlineRepo must not be blank')
                java.nio.file.Path path
                try {
                    if (configured.startsWith('file:')) {
                        URI uri = URI.create(configured)
                        if (uri.scheme != 'file' || uri.query != null || uri.fragment != null) {
                            throw new IllegalArgumentException('unsupported file URI')
                        }
                        path = java.nio.file.Path.of(uri)
                    } else {
                        path = java.nio.file.Path.of(configured)
                    }
                } catch (IllegalArgumentException exception) {
                    throw new GradleException('map.offlineRepo must be an absolute normalized path or file URI', exception)
                }
                if (!path.isAbsolute() || path != path.normalize()) {
                    throw new GradleException('map.offlineRepo must be an absolute normalized path or file URI')
                }
                offline = path.toUri()
            }
            repositories {
                if (offline != null) { maven { name = 'offline'; url = offline } }
                else { gradlePluginPortal(); mavenCentral() }
            }
        }
        %s
        def offline = offlineRepositoryUri(providers)
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                if (offline != null) { maven { name = 'offline'; url = offline } }
                else { mavenCentral() }
            }
        }
        rootProject.name = 'repository-fixture'

        static URI offlineRepositoryUri(org.gradle.api.provider.ProviderFactory providers) {
            String configured = providers.gradleProperty('map.offlineRepo').orNull
            if (configured == null) return null
            if (configured.isBlank()) throw new GradleException('map.offlineRepo must not be blank')
            java.nio.file.Path path
            try {
                if (configured.startsWith('file:')) {
                    URI uri = URI.create(configured)
                    if (uri.scheme != 'file' || uri.query != null || uri.fragment != null) {
                        throw new IllegalArgumentException('unsupported file URI')
                    }
                    path = java.nio.file.Path.of(uri)
                } else {
                    path = java.nio.file.Path.of(configured)
                }
            } catch (IllegalArgumentException exception) {
                throw new GradleException('map.offlineRepo must be an absolute normalized path or file URI', exception)
            }
            if (!path.isAbsolute() || path != path.normalize()) {
                throw new GradleException('map.offlineRepo must be an absolute normalized path or file URI')
            }
            return path.toUri()
        }
        """
                .formatted(pluginBlock);
    }

    private static GradleRunner runner(Path project, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments(arguments)
                .forwardOutput();
    }

    private static GradleRunner pluginRunner(Path project, String... arguments) {
        return runner(project, arguments).withPluginClasspath();
    }

    private static void copyProjectSources(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (containsExcludedProjectSegment(relative)) {
                    continue;
                }
                Path target = destination.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(
                            path,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static boolean containsExcludedProjectSegment(Path relative) {
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.equals(".git") || name.equals(".gradle") || name.equals("build")) {
                return true;
            }
        }
        return false;
    }

    private static void createVerifiedMavenRepository(Path repository) throws Exception {
        Path cache = GRADLE_USER_HOME.resolve("caches/modules-2/files-2.1");
        assertTrue(Files.isDirectory(cache), "Gradle artifact cache is unavailable: " + cache);
        try (var paths = Files.walk(cache)) {
            for (Path artifact : paths.filter(Files::isRegularFile).toList()) {
                Path relative = cache.relativize(artifact);
                if (relative.getNameCount() != 5) {
                    continue;
                }
                String expectedSha1 = relative.getName(3).toString();
                assertEquals(
                        expectedSha1,
                        sha1(artifact).replaceFirst("^0+(?!$)", ""),
                        "unverified cached artifact " + artifact);
                Path target =
                        repository
                                .resolve(relative.getName(0).toString().replace('.', '/'))
                                .resolve(relative.getName(1))
                                .resolve(relative.getName(2))
                                .resolve(relative.getName(4));
                Files.createDirectories(target.getParent());
                Files.copy(artifact, target, StandardCopyOption.REPLACE_EXISTING);
                if (artifact.getFileName().toString().endsWith(".jar")) {
                    String group = relative.getName(0).toString();
                    String module = relative.getName(1).toString();
                    String version = relative.getName(2).toString();
                    Path pom = target.getParent().resolve(module + "-" + version + ".pom");
                    if (!Files.exists(pom)) {
                        Files.writeString(
                                pom,
                                """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>%s</groupId>
                                  <artifactId>%s</artifactId>
                                  <version>%s</version>
                                </project>
                                """
                                        .formatted(group, module, version));
                    }
                }
            }
        }
        Path marker =
                repository.resolve(
                        "org/gradle/toolchains/foojay-resolver-convention/"
                            + "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/"
                            + "1.0.0/"
                            + "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom");
        Files.createDirectories(marker.getParent());
        Files.writeString(
                marker,
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.gradle.toolchains.foojay-resolver-convention</groupId>
                  <artifactId>org.gradle.toolchains.foojay-resolver-convention.gradle.plugin</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencies><dependency>
                    <groupId>org.gradle.toolchains</groupId>
                    <artifactId>foojay-resolver</artifactId>
                    <version>1.0.0</version>
                  </dependency></dependencies>
                </project>
                """);
        Path pluginPom =
                repository.resolve(
                        "org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.pom");
        Files.createDirectories(pluginPom.getParent());
        Files.writeString(
                pluginPom,
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.gradle.toolchains</groupId>
                  <artifactId>foojay-resolver</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
    }

    private static void preprovisionVerifiedWrapper(Path isolatedGradleHome) throws IOException {
        String properties =
                Files.readString(ROOT.resolve("gradle/wrapper/gradle-wrapper.properties"));
        assertTrue(
                properties.contains(
                        "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip"));
        assertTrue(
                properties.contains(
                        "distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"));
        Path source = GRADLE_USER_HOME.resolve("wrapper/dists/gradle-9.5.1-bin");
        assertTrue(Files.isDirectory(source), "preprovisioned Gradle 9.5.1 is unavailable");
        try (var paths =
                Files.find(source, 2, (path, attributes) -> path.toString().endsWith(".zip.ok"))) {
            assertTrue(
                    paths.findAny().isPresent(),
                    "the wrapper distribution has no checksum-success marker");
        }
        copyTree(source, isolatedGradleHome.resolve("wrapper/dists/gradle-9.5.1-bin"));
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(
                            path,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static String sha1(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(path)));
    }
}
