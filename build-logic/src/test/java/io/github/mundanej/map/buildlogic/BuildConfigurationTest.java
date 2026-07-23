package io.github.mundanej.map.buildlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildConfigurationTest {
    private static final Path ROOT = Path.of(System.getProperty("map.rootDir"));

    @TempDir Path temporaryDirectory;

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

        Path nonNormalized = temporaryDirectory.resolve("parent").resolve("..").resolve("repository");
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

    @Test
    void publishedApiDocumentationPolicyIsStrictAndOffline() throws Exception {
        String javaConvention =
                Files.readString(
                        ROOT.resolve(
                                "build-logic/src/main/groovy/"
                                        + "mundane-map.java-library-conventions.gradle"));
        assertTrue(!javaConvention.contains("options.links"));

        String publishingConvention =
                Files.readString(
                        ROOT.resolve(
                                "build-logic/src/main/groovy/"
                                        + "mundane-map.publishing-conventions.gradle"));
        assertTrue(publishingConvention.contains("options.addBooleanOption('Xdoclint:all', true)"));
        assertTrue(publishingConvention.contains("options.addBooleanOption('Werror', true)"));
        assertTrue(publishingConvention.contains("options.addBooleanOption('notimestamp', true)"));
        assertTrue(!publishingConvention.contains("options.links"));
        assertTrue(publishingConvention.contains("tasks.register('checkstylePublicApi', Checkstyle)"));

        String publicApiRules =
                Files.readString(ROOT.resolve("config/checkstyle/checkstyle-public-api.xml"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocPackage\"/>"));
        assertTrue(publicApiRules.contains("<module name=\"MissingJavadocType\">"));
        assertTrue(publicApiRules.contains("<module name=\"MissingJavadocMethod\">"));
    }

    @Test
    void offlineRepositoryMarksMetadataOnlyComponentsAsPomPackaging() throws Exception {
        Path metadataOnly = Files.createDirectory(temporaryDirectory.resolve("metadata-only"));
        AssembleOfflineRepository.writePom(
                metadataOnly,
                "example",
                "metadata-only",
                "1",
                Set.of("example:runtime:1"));
        String metadataPom = Files.readString(metadataOnly.resolve("metadata-only-1.pom"));
        assertTrue(metadataPom.contains("<packaging>pom</packaging>"));
        assertTrue(metadataPom.contains("<artifactId>runtime</artifactId>"));

        Path ordinaryJar = Files.createDirectory(temporaryDirectory.resolve("ordinary-jar"));
        Files.write(ordinaryJar.resolve("ordinary-jar-1.jar"), new byte[] {0});
        AssembleOfflineRepository.writePom(
                ordinaryJar, "example", "ordinary-jar", "1", Set.of());
        String jarPom = Files.readString(ordinaryJar.resolve("ordinary-jar-1.pom"));
        assertTrue(!jarPom.contains("<packaging>pom</packaging>"));
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
                        if (offline != null) println "OFFLINE_REPOSITORY=" + file(offline).toPath().toUri()
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
                                "files('" + ROOT.resolve("gradle/libs.versions.toml").toUri() + "')");
        Files.writeString(project.resolve("settings.gradle"), actualIncludedBuildSettings);
        Files.writeString(
                project.resolve("build.gradle"),
                """
                configurations { probe }
                dependencies { probe 'com.example:missing-artifact:1.0' }
                tasks.register('resolveProbe') { doLast { configurations.probe.files } }
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
                    versionCatalogs { libs { from(files('%s')) } }
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
                        ? "plugins { id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0' }"
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
}
