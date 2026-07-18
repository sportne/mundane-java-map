package io.github.mundanej.map.buildlogic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.w3c.dom.Element;

/** Verifies the project-specific invariants of the staged Maven repository. */
@DisableCachingByDefault(because = "Reads freshly staged publication output")
public abstract class VerifyPublicationRepository extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRepositoryDirectory();

    @Input
    public abstract ListProperty<String> getReleaseContract();

    @Input
    public abstract Property<String> getPublicationVersion();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLicenseFile();

    @OutputFile
    public abstract RegularFileProperty getArtifactManifest();

    @TaskAction
    void verify() throws Exception {
        Path repository = getRepositoryDirectory().get().getAsFile().toPath();
        Map<String, Contract> contract = parseContract(getReleaseContract().get());
        Path group = repository.resolve("io/github/mundanej");
        Set<String> actualModules = childDirectories(group);
        require(
                actualModules.equals(contract.keySet()),
                "Published module mismatch: expected "
                        + contract.keySet()
                        + ", found "
                        + actualModules);

        List<String> manifest = new ArrayList<>();
        for (var entry : contract.entrySet()) {
            verifyModule(
                    repository,
                    group.resolve(entry.getKey()),
                    entry.getKey(),
                    getPublicationVersion().get(),
                    entry.getValue(),
                    getLicenseFile().get().getAsFile().toPath(),
                    manifest);
        }
        Path output = getArtifactManifest().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                String.join("\n", manifest.stream().sorted().toList()) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void verifyModule(
            Path repository,
            Path moduleDirectory,
            String module,
            String version,
            Contract contract,
            Path license,
            List<String> manifest)
            throws Exception {
        Path versionDirectory = moduleDirectory.resolve(version);
        require(Files.isDirectory(versionDirectory), "Missing publication " + module + ":" + version);
        List<Path> payloads;
        try (var paths = Files.list(versionDirectory)) {
            payloads =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> !isChecksum(path) && !path.getFileName().toString().equals("maven-metadata.xml"))
                            .sorted()
                            .toList();
        }

        Path pom = exactlyOne(payloads, name -> name.endsWith(".pom"), module + " POM");
        Path metadata = exactlyOne(payloads, name -> name.endsWith(".module"), module + " module metadata");
        Path sources = exactlyOne(payloads, name -> name.endsWith("-sources.jar"), module + " sources");
        Path javadoc = exactlyOne(payloads, name -> name.endsWith("-javadoc.jar"), module + " Javadocs");
        Path binary =
                exactlyOne(
                        payloads,
                        name ->
                                name.endsWith(".jar")
                                        && !name.endsWith("-sources.jar")
                                        && !name.endsWith("-javadoc.jar"),
                        module + " binary");
        require(
                Set.copyOf(payloads).equals(Set.of(pom, metadata, sources, javadoc, binary)),
                "Unexpected primary publication payload for " + module + ": " + payloads);

        verifyPom(pom, module, version, contract);
        verifyArchive(binary, contract.packageRoot(), license, true);
        verifyArchive(sources, contract.packageRoot(), license, true);
        verifyArchive(javadoc, contract.packageRoot(), license, false);
        for (Path payload : payloads) {
            verifySha256(payload);
            manifest.add(
                    repository.relativize(payload).toString().replace('\\', '/')
                            + "\t"
                            + Files.size(payload)
                            + "\t"
                            + sha256(payload));
        }
    }

    private static void verifyPom(Path pom, String module, String version, Contract contract)
            throws Exception {
        Element project = parseXml(pom);
        require("io.github.mundanej".equals(childText(project, "groupId")), "Wrong POM group: " + pom);
        require(module.equals(childText(project, "artifactId")), "Wrong POM artifact: " + pom);
        require(version.equals(childText(project, "version")), "Wrong POM version: " + pom);
        require(
                "BSD 3-Clause License".equals(
                        project.getElementsByTagName("license").getLength() == 1
                                ? childText((Element) project.getElementsByTagName("license").item(0), "name")
                                : ""),
                "Missing BSD-3-Clause POM license: " + pom);

        Map<String, Set<String>> dependencies = new HashMap<>();
        var nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            require(
                    "io.github.mundanej".equals(childText(dependency, "groupId")),
                    "External runtime dependency leaked into " + pom);
            dependencies
                    .computeIfAbsent(childText(dependency, "scope"), ignored -> new TreeSet<>())
                    .add(childText(dependency, "artifactId"));
        }
        require(
                dependencies.getOrDefault("compile", Set.of()).equals(contract.compileDependencies()),
                "Compile dependency mismatch in " + pom + ": " + dependencies);
        require(
                dependencies.getOrDefault("runtime", Set.of()).equals(contract.runtimeDependencies()),
                "Runtime dependency mismatch in " + pom + ": " + dependencies);
        require(
                dependencies.keySet().stream().allMatch(scope -> scope.equals("compile") || scope.equals("runtime")),
                "Unexpected dependency scope in " + pom + ": " + dependencies);
    }

    private static void verifyArchive(
            Path archive, String packageRoot, Path license, boolean requirePackageRoot)
            throws IOException {
        byte[] expectedLicense = Files.readAllBytes(license);
        try (var zip = new ZipFile(archive.toFile())) {
            var licenseEntry = zip.getEntry("META-INF/LICENSE");
            require(licenseEntry != null, "Missing META-INF/LICENSE in " + archive);
            try (InputStream input = zip.getInputStream(licenseEntry)) {
                require(
                        MessageDigest.isEqual(expectedLicense, input.readAllBytes()),
                        "License mismatch in " + archive);
            }
            if (requirePackageRoot) {
                boolean found =
                        zip.stream()
                                .anyMatch(
                                        entry ->
                                                !entry.isDirectory()
                                                        && entry.getName().startsWith(packageRoot));
                require(found, "Missing package root " + packageRoot + " in " + archive);
            }
        }
    }

    private static void verifySha256(Path payload) throws IOException {
        Path sidecar = payload.resolveSibling(payload.getFileName() + ".sha256");
        require(Files.isRegularFile(sidecar), "Missing SHA-256 sidecar for " + payload);
        String recorded = Files.readString(sidecar, StandardCharsets.UTF_8).trim();
        require(recorded.equalsIgnoreCase(sha256(payload)), "SHA-256 mismatch for " + payload);
    }

    private static Element parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static String childText(Element parent, String name) {
        var children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && element.getTagName().equals(name)) {
                return element.getTextContent().trim();
            }
        }
        return "";
    }

    private static Path exactlyOne(List<Path> paths, Predicate<String> match, String description) {
        List<Path> matches =
                paths.stream().filter(path -> match.test(path.getFileName().toString())).toList();
        require(matches.size() == 1, "Expected one " + description + ", found " + matches);
        return matches.getFirst();
    }

    private static boolean isChecksum(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".md5")
                || name.endsWith(".sha1")
                || name.endsWith(".sha256")
                || name.endsWith(".sha512");
    }

    private static Set<String> childDirectories(Path parent) throws IOException {
        if (!Files.isDirectory(parent)) {
            return Set.of();
        }
        try (var paths = Files.list(parent)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static Map<String, Contract> parseContract(List<String> rows) {
        Map<String, Contract> result = new LinkedHashMap<>();
        for (String row : rows) {
            String[] fields = row.split("\\|", -1);
            require(fields.length == 4, "Invalid publication contract row: " + row);
            Contract previous =
                    result.put(
                            fields[0],
                            new Contract(set(fields[1]), set(fields[2]), fields[3]));
            require(previous == null, "Duplicate publication contract: " + fields[0]);
        }
        return result;
    }

    private static Set<String> set(String value) {
        return value.isBlank() ? Set.of() : Set.of(value.split(","));
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GradleException(message);
        }
    }

    private record Contract(
            Set<String> compileDependencies,
            Set<String> runtimeDependencies,
            String packageRoot) {}
}
