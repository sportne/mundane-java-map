package io.github.mundanej.map.buildlogic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
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
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRepositoryDirectory();

    @Internal
    public abstract DirectoryProperty getGradleUserHome();

    @Input
    public abstract Property<String> getJavaHome();

    @Input
    public abstract Property<String> getJava21Home();

    @Internal
    public abstract DirectoryProperty getScratchDirectory();

    @TaskAction
    void verify() throws Exception {
        Path scratch = getScratchDirectory().get().getAsFile().toPath();
        deleteTree(scratch);
        Path project = scratch.resolve("project");
        Path isolatedHome = scratch.resolve("gradle-home");
        copyProject(getSourceDirectory().get().getAsFile().toPath(), project);
        copyWrapper(getGradleUserHome().get().getAsFile().toPath(), isolatedHome, project);

        var process =
                new ProcessBuilder(
                                "bash",
                                project.resolve("gradlew").toString(),
                                "qualityGate",
                                "--console=plain",
                                "--offline",
                                "--no-daemon",
                                "-Pmap.offlineRepo="
                                        + getRepositoryDirectory()
                                                .get()
                                                .getAsFile()
                                                .getAbsolutePath(),
                                "-Dorg.gradle.java.installations.auto-download=false",
                                "-Dorg.gradle.java.installations.auto-detect=false",
                                "-Dorg.gradle.java.installations.paths="
                                        + getJava21Home().get()
                                        + ","
                                        + getJavaHome().get())
                        .directory(project.toFile())
                        .redirectErrorStream(true);
        process.environment().put("GRADLE_USER_HOME", isolatedHome.toString());
        process.environment().put("JAVA_HOME", getJavaHome().get());
        Process child = process.start();
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> transfer(child, capture));
        boolean finished = child.waitFor(15, TimeUnit.MINUTES);
        if (!finished) {
            child.destroyForcibly();
        }
        reader.join();
        String output = capture.toString(StandardCharsets.UTF_8);
        if (!finished || child.exitValue() != 0 || !output.contains("BUILD SUCCESSFUL")) {
            throw new GradleException(
                    "Isolated offline quality build failed:\n" + tail(output));
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
            if (name.equals(".git") || name.equals(".gradle") || name.equals("build")) {
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
