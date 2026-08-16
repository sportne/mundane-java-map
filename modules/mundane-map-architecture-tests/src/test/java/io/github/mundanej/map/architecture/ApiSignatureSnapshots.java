package io.github.mundanej.map.architecture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

/** Creates and verifies deterministic pre-publication Java API signature snapshots. */
public final class ApiSignatureSnapshots {
    private static final String FORMAT = "public-protected-java21-v1";
    private static final Set<String> MANIFEST_KEYS =
            Set.of(
                    "artifact",
                    "candidateVersion",
                    "coordinate",
                    "format",
                    "jarSha256",
                    "kind",
                    "moduleMetadataSha256",
                    "pomSha256",
                    "provenance",
                    "signature",
                    "signatureSha256");

    private ApiSignatureSnapshots() {}

    /** Writes baselines only after an explicit maintainer-approved invocation. */
    public static void main(String[] arguments) throws Exception {
        if (!Boolean.getBoolean("map.approveApiBaseline")) {
            throw new IllegalStateException(
                    "writeProvisionalApiBaselines requires -Pmap.approveApiBaseline=true");
        }
        String version = requiredProperty("map.project.version");
        SemanticVersion semanticVersion = SemanticVersion.parse(version);
        if (semanticVersion.major() != 0 || !semanticVersion.snapshot()) {
            throw new IllegalStateException(
                    "provisional baselines are allowed only before first publication");
        }
        Path root = Path.of(requiredProperty("map.apiBaseline.root"));
        Files.createDirectories(root);
        for (Module module : modules()) {
            String signature = signature(module);
            String signatureName = module.artifact() + ".sig";
            Files.writeString(root.resolve(signatureName), signature, StandardCharsets.UTF_8);
            String manifest =
                    "artifact="
                            + module.artifact()
                            + "\n"
                            + "candidateVersion="
                            + version
                            + "\n"
                            + "coordinate=UNPUBLISHED\n"
                            + "format="
                            + FORMAT
                            + "\n"
                            + "jarSha256=UNPUBLISHED\n"
                            + "kind=PROVISIONAL\n"
                            + "moduleMetadataSha256=UNPUBLISHED\n"
                            + "pomSha256=UNPUBLISHED\n"
                            + "provenance=reviewed-source-snapshot:G19-190\n"
                            + "signature="
                            + signatureName
                            + "\n"
                            + "signatureSha256="
                            + sha256(signature.getBytes(StandardCharsets.UTF_8))
                            + "\n";
            Files.writeString(
                    root.resolve(module.artifact() + ".properties"),
                    manifest,
                    StandardCharsets.UTF_8);
        }
    }

