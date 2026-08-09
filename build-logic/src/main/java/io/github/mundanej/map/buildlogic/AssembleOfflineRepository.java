package io.github.mundanej.map.buildlogic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Materializes the exact resolved external component set as a reusable Maven repository. */
@DisableCachingByDefault(because = "Reads Gradle's local artifact cache")
public abstract class AssembleOfflineRepository extends DefaultTask {
    /** Creates a task instance whose properties Gradle configures before execution. */
    @Inject
    public AssembleOfflineRepository() {
        getShadedPrimaryAliases().convention(List.of());
    }

    /**
     * Provides the exact external module coordinates to stage.
     *
     * @return exact external module coordinates to stage
     */
    @Input
    public abstract ListProperty<String> getCoordinates();

    /**
     * Provides plugin-marker rows whose implementation modules must also be staged.
     *
     * @return plugin-marker rows whose implementation modules must also be staged
     */
    @Input
    public abstract ListProperty<String> getPluginMarkers();

    /**
     * Provides exact coordinates whose sole {@code -shaded.jar} is the primary runtime artifact.
     *
     * @return exact coordinates requiring a shaded-to-primary alias
     */
    @Input
    public abstract ListProperty<String> getShadedPrimaryAliases();

    /**
     * Provides per-project files containing additional resolved coordinate rows.
     *
     * @return per-project files containing additional resolved coordinate rows
     */
    @InputFiles
    public abstract ConfigurableFileCollection getCoordinateFiles();

    /**
     * Provides the Gradle artifact cache read by this non-cacheable staging task.
     *
     * @return Gradle artifact cache
     */
    @Internal
    public abstract DirectoryProperty getArtifactCache();

