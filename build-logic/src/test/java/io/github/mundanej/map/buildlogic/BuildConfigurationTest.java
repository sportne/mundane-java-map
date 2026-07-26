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
        assertTrue(result.getOutput().contains("JACOCO_ENABLED=true"));
        assertTrue(result.getOutput().contains("TEST_FINALIZERS=:jacocoTestReport"));
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
    void supportedTestJdkDoesNotRepeatJava21CoverageReporting() throws Exception {
        Path project = createJavaConventionFixture();

        BuildResult result =
                pluginRunner(
                                project,
                                "printJavaBaseline",
                                "-Pmap.testJavaVersion=25")
                        .build();

        assertTrue(result.getOutput().contains("TEST_JAVA_VERSION=25"));
        assertTrue(result.getOutput().contains("JACOCO_ENABLED=false"));
        assertTrue(result.getOutput().contains("TEST_FINALIZERS="));
        assertTrue(!result.getOutput().contains("TEST_FINALIZERS=:jacocoTestReport"));
    }

    @Test
    void supportedJdkAggregateContainsOnlyNormalInventoryTests() throws Exception {
        String rootBuild = Files.readString(ROOT.resolve("build.gradle"));
        int start = rootBuild.indexOf("tasks.register('supportedJdkTests')");
        int end = rootBuild.indexOf("\ntasks.register(", start + 1);
        assertTrue(start >= 0);
        assertTrue(end > start);
        String task = rootBuild.substring(start, end);

        assertTrue(task.contains("gradle.includedBuild('build-logic').task(':test')"));
        assertTrue(task.contains("checkedProjects.collect { \"${it}:test\" }"));
        assertTrue(!task.contains(":check"));
        assertTrue(!task.contains("qualityGate"));
        assertTrue(!task.contains("jacoco"));
        assertTrue(!task.contains("javadoc"));
        assertTrue(!task.contains("spot"));

        String performanceBuild =
                Files.readString(
                        ROOT.resolve("modules/mundane-map-performance-tests/build.gradle"));
        assertTrue(!performanceBuild.contains("tasks.withType(Test).configureEach"));
        int oracleStart =
                performanceBuild.indexOf(
                        "def verifyBaselineOracle = tasks.register('verifyBaselineOracle', Test)");
        int oracleEnd = performanceBuild.indexOf("\n}", oracleStart);
        assertTrue(oracleStart >= 0);
        assertTrue(oracleEnd > oracleStart);
        assertTrue(
                performanceBuild
                        .substring(oracleStart, oracleEnd)
                        .contains("javaLauncher = java21Launcher"));
        assertTrue(performanceBuild.contains("def java21EvidenceContractTest"));
        assertTrue(performanceBuild.contains("includeTags 'java21-evidence'"));
        assertTrue(performanceBuild.contains("excludeTags 'java21-evidence'"));
        assertTrue(performanceBuild.contains("dependsOn java21EvidenceContractTest"));
        assertTrue(
                performanceBuild.contains(
                        "executionData.from(java21EvidenceExecutionData)"));
    }

    @Test
    void allMainSourceDocumentationPolicyIsStrictAndOffline() throws Exception {
        String javaConvention =
                Files.readString(
                        ROOT.resolve(
                                "build-logic/src/main/groovy/"
                                        + "mundane-map.java-library-conventions.gradle"));
        assertTrue(javaConvention.contains("options.addBooleanOption('Xdoclint:all', true)"));
        assertTrue(javaConvention.contains("options.addBooleanOption('Werror', true)"));
        assertTrue(javaConvention.contains("options.addBooleanOption('notimestamp', true)"));
        assertTrue(!javaConvention.contains("options.links"));
        assertTrue(javaConvention.contains("tasks.register('checkstylePublicApi', Checkstyle)"));
        assertTrue(javaConvention.contains("source = sourceSets.main.allJava"));

        String publishingConvention =
                Files.readString(
                        ROOT.resolve(
                                "build-logic/src/main/groovy/"
                                        + "mundane-map.publishing-conventions.gradle"));
        assertTrue(!publishingConvention.contains("options.links"));
        assertTrue(!publishingConvention.contains("tasks.withType(Javadoc)"));
        assertTrue(!publishingConvention.contains("tasks.register('checkstylePublicApi'"));

        String buildLogic = Files.readString(ROOT.resolve("build-logic/build.gradle"));
        assertTrue(buildLogic.contains("source = fileTree('src/main/java')"));
        assertTrue(buildLogic.contains("tasks.register('checkstylePublicApi', Checkstyle)"));
        assertTrue(buildLogic.contains("options.addBooleanOption('Xdoclint:all', true)"));
        assertTrue(buildLogic.contains("options.addBooleanOption('Werror', true)"));
        assertTrue(buildLogic.contains("options.addBooleanOption('notimestamp', true)"));

        String rootBuild = Files.readString(ROOT.resolve("build.gradle"));
        assertTrue(rootBuild.contains("tasks.register('javadocAll')"));
        assertTrue(rootBuild.contains("gradle.includedBuild('build-logic').task(':javadoc')"));
        assertTrue(rootBuild.contains("checkedProjects.collect { \"${it}:javadoc\" }"));
        assertTrue(rootBuild.contains("dependsOn 'javadocAll'"));

        String publicApiRules =
                Files.readString(ROOT.resolve("config/checkstyle/checkstyle-public-api.xml"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocPackage\"/>"));
        assertTrue(publicApiRules.contains("<module name=\"MissingJavadocType\">"));
        assertTrue(publicApiRules.contains("<module name=\"MissingJavadocMethod\">"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocType\">"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocMethod\">"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocVariable\">"));
        assertTrue(publicApiRules.contains("<module name=\"JavadocStyle\">"));
        assertTrue(publicApiRules.contains("<module name=\"MissingDeprecated\"/>"));
        assertTrue(publicApiRules.contains("<property name=\"skipAnnotations\" value=\"\"/>"));
        assertTrue(publicApiRules.contains("<property name=\"allowedAnnotations\" value=\"\"/>"));
        assertTrue(publicApiRules.contains("<property name=\"validateThrows\" value=\"true\"/>"));
        assertTrue(!publicApiRules.contains("excludeScope"));
        assertTrue(!publicApiRules.contains("Generated"));
    }

    @Test
    void publicApiInventoryRejectsEveryUndocumentedDeclarationKind() throws Exception {
        Path project = createJavaConventionFixture();
        Path config = Files.createDirectories(project.resolve("config/checkstyle"));
        Files.copy(
                ROOT.resolve("config/checkstyle/checkstyle-public-api.xml"),
                config.resolve("checkstyle-public-api.xml"));
        Path source = project.resolve("src/main/java/example");
        Files.writeString(
                source.resolve("package-info.java"),
                "/** Documented fixture package. */\npackage example;\n");
        String[][] mutations = {
            {"TypeMissing.java", "package example;\npublic class TypeMissing {}\n"},
            {
                "ConstructorMissing.java",
                """
                package example;
                /** Documented type. */
                public class ConstructorMissing {
                    public ConstructorMissing() {}
                }
                """
            },
            {
                "MethodMissing.java",
                """
                package example;
                /** Documented type. */
                public class MethodMissing {
                    public int method() {
                        return 1;
                    }
                }
                """
            },
            {
                "ProtectedMissing.java",
                """
                package example;
                /** Documented type. */
                public class ProtectedMissing {
                    protected void operation() {}
                }
                """
            },
            {
                "ProtectedTypeMissing.java",
                """
                package example;
                /** Documented enclosing type. */
                public class ProtectedTypeMissing {
                    protected static class Nested {}
                }
                """
            },
            {
                "FieldMissing.java",
                """
                package example;
                /** Documented type. */
                public final class FieldMissing {
                    public static final int VALUE = 1;
                    private FieldMissing() {}
                }
                """
            },
            {
                "ChoiceMissing.java",
                """
                package example;
                /** Documented enum. */
                public enum ChoiceMissing {
                    VALUE
                }
                """
            },
            {
                "AnnotationElementMissing.java",
                """
                package example;
                /** Documented annotation. */
                public @interface AnnotationElementMissing {
                    String value();
                }
                """
            },
            {
                "RecordComponentMissing.java",
                """
                package example;
                /** Documented record without its component tag. */
                public record RecordComponentMissing(String value) {}
                """
            },
            {
                "TypeParameterMissing.java",
                """
                package example;
                /** Documented generic type without its type-parameter tag. */
                public final class TypeParameterMissing<T> {}
                """
            },
            {
                "ParameterMissing.java",
                """
                package example;
                /** Documented type. */
                public final class ParameterMissing {
                    private ParameterMissing() {}
                    /**
                     * Returns its argument.
                     *
                     * @return the argument
                     */
                    public static int method(int value) {
                        return value;
                    }
                }
                """
            },
            {
                "ReturnMissing.java",
                """
                package example;
                /** Documented type. */
                public final class ReturnMissing {
                    private ReturnMissing() {}
                    /** Returns one without its return tag. */
                    public static int method() {
                        return 1;
                    }
                }
                """
            },
            {
                "EmptyMissing.java",
                """
                package example;
                /** */
                public final class EmptyMissing {}
                """
            },
            {
                "DeprecatedMissing.java",
                """
                package example;
                /** Legacy type without its deprecation tag. */
                @Deprecated
                public final class DeprecatedMissing {}
                """
            },
            {
                "CheckedThrowsMissing.java",
                """
                package example;
                import java.io.IOException;
                /** Documented type. */
                public final class CheckedThrowsMissing {
                    private CheckedThrowsMissing() {}
                    /**
                     * Reads one value.
                     *
                     * @param value value to parse
                     * @return parsed length
                     */
                    public static int read(String value) throws IOException {
                        throw new IOException("fixture");
                    }
                }
                """
            },
            {
                "UncheckedThrowsMissing.java",
                """
                package example;
                /** Documented type. */
                public final class UncheckedThrowsMissing {
                    private UncheckedThrowsMissing() {}
                    /**
                     * Parses one value.
                     *
                     * @param value value to parse
                     * @return parsed length
                     */
                    public static int parse(String value) {
                        if (value.isBlank()) {
                            throw new IllegalArgumentException("blank");
                        }
                        return value.length();
                    }
                }
                """
            },
            {
                "GeneratedMissing.java",
                """
                package example;
                import javax.annotation.processing.Generated;
                @Generated("fixture")
                public final class GeneratedMissing {}
                """
            }
        };
        for (String[] mutation : mutations) {
            Files.writeString(source.resolve(mutation[0]), mutation[1]);
        }
        Path missingPackage = Files.createDirectories(source.getParent().resolve("missingpackage"));
        Files.writeString(
                missingPackage.resolve("PackageMissing.java"),
                "package missingpackage;\npublic final class PackageMissing {}\n");

        UnexpectedBuildFailure failure =
                assertThrows(
                        UnexpectedBuildFailure.class,
                        () -> pluginRunner(project, "checkstylePublicApi").build());
        String output = failure.getMessage();
        assertTrue(output.contains("Missing package-info.java file. [JavadocPackage]"));
        assertViolation(output, "TypeMissing.java", "MissingJavadocType");
        assertViolation(output, "ConstructorMissing.java", "MissingJavadocMethod");
        assertViolation(output, "MethodMissing.java", "MissingJavadocMethod");
        assertViolation(output, "ProtectedMissing.java", "MissingJavadocMethod");
        assertViolation(output, "ProtectedTypeMissing.java", "MissingJavadocType");
        assertViolation(output, "FieldMissing.java", "JavadocVariable");
        assertViolation(output, "ChoiceMissing.java", "JavadocVariable");
        assertViolation(output, "AnnotationElementMissing.java", "MissingJavadocMethod");
        assertViolation(output, "RecordComponentMissing.java", "JavadocType");
        assertViolation(output, "TypeParameterMissing.java", "JavadocType");
        assertViolation(output, "ParameterMissing.java", "JavadocMethod");
        assertViolation(output, "ReturnMissing.java", "JavadocMethod");
        assertViolation(output, "EmptyMissing.java", "JavadocStyle");
        assertViolation(output, "DeprecatedMissing.java", "MissingDeprecated");
        assertViolation(output, "CheckedThrowsMissing.java", "JavadocMethod");
        assertViolation(output, "UncheckedThrowsMissing.java", "JavadocMethod");
        assertViolation(output, "GeneratedMissing.java", "MissingJavadocType");
    }

    private static void assertViolation(String output, String file, String rule) {
        assertTrue(
                output.lines()
                        .anyMatch(line -> line.contains(file) && line.contains("[" + rule + "]")),
                () -> "Missing " + rule + " mutation result for " + file + ":\n" + output);
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
                        println "DEPENDENCY_REPOSITORIES=" \
                                + gradle.settings.dependencyResolutionManagement.repositories*.name.join(',')
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
                        println "JACOCO_ENABLED=" + tasks.test.extensions.getByName('jacoco').enabled
                        println "TEST_FINALIZERS=" \
                                + tasks.test.finalizedBy.getDependencies(tasks.test)*.path.sort().join(',')
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
                    throw new GradleException(
                            'map.offlineRepo must be an absolute normalized path or file URI',
                            exception)
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