    static void verifyAll() throws Exception {
        Path root = Path.of(requiredProperty("map.apiBaseline.root"));
        String currentVersion = requiredProperty("map.project.version");
        validateExceptions(
                Files.readString(
                        Path.of(requiredProperty("map.apiExceptions.file")),
                        StandardCharsets.UTF_8),
                SemanticVersion.parse(currentVersion));
        Set<String> expectedFiles = new HashSet<>();
        List<String> failures = new ArrayList<>();
        for (Module module : modules()) {
            Path manifestPath = root.resolve(module.artifact() + ".properties");
            expectedFiles.add(Objects.requireNonNull(manifestPath.getFileName()).toString());
            Map<String, String> manifest;
            try {
                manifest = parseManifest(manifestPath, module.artifact());
            } catch (IllegalArgumentException | IOException failure) {
                failures.add(module.artifact() + ": " + failure.getMessage());
                continue;
            }
            Path signaturePath = root.resolve(Objects.requireNonNull(manifest.get("signature")));
            expectedFiles.add(Objects.requireNonNull(signaturePath.getFileName()).toString());
            byte[] baselineBytes = Files.readAllBytes(signaturePath);
            if (!sha256(baselineBytes).equals(manifest.get("signatureSha256"))) {
                failures.add(module.artifact() + ": baseline signature checksum mismatch");
                continue;
            }
            String baseline = new String(baselineBytes, StandardCharsets.UTF_8);
            String current = signature(module);
            ChangeKind change = classify(baseline, current);
            if (change != ChangeKind.NONE) {
                SemanticVersion baselineVersion =
                        SemanticVersion.parse(manifest.get("candidateVersion"));
                SemanticVersion candidate = SemanticVersion.parse(currentVersion);
                if (!VersionPolicy.sufficient(baselineVersion, candidate, change)) {
                    failures.add(
                            module.artifact()
                                    + ": API_COMPATIBILITY_DRIFT "
                                    + change
                                    + " requires "
                                    + VersionPolicy.requiredBump(baselineVersion, change)
                                    + "; first difference: "
                                    + firstDifference(baseline, current));
                }
            }
        }
        try (Stream<Path> paths = Files.list(root)) {
            Set<String> actualFiles =
                    paths.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet());
            if (!actualFiles.equals(expectedFiles)) {
                failures.add(
                        "API baseline inventory mismatch: expected "
                                + expectedFiles.stream().sorted().toList()
                                + ", found "
                                + actualFiles.stream().sorted().toList());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
    }

    static Map<String, String> parseManifest(Path path, String artifact) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("missing baseline manifest");
        }
        Map<String, String> values = new TreeMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException("invalid baseline manifest line");
            }
            String previous =
                    values.put(line.substring(0, separator), line.substring(separator + 1));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate baseline manifest key");
            }
        }
        if (!values.keySet().equals(MANIFEST_KEYS)) {
            throw new IllegalArgumentException("baseline manifest keys changed");
        }
        if (!artifact.equals(values.get("artifact")) || !FORMAT.equals(values.get("format"))) {
            throw new IllegalArgumentException("baseline artifact or format mismatch");
        }
        SemanticVersion.parse(values.get("candidateVersion"));
        String kind = values.get("kind");
        if ("PROVISIONAL".equals(kind)) {
            requireUnpublished(values, "coordinate");
            requireUnpublished(values, "jarSha256");
            requireUnpublished(values, "pomSha256");
            requireUnpublished(values, "moduleMetadataSha256");
        } else if ("RELEASE".equals(kind)) {
            if (!values.get("coordinate")
                    .matches("io\\.github\\.mundanej:[a-z0-9-]+:[0-9]+\\.[0-9]+\\.[0-9]+")) {
                throw new IllegalArgumentException("released baseline coordinate is invalid");
            }
            for (String key : List.of("jarSha256", "pomSha256", "moduleMetadataSha256")) {
                if (!values.get(key).matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException(
                            "released baseline digest is invalid: " + key);
                }
            }
        } else {
            throw new IllegalArgumentException("unknown baseline kind");
        }
        if (!values.get("signature").equals(artifact + ".sig")
                || !values.get("signatureSha256").matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("baseline signature provenance is invalid");
        }
        return Map.copyOf(values);
    }

    static String signature(Module module) throws Exception {
        List<String> names = publicClasses(module.classesDirectory());
        ToolProvider javap =
                ToolProvider.findFirst("javap")
                        .orElseThrow(
                                () -> new IllegalStateException("JDK javap tool is unavailable"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        List<String> arguments =
                new ArrayList<>(
                        List.of(
                                "-public",
                                "-constants",
                                "-s",
                                "-classpath",
                                System.getProperty("java.class.path")));
        arguments.addAll(names);
        int result;
        try (PrintWriter standard = new PrintWriter(output, true, StandardCharsets.UTF_8);
                PrintWriter error = new PrintWriter(errors, true, StandardCharsets.UTF_8)) {
            result = javap.run(standard, error, arguments.toArray(String[]::new));
        }
        if (result != 0) {
            throw new IllegalStateException(
                    "javap failed for "
                            + module.artifact()
                            + ": "
                            + errors.toString(StandardCharsets.UTF_8));
        }
        StringBuilder normalized = new StringBuilder();
        output.toString(StandardCharsets.UTF_8)
                .lines()
                .map(String::stripTrailing)
                .filter(line -> !line.startsWith("Compiled from "))
                .filter(line -> !line.isBlank())
                .forEach(line -> normalized.append(line).append('\n'));
        for (String name : names) {
            Class<?> type =
                    Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            normalized.append(shape(type)).append('\n');
        }
        return normalized.toString();
    }

    static ChangeKind classify(String baseline, String current) {
        if (baseline.equals(current)) {
            return ChangeKind.NONE;
        }
        Set<String> oldLines = new HashSet<>(Arrays.asList(baseline.split("\\R")));
        Set<String> newLines = new HashSet<>(Arrays.asList(current.split("\\R")));
        Set<String> removed = new HashSet<>(oldLines);
        removed.removeAll(newLines);
        return removed.isEmpty() ? ChangeKind.ADDITION : ChangeKind.BREAKING;
    }

    static void validateExceptions(String content, SemanticVersion current) {
        List<String> lines = content.lines().toList();
        String header =
                "artifact\tdifferenceCode\telement\texpiresAfter\trationale\tmigration\treleaseNote\tapproval";
        if (lines.isEmpty() || !header.equals(lines.getFirst())) {
            throw new IllegalArgumentException("compatibility exception header changed");
        }
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            if (fields.length != 8
                    || Arrays.stream(fields).anyMatch(String::isBlank)
                    || Arrays.stream(fields).anyMatch(field -> field.contains("*"))) {
                throw new IllegalArgumentException(
                        "compatibility exception must be exact and complete");
            }
            SemanticVersion expiry = SemanticVersion.parse(fields[3]);
            if (VersionPolicy.compare(expiry, current) <= 0) {
                throw new IllegalArgumentException("compatibility exception is expired");
            }
            if (!fields[6].contains("/") || !fields[7].matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException(
                        "compatibility exception review linkage is invalid");
            }
        }
    }

    private static List<String> publicClasses(Path classes) throws Exception {
        List<String> names;
        try (Stream<Path> paths = Files.walk(classes)) {
            names =
                    paths.filter(path -> path.toString().endsWith(".class"))
                            .map(classes::relativize)
                            .map(Path::toString)
                            .map(name -> name.substring(0, name.length() - 6))
                            .map(name -> name.replace('/', '.').replace('\\', '.'))
                            .filter(
                                    name ->
                                            !name.equals("module-info")
                                                    && !name.endsWith("package-info"))
                            .sorted()
                            .toList();
        }
        List<String> result = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String name : names) {
            Class<?> type = Class.forName(name, false, loader);
            int modifiers = type.getModifiers();
            if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
                    && !type.isSynthetic()) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }

    private static String shape(Class<?> type) {
        Class<?>[] permittedSubclasses = type.getPermittedSubclasses();
        List<String> permits =
                permittedSubclasses == null
                        ? List.of()
                        : Arrays.stream(permittedSubclasses).map(Class::getName).sorted().toList();
        List<String> record =
                type.isRecord()
                        ? Arrays.stream(type.getRecordComponents())
                                .map(ApiSignatureSnapshots::recordComponent)
                                .toList()
                        : List.of();
        List<String> constants =
                type.isEnum()
                        ? Arrays.stream(type.getEnumConstants())
                                .map(value -> ((Enum<?>) value).name())
                                .toList()
                        : List.of();
        List<String> members = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (visible(field.getModifiers()) && !field.isSynthetic()) {
                members.add(
                        "field:" + field.getName() + annotations(field.getDeclaredAnnotations()));
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (visible(constructor.getModifiers()) && !constructor.isSynthetic()) {
                members.add("constructor:" + executable(constructor));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (visible(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) {
                members.add("method:" + method.getName() + executable(method));
            }
        }
        members.sort(String::compareTo);
        return "SHAPE "
                + type.getName()
                + " sealed="
                + type.isSealed()
                + " permits="
                + permits
                + " record="
                + record
                + " enum="
                + constants
                + " annotations="
                + annotations(type.getDeclaredAnnotations())
                + " members="
                + members;
    }

    private static String recordComponent(RecordComponent component) {
        return component.getName()
                + ':'
                + component.getGenericType().getTypeName()
                + annotations(component.getDeclaredAnnotations());
    }

    private static String executable(Executable executable) {
        List<String> parameters =
                Arrays.stream(executable.getGenericParameterTypes())
                        .map(java.lang.reflect.Type::getTypeName)
                        .toList();
        List<String> parameterAnnotations =
                Arrays.stream(executable.getParameterAnnotations())
                        .map(ApiSignatureSnapshots::annotations)
                        .toList();
        return parameters
                + " throws="
                + Arrays.stream(executable.getGenericExceptionTypes())
                        .map(java.lang.reflect.Type::getTypeName)
                        .sorted()
                        .toList()
                + " annotations="
                + annotations(executable.getDeclaredAnnotations())
                + " parameterAnnotations="
                + parameterAnnotations;
    }

    private static String annotations(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .sorted(Comparator.comparing(annotation -> annotation.annotationType().getName()))
                .map(Annotation::toString)
                .toList()
                .toString();
    }

    private static boolean visible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String firstDifference(String baseline, String current) {
        List<String> oldLines = baseline.lines().toList();
        List<String> newLines = current.lines().toList();
        int count = Math.min(oldLines.size(), newLines.size());
        for (int index = 0; index < count; index++) {
            if (!oldLines.get(index).equals(newLines.get(index))) {
                return "old=" + oldLines.get(index) + ", new=" + newLines.get(index);
            }
        }
        return "line-count old=" + oldLines.size() + ", new=" + newLines.size();
    }

    private static List<Module> modules() {
        return requiredProperty("map.apiBaseline.modules")
                .lines()
                .filter(line -> !line.isBlank())
                .map(
                        line -> {
                            String[] fields = line.split("\\t", -1);
                            if (fields.length != 2 || fields[0].isBlank()) {
                                throw new IllegalArgumentException("invalid API module descriptor");
                            }
                            return new Module(fields[0], Path.of(fields[1]));
                        })
                .sorted(Comparator.comparing(Module::artifact))
                .toList();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing system property " + name);
        }
        return value;
    }

    private static void requireUnpublished(Map<String, String> values, String key) {
        if (!"UNPUBLISHED".equals(values.get(key))) {
            throw new IllegalArgumentException("provisional baseline cannot claim " + key);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    enum ChangeKind {
        NONE,
        ADDITION,
        BREAKING
    }

    record Module(String artifact, Path classesDirectory) {}

    record SemanticVersion(int major, int minor, int patch, boolean snapshot) {
        static SemanticVersion parse(String value) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(
                                    "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-SNAPSHOT)?")
                            .matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid governed semantic version: " + value);
            }
            return new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    matcher.group(4) != null);
        }

        String core() {
            return String.format(Locale.ROOT, "%d.%d.%d", major, minor, patch);
        }
    }

    static final class VersionPolicy {
        private VersionPolicy() {}

        static boolean sufficient(
                SemanticVersion baseline, SemanticVersion candidate, ChangeKind change) {
            if (compare(candidate, baseline) <= 0) {
                return false;
            }
            if (baseline.major() == 0) {
                if (change == ChangeKind.BREAKING) {
                    return candidate.major() > 0
                            || (candidate.major() == 0
                                    && candidate.minor() > baseline.minor()
                                    && candidate.patch() == 0);
                }
                return candidate.major() > 0
                        || candidate.minor() > baseline.minor()
                        || candidate.patch() > baseline.patch();
            }
            if (change == ChangeKind.BREAKING) {
                return candidate.major() > baseline.major();
            }
            return candidate.major() > baseline.major()
                    || (candidate.major() == baseline.major()
                            && candidate.minor() > baseline.minor());
        }

        static String requiredBump(SemanticVersion baseline, ChangeKind change) {
            if (baseline.major() == 0 && change == ChangeKind.BREAKING) {
                return "0." + (baseline.minor() + 1) + ".0";
            }
            if (baseline.major() == 0) {
                return "0." + baseline.minor() + '.' + (baseline.patch() + 1);
            }
            if (change == ChangeKind.BREAKING) {
                return (baseline.major() + 1) + ".0.0";
            }
            return baseline.major() + "." + (baseline.minor() + 1) + ".0";
        }

        static int compare(SemanticVersion left, SemanticVersion right) {
            int major = Integer.compare(left.major(), right.major());
            int minor = Integer.compare(left.minor(), right.minor());
            int patch = Integer.compare(left.patch(), right.patch());
            return major != 0 ? major : minor != 0 ? minor : patch;
        }
    }
}
