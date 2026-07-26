package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VerificationManifestTest {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String COMMAND_SEPARATOR = " ; ";
    private static final Map<String, Set<String>> REQUIRED_PATH_FILTERS =
            Map.of(
                    ".github/workflows/offline-repository.yml",
                    Set.of(
                            ".github/workflows/offline-repository.yml",
                            "build.gradle",
                            "settings.gradle",
                            "gradle.properties",
                            "gradlew",
                            "gradlew.bat",
                            "gradle/**",
                            "build-logic/**",
                            "**/*.gradle",
                            "**/*.gradle.kts",
                            "**/gradle.properties"),
                    ".github/workflows/sqlite-adapter-evidence.yml",
                    Set.of(
                            ".github/workflows/sqlite-adapter-evidence.yml",
                            "build-logic/**",
                            "gradle/**",
                            "modules/mundane-map-api/**",
                            "modules/mundane-map-core/**",
                            "modules/mundane-map-awt/**",
                            "modules/mundane-map-io-image/**",
                            "modules/mundane-map-io-geopackage-xerial/**",
                            "modules/mundane-map-io-mbtiles-xerial/**",
                            "build.gradle",
                            "settings.gradle"));
    private static final Set<String> REQUIRED_LANES =
            Set.of(
                    "architecture-quality-coverage",
                    "supported-test-jdk",
                    "render-regression",
                    "shapefile-corpus",
                    "dted-corpus",
                    "performance-evidence",
                    "native-image",
                    "offline-repository",
                    "sqlite-glibc",
                    "sqlite-musl",
                    "publication-consumer",
                    "live-track-smoke",
                    "live-track-evidence");
    private static final Map<String, LanePolicy> REQUIRED_POLICIES =
            Map.ofEntries(
                    policy(
                            "architecture-quality-coverage",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "quality",
                            "checkAll,Spotless,Javadoc,Checkstyle,SpotBugs,JaCoCo,architecture"),
                    policy(
                            "supported-test-jdk",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "test-java-25",
                            "every normal JUnit suite on Java 25 with Java 21 compilation; no"
                                    + " duplicate formatting, static-analysis, coverage, or Javadoc"
                                    + " tasks"),
                    policy(
                            "render-regression",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "render-regression",
                            "portable tolerant rendering"),
                    policy(
                            "shapefile-corpus",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "shapefile-corpus",
                            "primed isolated corpus"),
                    policy(
                            "dted-corpus",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "dted-corpus",
                            "primed isolated corpus"),
                    policy(
                            "performance-evidence",
                            ".github/workflows/ci.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "performance-evidence",
                            "canonical descriptive evidence"),
                    policy(
                            "native-image",
                            ".github/workflows/native-image.yml",
                            "pull_request,push-main,workflow_dispatch",
                            "native-smoke",
                            "Ubuntu 24.04 Linux x86-64 GraalVM Java 21"),
                    policy(
                            "offline-repository",
                            ".github/workflows/offline-repository.yml",
                            "relevant-pull-request,relevant-push-main,workflow_dispatch",
                            "offline-repository",
                            "isolated empty Gradle home and staged Maven repository"),
                    policy(
                            "sqlite-glibc",
                            ".github/workflows/sqlite-adapter-evidence.yml",
                            "relevant-pull-request,relevant-push-main,workflow_dispatch",
                            "glibc",
                            "Java 21 Ubuntu 22.04/24.04 checks, staged probes, rejection cases, and"
                                    + " success for both adapters"),
                    policy(
                            "sqlite-musl",
                            ".github/workflows/sqlite-adapter-evidence.yml",
                            "relevant-pull-request,relevant-push-main,workflow_dispatch",
                            "musl",
                            "Alpine 3.20 musl rejection for both adapters"),
                    policy(
                            "publication-consumer",
                            "README.md",
                            "explicit-opt-in",
                            "documentation",
                            "staged artifacts and isolated Java 21 consumer"),
                    policy(
                            "live-track-smoke",
                            "README.md",
                            "explicit-opt-in",
                            "documentation",
                            "bounded deterministic 10k smoke"),
                    policy(
                            "live-track-evidence",
                            "examples/live-track-stress/README.md",
                            "explicit-opt-in",
                            "documentation",
                            "10k/100k/1m named profiles"));

    private static Path repositoryRoot;
    private static List<ManifestRow> rows;

    @BeforeAll
    static void readManifest() throws IOException {
        repositoryRoot = Path.of(System.getProperty("map.verification.repositoryRoot"));
        List<String> lines =
                Files.readAllLines(Path.of(System.getProperty("map.verification.manifest")));
        assertFalse(lines.isEmpty());
        assertEquals("lane\towner\ttrigger\tjob\tcommands\tevidence", lines.getFirst());
        rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(6, fields.length, line);
            rows.add(
                    new ManifestRow(
                            fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]));
        }
    }

    @Test
    void manifestHasEveryRequiredLaneExactlyOnce() {
        Set<String> lanes = new HashSet<>();
        for (ManifestRow row : rows) {
            assertTrue(lanes.add(row.lane), () -> "Duplicate verification lane " + row.lane);
            assertEquals(
                    REQUIRED_POLICIES.get(row.lane),
                    new LanePolicy(row.owner, row.trigger, row.job, row.evidence),
                    row.lane);
            assertFalse(commands(row).isEmpty(), row.lane);
        }
        assertEquals(REQUIRED_LANES, lanes);
    }

    @Test
    void everyManifestCommandIsScheduledInItsOwningJob() throws IOException {
        for (ManifestRow row : rows) {
            String scope = owningScope(row, Files.readString(owner(row)));
            for (String command : commands(row)) {
                assertTrue(
                        normalize(scope).contains(command),
                        () -> row.lane + " job " + row.job + " omits exact command " + command);
            }
        }
    }

    @Test
    void everyLaneRejectsRemovalOfEachOwningCommand() throws IOException {
        for (ManifestRow row : rows) {
            String scope = normalize(owningScope(row, Files.readString(owner(row))));
            assertTrue(missingCommands(row, scope).isEmpty(), row.lane);
            for (String command : commands(row)) {
                String mutated = scope.replace(command, "REMOVED_RETAINED_COMMAND");
                assertTrue(
                        missingCommands(row, mutated).contains(command),
                        () -> row.lane + " mutation was not rejected for " + command);
            }
        }
    }

    @Test
    void workflowTriggerPoliciesMatchManifest() throws IOException {
        for (ManifestRow row : rows) {
            if (!workflow(row)) {
                continue;
            }
            String workflow = Files.readString(owner(row));
            String onBlock = workflow.substring(0, workflow.indexOf("\npermissions:"));
            assertTrue(onBlock.contains("\n  pull_request:"), row.lane);
            assertTrue(onBlock.contains("\n  push:"), row.lane);
            assertTrue(onBlock.contains("\n  workflow_dispatch:"), row.lane);
            if (row.trigger.startsWith("relevant-")) {
                assertEquals(
                        REQUIRED_PATH_FILTERS.get(row.owner),
                        pathFilters(onBlock, "pull_request"),
                        () -> row.lane + " pull_request path-filter inventory");
                assertEquals(
                        REQUIRED_PATH_FILTERS.get(row.owner),
                        pathFilters(onBlock, "push"),
                        () -> row.lane + " push path-filter inventory");
            } else {
                assertFalse(onBlock.contains("\n    paths:"), row.lane);
            }
        }
    }

    @Test
    void filteredWorkflowRejectsAPathRemovedFromOnlyOneTrigger() throws IOException {
        for (var entry : REQUIRED_PATH_FILTERS.entrySet()) {
            String workflow = Files.readString(repositoryRoot.resolve(entry.getKey()));
            String onBlock = workflow.substring(0, workflow.indexOf("\npermissions:"));
            String requiredPath = entry.getValue().iterator().next();
            String pullRequest = eventBlock(onBlock, "pull_request");
            String mutatedPullRequest = pullRequest.replace("      - '" + requiredPath + "'\n", "");
            assertFalse(pullRequest.equals(mutatedPullRequest), entry.getKey());
            String mutated = onBlock.replace(pullRequest, mutatedPullRequest);
            assertFalse(
                    entry.getValue().equals(pathFilters(mutated, "pull_request")),
                    () -> entry.getKey() + " accepted a one-trigger path removal");
            assertEquals(entry.getValue(), pathFilters(mutated, "push"), entry.getKey());
        }
    }

    @Test
    void ciRetainsFullJava21GateAndNarrowJava25RuntimeCheck() throws IOException {
        String workflow = Files.readString(repositoryRoot.resolve(".github/workflows/ci.yml"));
        assertTrue(ciPolicyViolations(workflow).isEmpty(), ciPolicyViolations(workflow).toString());
    }

    @Test
    void ciPolicyRejectsMissingOrWeakenedOwningCommands() throws IOException {
        String workflow = Files.readString(repositoryRoot.resolve(".github/workflows/ci.yml"));
        assertFalse(
                ciPolicyViolations(workflow.replace(" qualityGate --console=plain", " help"))
                        .isEmpty());
        assertFalse(
                ciPolicyViolations(workflow.replace(" supportedJdkTests --console=plain", " test"))
                        .isEmpty());
        assertFalse(ciPolicyViolations(workflow.replace("  render-regression:\n", "")).isEmpty());
    }

    private static Path owner(ManifestRow row) {
        Path owner = repositoryRoot.resolve(row.owner).normalize();
        assertTrue(owner.startsWith(repositoryRoot) && Files.isRegularFile(owner), row.owner);
        return owner;
    }

    private static String owningScope(ManifestRow row, String content) {
        if (!workflow(row)) {
            assertEquals("documentation", row.job, row.lane);
            return content;
        }
        String marker = "\n  " + row.job + ":\n";
        int start = content.indexOf(marker);
        assertTrue(start >= 0, () -> row.owner + " omits job " + row.job);
        int end = content.length();
        Pattern nextJob = Pattern.compile("(?m)^  [a-zA-Z0-9_-]+:\\s*$");
        var matcher = nextJob.matcher(content);
        if (matcher.find(start + marker.length())) {
            end = matcher.start();
        }
        return content.substring(start, end);
    }

    private static boolean workflow(ManifestRow row) {
        return row.owner.startsWith(".github/workflows/");
    }

    private static Set<String> missingCommands(ManifestRow row, String normalizedScope) {
        Set<String> missing = new HashSet<>();
        for (String command : commands(row)) {
            if (!normalizedScope.contains(command)) {
                missing.add(command);
            }
        }
        return missing;
    }

    private static Set<String> pathFilters(String onBlock, String event) {
        Set<String> filters = new HashSet<>();
        for (String line : eventBlock(onBlock, event).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- '") && trimmed.endsWith("'")) {
                filters.add(trimmed.substring(3, trimmed.length() - 1));
            }
        }
        return filters;
    }

    private static String eventBlock(String onBlock, String event) {
        String marker = "\n  " + event + ":\n";
        int start = onBlock.indexOf(marker);
        assertTrue(start >= 0, event);
        int end = onBlock.length();
        var nextEvent = Pattern.compile("(?m)^  [a-zA-Z0-9_-]+:\\s*$").matcher(onBlock);
        if (nextEvent.find(start + marker.length())) {
            end = nextEvent.start();
        }
        return onBlock.substring(start, end);
    }

    private static List<String> commands(ManifestRow row) {
        return Pattern.compile(Pattern.quote(COMMAND_SEPARATOR))
                .splitAsStream(row.commands)
                .filter(command -> !command.isBlank())
                .toList();
    }

    private static String normalize(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private static List<String> ciPolicyViolations(String workflow) {
        List<String> violations = new ArrayList<>();
        requireCount(workflow, "-Pmap.testJavaVersion=21 qualityGate", 1, violations);
        requireCount(workflow, "-Pmap.testJavaVersion=25 supportedJdkTests", 1, violations);
        requireCount(workflow, "-Pmap.testJavaVersion=25 checkAll", 0, violations);
        requireCount(workflow, "-Pmap.testJavaVersion=25 qualityGate", 0, violations);
        requireCount(workflow, "java-version: '21'", 6, violations);
        requireCount(workflow, "java-version: '25'", 1, violations);
        requireCount(workflow, "  quality:\n", 1, violations);
        requireCount(workflow, "  test-java-25:\n", 1, violations);
        requireCount(workflow, "  render-regression:\n", 1, violations);
        requireCount(workflow, "  shapefile-corpus:\n", 1, violations);
        requireCount(workflow, "  dted-corpus:\n", 1, violations);
        requireCount(workflow, "  performance-evidence:\n", 1, violations);
        return violations;
    }

    private static void requireCount(
            String workflow, String token, int expected, List<String> violations) {
        int actual = occurrences(workflow, token);
        if (actual != expected) {
            violations.add(token + ": expected " + expected + ", found " + actual);
        }
    }

    private static int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }

    private static Map.Entry<String, LanePolicy> policy(
            String lane, String owner, String trigger, String job, String evidence) {
        return Map.entry(lane, new LanePolicy(owner, trigger, job, evidence));
    }

    private record LanePolicy(String owner, String trigger, String job, String evidence) {}

    private record ManifestRow(
            String lane,
            String owner,
            String trigger,
            String job,
            String commands,
            String evidence) {}
}
