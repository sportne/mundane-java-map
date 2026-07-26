package io.github.mundanej.map.buildlogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Runs a performance evidence process entirely from an invocation-specific /tmp directory. */
@DisableCachingByDefault(because = "Performance observations are intentionally rerun")
public abstract class RunPerformanceEvidence extends DefaultTask {
    /** Creates a task instance whose properties Gradle configures before execution. */
    @Inject
    public RunPerformanceEvidence() {}

    /**
     * Provides the runtime classpath copied into the invocation-specific directory.
     *
     * @return child-process runtime classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * Provides fixture files staged for the child evidence process.
     *
     * @return staged fixture files
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getStagedInputFiles();

    /**
     * Provides system-property names mapped to staged fixture paths.
     *
     * @return staged-input property mapping
     */
    @Input
    public abstract MapProperty<String, String> getStagedInputs();

    /**
     * Provides the ordered JVM arguments for the child process.
     *
     * @return ordered JVM arguments
     */
    @Input
    public abstract ListProperty<String> getJvmArguments();

    /**
     * Provides deterministic system properties for the child process.
     *
     * @return child-process system properties
     */
    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    /**
     * Provides ordered application arguments for the evidence entry point.
     *
     * @return ordered application arguments
     */
    @Input
    public abstract ListProperty<String> getProgramArguments();

    /**
     * Provides report names that must be emitted before output is accepted.
     *
     * @return expected report names
     */
    @Input
    public abstract ListProperty<String> getExpectedReports();

    /**
     * Provides scenario identifiers permitted for a selected-scenario invocation.
     *
     * @return allowed scenario identifiers
     */
    @Input
    public abstract ListProperty<String> getAllowedScenarios();

    /**
     * Provides the safe prefix used for the invocation-specific directory under {@code /tmp}.
     *
     * @return temporary-directory prefix
     */
    @Input
    public abstract Property<String> getScratchPrefix();

    /**
     * Provides the exact Java executable used for the child process.
     *
     * @return child-process Java executable
     */
    @InputFile
    public abstract RegularFileProperty getJavaExecutable();

    /**
     * Provides the directory receiving validated reports from the isolated process.
     *
     * @return validated report directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    void runEvidence() throws Exception {
        validateScenario();
        Path scratch = Files.createTempDirectory(Path.of("/tmp"), getScratchPrefix().get());
        try {
            Path runtime = Files.createDirectory(scratch.resolve("runtime"));
            List<Path> classpath = stageClasspath(runtime);
            Path work = Files.createDirectory(scratch.resolve("work"));
            Path output = Files.createDirectory(scratch.resolve("output"));
            Path temporary = Files.createDirectory(scratch.resolve("tmp"));
            Map<String, String> stagedProperties = stageInputs(scratch);

            List<String> command = new ArrayList<>();
            command.add(getJavaExecutable().get().getAsFile().getAbsolutePath());
            for (String argument : getJvmArguments().get()) {
                command.add(argument.replace("@OUTPUT@", output.toString()));
            }
            getSystemProperties().get().forEach(
                    (name, value) -> command.add("-D" + name + "=" + value));
            stagedProperties.forEach(
                    (name, value) -> command.add("-D" + name + "=" + value));
            command.add("-DperformanceOutput=" + output);
            command.add("-Djava.io.tmpdir=" + temporary);
            command.add("-cp");
            command.add(String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()));
            command.add("io.github.mundanej.map.performance.PerformanceEvidenceMain");
            command.addAll(getProgramArguments().get());

            Process process =
                    new ProcessBuilder(command).directory(work.toFile()).inheritIO().start();
            if (process.waitFor() != 0) {
                throw new GradleException("Performance evidence process failed");
            }
            publishReports(output);
        } finally {
            deleteTree(scratch);
        }
    }

    private void validateScenario() {
        String selected = getSystemProperties().get().get("performanceScenario");
        if (!getAllowedScenarios().get().isEmpty()
                && (selected == null || !getAllowedScenarios().get().contains(selected))) {
            throw new GradleException("performanceScenario must name one exact scenario");
        }
    }

    private List<Path> stageClasspath(Path runtime) throws IOException {
        List<Path> staged = new ArrayList<>();
        int index = 0;
        for (var entry : getRuntimeClasspath().getFiles()) {
            Path source = entry.toPath();
            if (!Files.exists(source)) {
                continue;
            }
            Path target = runtime.resolve("%03d-entry".formatted(index++));
            copyTree(source, target);
            staged.add(target);
        }
        if (staged.isEmpty()) {
            throw new GradleException("Performance runtime classpath is empty");
        }
        return staged;
    }

    private Map<String, String> stageInputs(Path scratch) throws IOException {
        Map<String, String> result = new java.util.TreeMap<>();
        Path inputs = Files.createDirectory(scratch.resolve("inputs"));
        int index = 0;
        for (var entry : new java.util.TreeMap<>(getStagedInputs().get()).entrySet()) {
            Path source = Path.of(entry.getValue());
            if (!Files.isRegularFile(source)) {
                throw new GradleException("Performance input is missing: " + source);
            }
            Path target = inputs.resolve("%03d-%s".formatted(index++, source.getFileName()));
            Files.copy(source, target);
            result.put(entry.getKey(), target.toString());
        }
        return result;
    }

    private void publishReports(Path scratchOutput) throws IOException {
        Path destination = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(destination);
        for (String name : getExpectedReports().get()) {
            Path source = scratchOutput.resolve(name);
            if (!Files.isRegularFile(source)
                    || Files.size(source) == 0
                    || Files.size(source) > 512L * 1024L * 1024L) {
                throw new GradleException("Performance report is missing or invalid: " + name);
            }
            Files.copy(source, destination.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path target = destination.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } else {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
