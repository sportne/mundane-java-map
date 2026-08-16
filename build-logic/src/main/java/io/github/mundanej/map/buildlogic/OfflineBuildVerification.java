package io.github.mundanej.map.buildlogic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Runs the quality gate from copied sources, an empty Gradle home, and one local repository. */
@DisableCachingByDefault(because = "Executes an isolated child build")
public abstract class OfflineBuildVerification extends DefaultTask {
    /** Creates a task instance whose properties Gradle configures before execution. */
    @Inject
    public OfflineBuildVerification() {}

    /**
     * Provides the project source tree copied into the isolated scratch directory.
     *
     * @return project source tree
     */
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /**
     * Provides the sole Maven repository made visible to the isolated build.
     *
     * @return isolated Maven repository
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRepositoryDirectory();

    /**
     * Provides the frozen frontend installation prepared from the committed lockfile.
     *
     * @return prepared frontend modules
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFrontendModulesDirectory();

    /**
     * Provides the exact Node.js installation used by the Flow production build.
     *
     * @return prepared Node.js installation
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getNodeInstallationDirectory();

    /**
     * Provides the ordinary Gradle home from which only the verified wrapper is copied.
     *
     * @return ordinary Gradle home
     */
    @Internal
    public abstract DirectoryProperty getGradleUserHome();

    /**
     * Provides the Java home used to launch the isolated Gradle process.
     *
     * @return launcher Java home
     */
    @Input
    public abstract Property<String> getJavaHome();

    /**
     * Provides the Java 21 toolchain installation exposed to the isolated build.
     *
     * @return Java 21 home
     */
    @Input
    public abstract Property<String> getJava21Home();

    /**
     * Provides the directory recreated for the copied project and empty Gradle home.
     *
     * @return isolated scratch directory
     */
    @Internal
    public abstract DirectoryProperty getScratchDirectory();

    @TaskAction
    void verify() throws Exception {
        Path scratch = getScratchDirectory().get().getAsFile().toPath();
        deleteTree(scratch);
        Path project = scratch.resolve("project");
        Path isolatedHome = scratch.resolve("gradle-home");
        Path isolatedUserHome = scratch.resolve("user-home");
        copyProject(getSourceDirectory().get().getAsFile().toPath(), project);
        copyTree(
                getFrontendModulesDirectory().get().getAsFile().toPath(),
                project.resolve("examples/vaadin-viewer/node_modules"));
        copyTree(
                getNodeInstallationDirectory().get().getAsFile().toPath(),
                isolatedUserHome.resolve(".vaadin/node-v24.14.0"));
        copyWrapper(getGradleUserHome().get().getAsFile().toPath(), isolatedHome, project);

        runBuild(
                project,
                isolatedHome,
                isolatedUserHome,
                List.of(
                        ":examples:vaadin-viewer:vaadinBuildFrontend",
                        "-Pvaadin.productionMode",
                        "-Pmap.offlineFrontend"),
                "production frontend");
        runBuild(
                project,
                isolatedHome,
                isolatedUserHome,
                List.of("qualityGate"),
                "quality");
    }

    private void runBuild(
            Path project,
            Path isolatedHome,
            Path isolatedUserHome,
            List<String> requestedArguments,
            String description)
            throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add("bash");
        command.add(project.resolve("gradlew").toString());
        command.addAll(requestedArguments);
        command.add("--console=plain");
        command.add("--offline");
        command.add("--no-daemon");
        command.add(
                "-Pmap.offlineRepo="
                        + getRepositoryDirectory().get().getAsFile().getAbsolutePath());
        command.add("-Dorg.gradle.java.installations.auto-download=false");
        command.add("-Dorg.gradle.java.installations.auto-detect=false");
        command.add(
                "-Dorg.gradle.java.installations.paths="
                        + getJava21Home().get()
                        + ","
                        + getJavaHome().get());
        var process =
                new ProcessBuilder(command)
                        .directory(project.toFile())
                        .redirectErrorStream(true);
        process.environment().put("GRADLE_USER_HOME", isolatedHome.toString());
        process.environment().put("HOME", isolatedUserHome.toString());
        process.environment().put("JAVA_HOME", getJavaHome().get());
        process.environment().put("npm_config_offline", "true");
        process.environment().put("npm_config_audit", "false");
        process.environment().put("npm_config_fund", "false");
        process.environment().put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        Process child = process.start();
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> transfer(child, capture));
        boolean finished = child.waitFor(25, TimeUnit.MINUTES);
        if (!finished) {
            child.destroyForcibly();
        }
        reader.join();
        String output = capture.toString(StandardCharsets.UTF_8);
        if (!finished || child.exitValue() != 0 || !output.contains("BUILD SUCCESSFUL")) {
            throw new GradleException(
                    "Isolated offline " + description + " build failed:\n" + tail(output));
        }
    }

    private static void transfer(Process process, ByteArrayOutputStream capture) {
        try {
            process.getInputStream().transferTo(capture);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void copyProject(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (excluded(relative)) {
                    continue;
                }
                copy(path, destination.resolve(relative));
            }
        }
    }

    private static boolean excluded(Path relative) {
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.equals(".git")
                    || name.equals(".gradle")
                    || name.equals("build")
                    || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private static void copyWrapper(Path sourceHome, Path destinationHome, Path project)
            throws IOException {
        String properties =
                Files.readString(project.resolve("gradle/wrapper/gradle-wrapper.properties"));
        var match =
                java.util.regex.Pattern.compile("distributionUrl=.*?/(gradle-[^/]+-bin)\\.zip")
                        .matcher(properties);
        if (!match.find()) {
            throw new GradleException("Unsupported Gradle wrapper distribution URL");
        }
        String distributionName = match.group(1);
        Path distribution = sourceHome.resolve("wrapper/dists").resolve(distributionName);
        try (var candidates =
                Files.find(
                        distribution,
                        3,
                        (path, attributes) -> path.getFileName().toString().endsWith(".zip.ok"))) {
            Path marker =
                    candidates
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new GradleException(
                                                    "No verified Gradle wrapper distribution is cached"));
            copyTree(
                    distribution,
                    destinationHome.resolve("wrapper/dists").resolve(distributionName));
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                copy(path, destination.resolve(source.relativize(path)));
            }
        }
    }

    private static void copy(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(destination);
        } else {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
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

    private static String tail(String output) {
        int limit = 16 * 1024;
        return output.length() <= limit ? output : output.substring(output.length() - limit);
    }
}
