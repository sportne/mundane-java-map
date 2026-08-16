package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DocumentationConsistencyTest {
    private static final Pattern LOCAL_LINK =
            Pattern.compile("(?<!!)\\[[^\\]\\r\\n]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern GRADLE_COMMAND =
            Pattern.compile("^\\s*\\./gradlew\\s+([^\\r\\n]+)", Pattern.MULTILINE);
    private static final Pattern HEADING =
            Pattern.compile("^#{1,6}\\s+(.+?)(?:\\s+#+)?$", Pattern.MULTILINE);
    private static final Pattern PUBLISHED_MODULE_ROW =
            Pattern.compile("^\\|\\s+`([^`]+)`\\s+\\|", Pattern.MULTILINE);
    private static final Pattern EXAMPLE_RUN = Pattern.compile(":examples:([a-z0-9-]+):run");
    private static final Pattern INDEXED_TASK =
            Pattern.compile("\\(((?:[a-zA-Z0-9._-]+/)*)(G\\d+-\\d+[^)]*\\.md)\\)");
    private static final Pattern COMMA = Pattern.compile(",");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static Path repositoryRoot;
    private static List<InventoryEntry> inventory;
    private static Map<String, Set<String>> configuredTasks;

    @BeforeAll
    static void configure() {
        repositoryRoot =
                Path.of(System.getProperty("map.documentation.repositoryRoot"))
                        .toAbsolutePath()
                        .normalize();
        inventory =
                Arrays.stream(System.getProperty("map.documentation.inventory").split("\\R"))
                        .map(
                                row -> {
                                    String[] fields = row.split("\\t", -1);
                                    return new InventoryEntry(
                                            fields[0], Boolean.parseBoolean(fields[1]));
                                })
                        .toList();
        configuredTasks = new HashMap<>();
        for (String row : System.getProperty("map.documentation.tasks").lines().toList()) {
            String[] fields = row.split("\\t", -1);
            configuredTasks.put(
                    fields[0],
                    fields[1].isEmpty()
                            ? Set.of()
                            : Set.copyOf(Arrays.asList(COMMA.split(fields[1], -1))));
        }
    }

    @Test
    void localMarkdownLinksAndAnchorsExist() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path document : markdownFiles()) {
            Matcher matcher = LOCAL_LINK.matcher(Files.readString(document));
            while (matcher.find()) {
                String destination = matcher.group(1);
                if (hasExternalScheme(destination)) {
                    continue;
                }
                String[] parts = destination.split("#", 2);
                Path target =
                        parts[0].isEmpty()
                                ? document
                                : Objects.requireNonNull(document.getParent())
                                        .resolve(parts[0])
                                        .normalize();
                if (!target.startsWith(repositoryRoot) || !Files.exists(target)) {
                    violations.add(relative(document) + " -> " + destination);
                } else if (parts.length == 2
                        && Objects.requireNonNull(target.getFileName()).toString().endsWith(".md")
                        && !markdownAnchors(target)
                                .contains(URLDecoder.decode(parts[1], StandardCharsets.UTF_8))) {
                    violations.add(relative(document) + " -> missing anchor " + destination);
                }
            }
        }
        assertTrue(
                violations.isEmpty(),
                () -> "Broken repository-local Markdown links:\n" + String.join("\n", violations));
    }

    @Test
    void taskIndexExactlyMatchesTaskCards() throws IOException {
        String index = Files.readString(repositoryRoot.resolve("tasks/README.md"));
        List<String> cards = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot.resolve("tasks"))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("G\\d+-\\d+-.+\\.md"))
                    .forEach(
                            path ->
                                    cards.add(
                                            repositoryRoot
                                                    .resolve("tasks")
                                                    .relativize(path)
                                                    .toString()
                                                    .replace('\\', '/')));
        }
        List<String> indexed = new ArrayList<>();
        Matcher links = INDEXED_TASK.matcher(index);
        while (links.find()) {
            indexed.add((links.group(1) == null ? "" : links.group(1)) + links.group(2));
        }
        assertExactInventory("task index", cards, indexed);
    }

    @Test
    void everyCurrentModuleOwnsACapabilityProfile() {
        List<String> violations = new ArrayList<>();
        for (InventoryEntry entry : inventory) {
            if (!entry.path.startsWith(":modules:")) {
                continue;
            }
            Path module = repositoryRoot.resolve(entry.path.substring(1).replace(':', '/'));
            if (!Files.isRegularFile(module.resolve("CAPABILITIES.md"))) {
                violations.add(entry.path);
            }
        }
        assertTrue(
                violations.isEmpty(),
                () ->
                        "Current modules without module-local CAPABILITIES.md:\n"
                                + String.join("\n", violations));
    }

    @Test
    void rootReadmeExactlyInventoriesPublishedModulesAndRunnableExamples() throws IOException {
        String readme = Files.readString(repositoryRoot.resolve("README.md"));
        List<String> expectedModules = new ArrayList<>();
        List<String> expectedExamples = new ArrayList<>();
        for (InventoryEntry entry : inventory) {
            String artifact = entry.path.substring(entry.path.lastIndexOf(':') + 1);
            if (entry.published) {
                expectedModules.add(artifact);
            }
            if (entry.path.startsWith(":examples:")) {
                expectedExamples.add(artifact);
            }
        }
        List<String> documentedModules = matches(PUBLISHED_MODULE_ROW, readme);
        String examplesSection =
                readme.substring(
                        readme.indexOf("## Examples"), readme.indexOf("## Licenses and fixture"));
        List<String> documentedExamples =
                new ArrayList<>(new HashSet<>(matches(EXAMPLE_RUN, examplesSection)));
        assertExactInventory("published-module README", expectedModules, documentedModules);
        assertExactInventory("runnable-example README", expectedExamples, documentedExamples);
    }

    @Test
    void operationalGradleCommandsReferToConfiguredTasks() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path document : operationalDocuments()) {
            Matcher commands = GRADLE_COMMAND.matcher(Files.readString(document));
            while (commands.find()) {
                for (String token : WHITESPACE.splitAsStream(commands.group(1).trim()).toList()) {
                    if (token.startsWith("-") || token.equals("\\")) {
                        break;
                    }
                    validateTask(relative(document), token, violations);
                }
            }
        }
        assertTrue(
                violations.isEmpty(),
                () ->
                        "Documented Gradle commands without matching tasks:\n"
                                + String.join("\n", violations));
    }

    private static void validateTask(String document, String taskPath, List<String> violations) {
        int separator = taskPath.lastIndexOf(':');
        String projectPath = separator > 0 ? taskPath.substring(0, separator) : ":";
        String taskName = separator >= 0 ? taskPath.substring(separator + 1) : taskPath;
        Set<String> tasks = configuredTasks.get(projectPath);
        if (tasks == null || !tasks.contains(taskName)) {
            violations.add(document + " -> " + taskPath);
        }
    }

    private static Set<String> markdownAnchors(Path document) throws IOException {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        Matcher headings = HEADING.matcher(Files.readString(document));
        while (headings.find()) {
            String base =
                    headings.group(1)
                            .toLowerCase(Locale.ROOT)
                            .replaceAll("[^\\p{L}\\p{N}\\s_-]", "")
                            .replace(' ', '-');
            int occurrence = occurrences.merge(base, 1, Integer::sum);
            anchors.add(occurrence == 1 ? base : base + "-" + (occurrence - 1));
        }
        return anchors;
    }

    private static List<String> matches(Pattern pattern, String text) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private static void assertExactInventory(
            String name, List<String> expectedItems, List<String> actualItems) {
        List<String> expected = expectedItems.stream().sorted().toList();
        List<String> actual = actualItems.stream().sorted().toList();
        assertTrue(
                expected.equals(actual),
                () -> name + " mismatch:\nexpected " + expected + "\nactual   " + actual);
    }

    private static List<Path> markdownFiles() throws IOException {
        List<Path> documents = new ArrayList<>();
        Files.walkFileTree(
                repositoryRoot,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) {
                        return !directory.equals(repositoryRoot) && isIgnored(directory)
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (Objects.requireNonNull(file.getFileName()).toString().endsWith(".md")) {
                            documents.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return List.copyOf(documents);
    }

    private static List<Path> operationalDocuments() throws IOException {
        List<Path> documents = new ArrayList<>();
        documents.add(repositoryRoot.resolve("README.md"));
        documents.add(repositoryRoot.resolve("AGENTS.md"));
        for (Path document : markdownFiles()) {
            Path relative = repositoryRoot.relativize(document);
            if ((relative.startsWith("examples") || relative.startsWith("modules"))
                    && Objects.requireNonNull(document.getFileName())
                            .toString()
                            .equals("README.md")) {
                documents.add(document);
            }
        }
        return documents;
    }

    private static boolean isIgnored(Path path) {
        Path relative = repositoryRoot.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals(".gradle")
                    || name.equals("build")
                    || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExternalScheme(String destination) {
        try {
            return URI.create(destination).isAbsolute();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String relative(Path path) {
        return repositoryRoot.relativize(path).toString().replace('\\', '/');
    }

    private record InventoryEntry(String path, boolean published) {}
}