    /**
     * Provides the directory in which the complete Maven repository is materialized.
     *
     * @return repository output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    void assemble() throws IOException, NoSuchAlgorithmException {
        Path repository = getOutputDirectory().get().getAsFile().toPath();
        deleteTree(repository);
        Files.createDirectories(repository);

        Map<String, Set<String>> dependencyEdges = new java.util.TreeMap<>();
        for (String row : getCoordinates().get()) {
            addCoordinateRow(dependencyEdges, row);
        }
        for (var file : getCoordinateFiles().getFiles()) {
            if (file.isFile()) {
                for (String row : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (!row.isBlank()) {
                        addCoordinateRow(dependencyEdges, row.trim());
                    }
                }
            }
        }
        Set<String> requiredPluginImplementations = new TreeSet<>();
        Set<String> shadedPrimaryAliases = new TreeSet<>(getShadedPrimaryAliases().get());
        for (String marker : getPluginMarkers().get()) {
            String[] fields = marker.split("\\|", -1);
            require(fields.length == 5, "Invalid plugin marker row: " + marker);
            String implementation = fields[3] + ":" + fields[4] + ":" + fields[2];
            dependencyEdges.computeIfAbsent(implementation, ignored -> new TreeSet<>());
            requiredPluginImplementations.add(implementation);
        }

        Path cache = getArtifactCache().get().getAsFile().toPath();
        List<String> missing = new ArrayList<>();
        for (var component : dependencyEdges.entrySet()) {
            String coordinate = component.getKey();
            String[] fields = coordinate.split(":", -1);
            require(fields.length == 3, "Invalid module coordinate: " + coordinate);
            Path source = cache.resolve(fields[0]).resolve(fields[1]).resolve(fields[2]);
            Path destination =
                    repository
                            .resolve(fields[0].replace('.', '/'))
                            .resolve(fields[1])
                            .resolve(fields[2]);
            if (Files.isDirectory(source)) {
                copyCachedPayloads(source, destination);
                if (shadedPrimaryAliases.contains(coordinate)) {
                    materializeShadedPrimaryJar(destination, fields[1], fields[2]);
                }
            } else {
                if (requiredPluginImplementations.contains(coordinate)) {
                    missing.add(coordinate);
                }
            }
            writePom(
                    destination,
                    fields[0],
                    fields[1],
                    fields[2],
                    component.getValue());
        }
        if (!missing.isEmpty()) {
            throw new GradleException(
                    "Resolved coordinates are missing from the Gradle artifact cache: " + missing);
        }
        writePluginMarkers(repository);
        writeManifest(repository);
    }

    private void writePluginMarkers(Path repository) throws IOException {
        for (String row : getPluginMarkers().get()) {
            String[] fields = row.split("\\|", -1);
            Path directory =
                    repository
                            .resolve(fields[0].replace('.', '/'))
                            .resolve(fields[1])
                            .resolve(fields[2]);
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve(fields[1] + "-" + fields[2] + ".pom"),
                    pom(
                            fields[0],
                            fields[1],
                            fields[2],
                            "<packaging>pom</packaging><dependencies><dependency><groupId>"
                                    + fields[3]
                                    + "</groupId><artifactId>"
                                    + fields[4]
                                    + "</artifactId><version>"
                                    + fields[2]
                                    + "</version></dependency></dependencies>"),
                    StandardCharsets.UTF_8);
        }
    }

    private static void copyCachedPayloads(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        Set<String> copiedNames = new LinkedHashSet<>();
        try (var paths = Files.walk(source, 2)) {
            for (Path artifact : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = artifact.getFileName().toString();
                if (name.endsWith(".pom")) {
                    // Every component receives the deterministic resolved-dependency POM below.
                    // Repositories can cache different source POMs for the same Gradle module,
                    // so copying those transient inputs would create a false artifact conflict.
                    continue;
                }
                Path target = destination.resolve(name);
                if (copiedNames.add(name)) {
                    Files.copy(artifact, target, StandardCopyOption.REPLACE_EXISTING);
                } else if (!sha256(artifact).equals(sha256(target))) {
                    throw new GradleException("Conflicting cached artifacts named " + name);
                }
            }
        }
    }

    private static void materializeShadedPrimaryJar(Path directory, String module, String version)
            throws IOException {
        Path primary = directory.resolve(module + "-" + version + ".jar");
        Path shaded = directory.resolve(module + "-" + version + "-shaded.jar");
        if (!Files.isRegularFile(primary) && Files.isRegularFile(shaded)) {
            Files.copy(shaded, primary, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void writePom(
            Path directory,
            String group,
            String module,
            String version,
            Set<String> dependencies)
            throws IOException {
        Files.createDirectories(directory);
        Path pom = directory.resolve(module + "-" + version + ".pom");
        boolean hasModule = Files.isRegularFile(directory.resolve(module + "-" + version + ".module"));
        boolean hasJar = Files.isRegularFile(directory.resolve(module + "-" + version + ".jar"));
        StringBuilder additional = new StringBuilder();
        if (hasModule) {
            additional.append("<!-- do_not_remove: published-with-gradle-metadata -->");
        } else {
            if (!hasJar) {
                additional.append("<packaging>pom</packaging>");
            }
            appendDependencies(additional, dependencies);
        }
        Files.writeString(
                pom, pom(group, module, version, additional.toString()), StandardCharsets.UTF_8);
    }

    private static void addCoordinateRow(
            Map<String, Set<String>> dependencyEdges, String row) {
        String[] fields = row.split("\\|", -1);
        require(fields.length == 1 || fields.length == 2, "Invalid resolved coordinate row: " + row);
        Set<String> dependencies =
                dependencyEdges.computeIfAbsent(fields[0], ignored -> new TreeSet<>());
        if (fields.length == 2 && !fields[1].isBlank()) {
            dependencies.addAll(List.of(fields[1].split(",")));
        }
    }

    private static void appendDependencies(StringBuilder target, Set<String> dependencies) {
        if (dependencies.isEmpty()) {
            return;
        }
        target.append("<dependencies>");
        for (String dependency : dependencies) {
            String[] fields = dependency.split(":", -1);
            target.append("<dependency><groupId>")
                    .append(fields[0])
                    .append("</groupId><artifactId>")
                    .append(fields[1])
                    .append("</artifactId><version>")
                    .append(fields[2])
                    .append("</version></dependency>");
        }
        target.append("</dependencies>");
    }

    private static String pom(
            String group, String module, String version, String additionalContent) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  %s
                </project>
                """.formatted(group, module, version, additionalContent);
    }

    private static void writeManifest(Path repository)
            throws IOException, NoSuchAlgorithmException {
        List<String> rows = new ArrayList<>();
        try (var paths = Files.walk(repository)) {
            for (Path artifact : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (artifact.getFileName().toString().equals("manifest.sha256")) {
                    continue;
                }
                rows.add(
                        sha256(artifact)
                                + "  "
                                + repository.relativize(artifact).toString().replace('\\', '/'));
            }
        }
        Files.writeString(
                repository.resolve("manifest.sha256"),
                String.join("\n", rows) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                for (int count; (count = input.read(buffer)) >= 0; ) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GradleException(message);
        }
    }
}
