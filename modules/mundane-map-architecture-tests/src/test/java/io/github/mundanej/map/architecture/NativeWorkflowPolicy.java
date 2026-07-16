package io.github.mundanej.map.architecture;

import java.util.ArrayList;
import java.util.List;

/** Narrow source policy for the single checked-in Native Image release workflow. */
final class NativeWorkflowPolicy {
    private static final String GRADLE_COMMAND =
            "./gradlew nativeSmoke --console=plain 2>&1 | tee "
                    + "build/native-evidence/native-smoke.log";

    private NativeWorkflowPolicy() {}

    static List<String> violations(String workflow) {
        List<String> violations = new ArrayList<>();
        requireCount(workflow, "jobs:\n", 1, "one jobs mapping", violations);
        requireCount(workflow, "  native-smoke:\n", 1, "one native-smoke job", violations);
        long jobCount =
                workflow.lines()
                        .dropWhile(line -> !line.equals("jobs:"))
                        .filter(line -> line.matches("  [a-zA-Z0-9_-]+:"))
                        .count();
        if (jobCount != 1) {
            violations.add("Native workflow must contain exactly one job");
        }
        requireCount(
                workflow,
                "    runs-on: ubuntu-24.04\n",
                1,
                "pinned Ubuntu 24.04 runner",
                violations);
        requireCount(workflow, "    timeout-minutes: 30\n", 1, "30 minute timeout", violations);
        requireCount(
                workflow,
                "      - uses: graalvm/setup-graalvm@v1\n",
                1,
                "one GraalVM setup action",
                violations);
        requireCount(workflow, "          java-version: '21'\n", 1, "Java 21", violations);
        requireCount(
                workflow,
                "          distribution: graalvm\n",
                1,
                "GraalVM distribution",
                violations);
        requireCount(
                workflow,
                "          native-image-job-reports: true\n",
                1,
                "native job report",
                violations);

        List<String> commands =
                workflow.lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("./gradlew"))
                        .toList();
        if (!commands.equals(List.of(GRADLE_COMMAND))) {
            violations.add("Native workflow must run exactly the approved Gradle command");
        }
        for (String forbidden :
                List.of(
                        "nativeTest",
                        "qualityGate",
                        "shapefileCorpus",
                        "renderRegression",
                        "performanceEvidence",
                        "performanceQuick",
                        "publicationDryRun",
                        "consumerSmoke")) {
            if (commands.stream().anyMatch(command -> command.contains(forbidden))) {
                violations.add("Native workflow invokes forbidden lane " + forbidden);
            }
        }
        requireCount(workflow, "          set -o pipefail\n", 1, "pipefail", violations);
        requireCount(
                workflow,
                "          mkdir -p build/native-evidence\n",
                1,
                "bounded evidence directory",
                violations);
        requireCount(workflow, "            java -version\n", 1, "Java version record", violations);
        requireCount(
                workflow,
                "            native-image --version\n",
                1,
                "native-image version record",
                violations);
        requireCount(workflow, "            cat /etc/os-release\n", 1, "OS record", violations);
        requireCount(workflow, "            uname -m\n", 1, "architecture record", violations);
        requireCount(
                workflow,
                "      - name: Retain native evidence\n",
                1,
                "one evidence upload step",
                violations);
        requireCount(
                workflow, "        if: always()\n", 1, "always-run evidence upload", violations);
        requireCount(
                workflow,
                "        uses: actions/upload-artifact@v4\n",
                1,
                "versioned artifact upload",
                violations);
        requireCount(
                workflow,
                "          path: build/native-evidence\n",
                1,
                "bounded artifact path",
                violations);
        requireCount(workflow, "          retention-days: 14\n", 1, "14 day retention", violations);
        requireCount(
                workflow,
                "${{ secrets.GITHUB_TOKEN }}",
                1,
                "one read-scoped setup token",
                violations);
        if (count(workflow, "${{ secrets.") != 1) {
            violations.add("Native workflow must not consume another secret");
        }
        return List.copyOf(violations);
    }

    private static void requireCount(
            String value, String expected, int count, String description, List<String> violations) {
        if (count(value, expected) != count) {
            violations.add("Native workflow must contain " + description);
        }
    }

    private static int count(String value, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }
}
