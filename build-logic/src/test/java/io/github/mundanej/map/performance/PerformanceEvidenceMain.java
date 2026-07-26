package io.github.mundanej.map.performance;

import java.nio.file.Files;
import java.nio.file.Path;

/** Child-process fixture for build-logic performance-task tests. */
public final class PerformanceEvidenceMain {
    private PerformanceEvidenceMain() {}

    /**
     * Writes one report containing the staged fixture content.
     *
     * @param arguments ignored fixture arguments
     * @throws Exception when fixture input or report output fails
     */
    public static void main(String[] arguments) throws Exception {
        String content = Files.readString(Path.of(System.getProperty("fixture.path")));
        Path output = Path.of(System.getProperty("performanceOutput"));
        Files.writeString(output.resolve("performance.txt"), content + ":" + arguments[0]);
    }
}
