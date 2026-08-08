package io.github.mundanej.map.buildlogic;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
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
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
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
    /** Creates a task instance whose properties Gradle configures before execution. */
    @Inject
    public VerifyPublicationRepository() {}

    /**
     * Provides dependencies required to inspect published class visibility without initialization.
     *
     * @return publication surface-inspection classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getSurfaceClasspath();

    /**
     * Provides the staged Maven repository to inspect.
     *
     * @return staged repository directory
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRepositoryDirectory();

    /**
     * Provides the exact published-module and dependency contract rows.
     *
     * @return release contract rows
     */
    @Input
    public abstract ListProperty<String> getReleaseContract();

    /**
     * Provides the version expected on every staged publication.
     *
     * @return expected publication version
     */
    @Input
    public abstract Property<String> getPublicationVersion();

    /**
     * Provides the project license that must appear unchanged in every archive.
     *
     * @return expected license file
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLicenseFile();

    /**
     * Provides the deterministic artifact manifest written after successful verification.
     *
     * @return artifact-manifest output file
     */
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
        verifyDocumentedSurfaces(
                group, getPublicationVersion().get(), contract, getSurfaceClasspath().getFiles());
        Path output = getArtifactManifest().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                String.join("\n", manifest.stream().sorted().toList()) + "\n",
                StandardCharsets.UTF_8);
    }

    private static void verifyDocumentedSurfaces(
            Path group, String version, Map<String, Contract> contract, Set<java.io.File> classpath)
            throws Exception {
        Map<String, PublicationArchives> archives = new LinkedHashMap<>();
        for (var entry : contract.entrySet()) {
            Path versionDirectory = group.resolve(entry.getKey()).resolve(version);
            List<Path> payloads;
            try (var paths = Files.list(versionDirectory)) {
                payloads = paths.filter(Files::isRegularFile).toList();
            }
            archives.put(
                    entry.getKey(),
                    new PublicationArchives(
                            exactlyOne(
                                    payloads,
                                    name ->
                                            name.endsWith(".jar")
                                                    && !name.endsWith("-sources.jar")
                                                    && !name.endsWith("-javadoc.jar"),
                                    entry.getKey() + " binary"),
                            exactlyOne(
                                    payloads,
                                    name -> name.endsWith("-sources.jar"),
                                    entry.getKey() + " sources"),
                            exactlyOne(
                                    payloads,
                                    name -> name.endsWith("-javadoc.jar"),
                                    entry.getKey() + " Javadocs")));
        }
        List<URL> binaryUrls = new ArrayList<>();
        archives.values().stream()
                .map(PublicationArchives::binary)
                .map(VerifyPublicationRepository::toUrl)
                .forEach(binaryUrls::add);
        classpath.stream()
                .map(java.io.File::toPath)
                .map(VerifyPublicationRepository::toUrl)
                .forEach(binaryUrls::add);
        try (URLClassLoader loader =
                new URLClassLoader(
                        binaryUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            for (var entry : archives.entrySet()) {
                PublicationArchives module = entry.getValue();
                verifyDocumentedSurface(
                        module.binary(),
                        module.sources(),
                        module.javadocs(),
                        contract.get(entry.getKey()).packageRoot(),
                        loader);
            }
        }
    }

    static void verifyDocumentedSurface(
            Path binary, Path sources, Path javadocs, String packageRoot) throws Exception {
        try (URLClassLoader loader =
                new URLClassLoader(
                        new URL[] {toUrl(binary)}, ClassLoader.getPlatformClassLoader())) {
            verifyDocumentedSurface(binary, sources, javadocs, packageRoot, loader);
        }
    }

    private static void verifyDocumentedSurface(
            Path binary,
            Path sources,
            Path javadocs,
            String packageRoot,
            ClassLoader loader)
            throws Exception {
        Set<String> binaryEntries = archiveEntries(binary, packageRoot, ".class");
        Set<String> binaryTopLevel =
                binaryEntries.stream()
                        .filter(name -> !name.contains("$"))
                        .filter(name -> !name.endsWith("package-info.class"))
                        .map(name -> name.substring(0, name.length() - ".class".length()))
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> sourceTopLevel =
                archiveEntries(sources, packageRoot, ".java").stream()
                        .filter(name -> !name.endsWith("package-info.java"))
                        .map(name -> name.substring(0, name.length() - ".java".length()))
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        require(
                sourceTopLevel.equals(binaryTopLevel),
                "Source surface mismatch for "
                        + binary
                        + ": expected "
                        + binaryTopLevel
                        + ", found "
                        + sourceTopLevel);

        Set<String> expectedJavadocs = new TreeSet<>();
        for (String entry : binaryEntries) {
            if (entry.endsWith("package-info.class") || entry.endsWith("module-info.class")) {
                continue;
            }
            String className =
                    entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
            Class<?> type = Class.forName(className, false, loader);
            if (isDocumentedType(type)) {
                expectedJavadocs.add(
                        className.replace('.', '/').replace('$', '.') + ".html");
            }
        }
        Set<String> actualJavadocs =
                archiveEntries(javadocs, packageRoot, ".html").stream()
                        .filter(name -> !name.contains("/class-use/"))
                        .filter(name -> !name.contains("/doc-files/"))
                        .filter(name -> !name.endsWith("/package-summary.html"))
                        .filter(name -> !name.endsWith("/package-tree.html"))
                        .filter(name -> !name.endsWith("/package-use.html"))
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        require(
                actualJavadocs.equals(expectedJavadocs),
                "Javadoc surface mismatch for "
                        + binary
                        + ": expected "
                        + expectedJavadocs
                        + ", found "
                        + actualJavadocs);
    }

    private static Set<String> archiveEntries(Path archive, String packageRoot, String suffix)
            throws IOException {
        try (var zip = new ZipFile(archive.toFile())) {
            return zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(packageRoot) && name.endsWith(suffix))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static boolean isDocumentedType(Class<?> type) {
        if (type.isSynthetic() || type.isAnonymousClass() || type.isLocalClass()) {
            return false;
        }
        int modifiers = type.getModifiers();
        if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)) {
            return false;
        }
        Class<?> enclosing = type.getEnclosingClass();
        return enclosing == null || isDocumentedType(enclosing);
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new GradleException("Invalid publication path " + path, exception);
        }
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
                            .filter(
                                    path ->
                                            !isChecksum(path)
                                                    && !path.getFileName()
                                                            .toString()
                                                            .equals("maven-metadata.xml"))
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
        verifyMetadata(metadata, contract);
        verifyArchive(
                binary,
                contract.packageRoot(),
                license,
                true,
                contract.exactBinaryEntries());
        verifyArchive(sources, contract.packageRoot(), license, true, Set.of());
        verifyArchive(javadoc, contract.packageRoot(), license, false, Set.of());
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
        Set<String> externalCompile = new TreeSet<>();
        Set<String> externalRuntime = new TreeSet<>();
        for (Element dependency : dependencyElements(project)) {
            String group = childText(dependency, "groupId");
            String artifact = childText(dependency, "artifactId");
            String dependencyVersion = childText(dependency, "version");
            String scope = childText(dependency, "scope");
            if ("io.github.mundanej".equals(group)) {
                dependencies.computeIfAbsent(scope, ignored -> new TreeSet<>()).add(artifact);
            } else {
                require(
                        "compile".equals(scope) || "runtime".equals(scope),
                        "External dependency must be compile or runtime scoped in " + pom);
                String classifier = childText(dependency, "classifier");
                String coordinate =
                        group
                                + ":"
                                + artifact
                                + ":"
                                + dependencyVersion
                                + (classifier.isBlank() ? "" : "@" + classifier);
                ("compile".equals(scope) ? externalCompile : externalRuntime).add(coordinate);
            }
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
        require(
                externalCompile.equals(contract.externalCompileDependencies()),
                "External compile dependency mismatch in " + pom + ": " + externalCompile);
        require(
                externalRuntime.equals(contract.externalRuntimeDependencies()),
                "External runtime dependency mismatch in " + pom + ": " + externalRuntime);
        require(
                managedImports(project).equals(contract.managedImports()),
                "Managed import mismatch in " + pom + ": " + managedImports(project));
    }

    private static void verifyArchive(
            Path archive,
            String packageRoot,
            Path license,
            boolean requirePackageRoot,
            Set<String> exactEntries)
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
            if (!exactEntries.isEmpty()) {
                Set<String> actualEntries =
                        zip.stream()
                                .filter(entry -> !entry.isDirectory())
                                .map(java.util.zip.ZipEntry::getName)
                                .collect(
                                        java.util.stream.Collectors.toCollection(TreeSet::new));
                require(
                        actualEntries.equals(exactEntries),
                        "Binary entry mismatch in "
                                + archive
                                + ": expected "
                                + exactEntries
                                + ", found "
                                + actualEntries);
            }
        }
    }

    private static void verifyMetadata(Path metadata, Contract contract) throws IOException {
        if (contract.exactBinaryEntries().isEmpty()) {
            return;
        }
        String content = Files.readString(metadata, StandardCharsets.UTF_8);
        require(
                !content.contains("testFixtures") && !content.contains("-test-fixtures"),
                "Test-fixture variant leaked into " + metadata);
    }

    private record PublicationArchives(Path binary, Path sources, Path javadocs) {}

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

    private static Element childElement(Element parent, String name) {
        var children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && element.getTagName().equals(name)) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> dependencyElements(Element project) {
        Element dependencies = childElement(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>();
        var children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element
                    && element.getTagName().equals("dependency")) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> managedImports(Element project) {
        Element management = childElement(project, "dependencyManagement");
        if (management == null) {
            return Set.of();
        }
        Element dependencies = childElement(management, "dependencies");
        if (dependencies == null) {
            return Set.of();
        }
        Set<String> result = new TreeSet<>();
        var children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element dependency
                    && dependency.getTagName().equals("dependency")) {
                require(
                        "pom".equals(childText(dependency, "type"))
                                && "import".equals(childText(dependency, "scope")),
                        "Managed dependency must be a POM import");
                result.add(
                        childText(dependency, "groupId")
                                + ":"
                                + childText(dependency, "artifactId")
                                + ":"
                                + childText(dependency, "version"));
            }
        }
        return Set.copyOf(result);
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
            require(
                    fields.length >= 5 && fields.length <= 8,
                    "Invalid publication contract row: " + row);
            Contract previous =
                    result.put(
                            fields[0],
                            new Contract(
                                    set(fields[1]),
                                    set(fields[2]),
                                    set(fields[3]),
                                    fields[4],
                                    fields.length >= 6 ? set(fields[5]) : Set.of(),
                                    fields.length >= 7 ? set(fields[6]) : Set.of(),
                                    fields.length == 8 ? set(fields[7]) : Set.of()));
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
            Set<String> externalRuntimeDependencies,
            String packageRoot,
            Set<String> exactBinaryEntries,
            Set<String> externalCompileDependencies,
            Set<String> managedImports) {}
}
