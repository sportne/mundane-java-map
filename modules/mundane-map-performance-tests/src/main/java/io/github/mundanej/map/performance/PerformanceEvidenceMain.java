package io.github.mundanej.map.performance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;

/** Public JavaExec launcher for the support-only performance-evidence lane. */
public final class PerformanceEvidenceMain {
    private PerformanceEvidenceMain() {}

    /**
     * Builds deterministic fixtures, executes selected scenarios, and writes schema-v1 reports.
     *
     * @param arguments must be empty
     * @throws Exception when configuration, fixture, scenario, cleanup, or report work fails
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("Performance evidence accepts no command arguments");
        }
        List<String> ids = ScenarioRegistry.ids();
        if (ids.size() != 39 || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("Performance scenario registry is invalid");
        }
        EvidenceConfiguration configuration = EvidenceConfiguration.system(ids);
        Path output = outputDirectory();
        Files.createDirectories(output);
        Path workspace = output.resolve("fixtures");
        deleteTree(workspace);
        List<EvidenceScenario> scenarios =
                ScenarioRegistry.create(
                        configuration.profile(), workspace, configuration.scenario());
        List<String> expectedIds = configuration.scenario().map(List::of).orElse(ids);
        if (!scenarios.stream().map(EvidenceScenario::id).toList().equals(expectedIds)) {
            IllegalStateException failure =
                    new IllegalStateException("Performance scenario declaration order changed");
            ScenarioRegistry.closeScenariosAfterFailure(scenarios, failure);
            throw failure;
        }
        EvidenceReport report = new EvidenceRunner().run(configuration, scenarios);
        writeReplacing(output.resolve("evidence-v1.json"), report.json());
        writeReplacing(output.resolve("evidence-v1.md"), report.markdown());
    }

    private static void deleteTree(Path root) throws java.io.IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Path outputDirectory() {
        String configured = System.getProperty("performanceOutput");
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("performanceOutput is required");
        }
        Path output = Path.of(configured);
        if (!output.isAbsolute() || !output.equals(output.normalize())) {
            throw new IllegalArgumentException("performanceOutput must be absolute and normalized");
        }
        return output;
    }

    private static void writeReplacing(Path target, byte[] content) throws java.io.IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content);
        Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
