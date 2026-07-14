package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.mundanej.map.architecture.ArchitecturePolicy.ModuleDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    private static final String FIXTURE_PACKAGE = "io.github.mundanej.map.architecture.fixture.";

    private static List<ModuleDescriptor> modules;
    private static Map<ModuleDescriptor, JavaClasses> classesByModule;
    private static JavaClasses fixtureClasses;
    private static JavaClasses nativeSupportClasses;
    private static List<Path> nativeSupportResources;

    @BeforeAll
    static void importClasses() {
        modules = parseModules(System.getProperty("map.architecture.modules"));
        classesByModule = new HashMap<>();
        for (ModuleDescriptor module : modules) {
            classesByModule.put(
                    module, new ClassFileImporter().importPath(module.classesDirectory()));
        }
        fixtureClasses =
                new ClassFileImporter()
                        .importPath(Path.of(System.getProperty("map.architecture.fixtureClasses")));
        nativeSupportClasses =
                new ClassFileImporter()
                        .importPath(
                                Path.of(
                                        System.getProperty(
                                                "map.architecture.nativeSupportClasses")));
        nativeSupportResources =
                List.of(
                                System.getProperty("map.architecture.nativeSupportResources")
                                        .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                        .stream()
                        .map(Path::of)
                        .toList();
    }

    @Test
    void actualProductionSatisfiesModuleAndToolkitBoundaries() {
        Map<String, String> owners = owningModules(classesByModule);
        List<String> violations = new ArrayList<>();
        classesByModule.forEach(
                (module, classes) ->
                        violations.addAll(
                                ArchitecturePolicy.moduleBoundaryViolations(
                                        module, classes, owners)));

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void actualPublicApiSignaturesRemainExternalTypeFree() {
        ModuleDescriptor api = moduleEndingWith("mundane-map-api");
        List<String> violations = ArchitecturePolicy.publicApiViolations(classesByModule.get(api));

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void actualNativeTargetedCodeAvoidsProhibitedMechanisms() {
        List<String> violations = new ArrayList<>();
        classesByModule.forEach(
                (module, classes) -> {
                    if (module.nativeTarget()) {
                        violations.addAll(
                                ArchitecturePolicy.prohibitedMechanismViolations(classes));
                    }
                });

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeSmokeSupportAvoidsProhibitedMechanisms() {
        List<String> violations =
                ArchitecturePolicy.prohibitedMechanismViolations(nativeSupportClasses);

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void actualProductionResourcesAvoidImplicitDiscoveryMetadata() throws IOException {
        List<String> violations = new ArrayList<>();
        for (ModuleDescriptor module : modules) {
            if (module.nativeTarget()) {
                for (Path resourcesDirectory : module.resourcesDirectories()) {
                    violations.addAll(
                            ArchitecturePolicy.discoveryResourceViolations(
                                    module.path(), resourcesDirectory));
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeSmokeResourcesAvoidImplicitDiscoveryMetadata() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path resourcesDirectory : nativeSupportResources) {
            violations.addAll(
                    ArchitecturePolicy.discoveryResourceViolations(
                            ":modules:mundane-map-native-tests", resourcesDirectory));
        }

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeSmokeResourceInventoryIsExplicit() throws IOException {
        Path sourceResources =
                nativeSupportResources.stream()
                        .filter(path -> path.endsWith(Path.of("src", "main", "resources")))
                        .findFirst()
                        .orElseThrow();
        Set<String> actual;
        try (var paths = java.nio.file.Files.walk(sourceResources)) {
            actual =
                    paths.filter(java.nio.file.Files::isRegularFile)
                            .map(sourceResources::relativize)
                            .map(path -> path.toString().replace('\\', '/'))
                            .collect(Collectors.toUnmodifiableSet());
        }

        assertEquals(
                Set.of(
                        "META-INF/native-image/io.github.mundanej/mundane-map-native-tests/"
                                + "jni-config.json",
                        "META-INF/native-image/io.github.mundanej/mundane-map-native-tests/"
                                + "reflect-config.json",
                        "META-INF/native-image/io.github.mundanej/mundane-map-native-tests/"
                                + "resource-config.json",
                        "io/github/mundanej/map/nativeimage/symbol-smoke-4x2.rgba",
                        "io/github/mundanej/map/nativeimage/"
                                + "symbol-smoke-4x2.rgba.provenance.txt"),
                actual);
    }

    @Test
    void dependencyAndToolkitFixturesAreRejectedWithStableDiagnostics() {
        Map<String, String> owners = owningModules(classesByModule);
        assertModuleFixtureRejected(
                "BoundaryFixtures$ApiToCore", fakeModule("mundane-map-api", Set.of()), owners);
        assertModuleFixtureRejected(
                "BoundaryFixtures$NonAwtToDesktop",
                fakeModule("mundane-map-core", Set.of(":modules:mundane-map-api")),
                owners);
        assertModuleFixtureRejected(
                "BoundaryFixtures$IoToAwt",
                fakeModule("mundane-map-io-fixture", Set.of(":modules:mundane-map-api")),
                owners);
        assertModuleFixtureRejected(
                "BoundaryFixtures$IoToDesktop",
                fakeModule("mundane-map-io-fixture", Set.of(":modules:mundane-map-api")),
                owners);

        JavaClass allowed = fixture("BoundaryFixtures$AwtToCore");
        ModuleDescriptor awt =
                fakeModule(
                        "mundane-map-awt",
                        Set.of(":modules:mundane-map-api", ":modules:mundane-map-core"));
        assertTrue(
                ArchitecturePolicy.moduleBoundaryViolations(awt, List.of(allowed), owners)
                        .isEmpty());
    }

    @Test
    void externalPublicSignatureFixtureIsRejected() {
        JavaClass fixture = fixture("BoundaryFixtures$PublicApiLeak");
        List<String> violations = ArchitecturePolicy.publicApiViolations(List.of(fixture));

        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).contains(fixture.getName()));
        assertTrue(violations.get(0).contains("com.example.external.ExternalType"));

        JavaClass allowed = fixture("BoundaryFixtures$PublicApiSubpackageUse");
        assertTrue(ArchitecturePolicy.publicApiViolations(List.of(allowed)).isEmpty());
    }

    @Test
    void everyProhibitedMechanismFixtureIsRejected() {
        List<String> rejectedFixtures =
                List.of(
                        "ReflectionUse",
                        "ReflectionEnumerationUse",
                        "ClassMetadataUse",
                        "MethodHandleUse",
                        "DynamicProxyUse",
                        "DynamicClassLoadingUse",
                        "DynamicDefinitionUse",
                        "ServiceDiscoveryUse",
                        "SerializationUse",
                        "ResourceEnumerationUse",
                        "NativeMethodUse",
                        "UnsafeUse",
                        "NativeLibraryUse",
                        "GlobalRegistryUse",
                        "CustomGlobalRegistryUse");
        for (String simpleName : rejectedFixtures) {
            JavaClass fixture = fixture("MechanismFixtures$" + simpleName);
            List<String> violations =
                    ArchitecturePolicy.prohibitedMechanismViolations(List.of(fixture));
            assertFalse(violations.isEmpty(), fixture.getName());
            assertTrue(
                    violations.stream().allMatch(value -> value.contains(fixture.getName())),
                    () -> String.join("\n", violations));
        }

        JavaClass allowed = fixture("MechanismFixtures$ExplicitResourceUse");
        assertTrue(ArchitecturePolicy.prohibitedMechanismViolations(List.of(allowed)).isEmpty());
        JavaClass stateless = fixture("MechanismFixtures$StatelessRegistrationUse");
        assertTrue(ArchitecturePolicy.prohibitedMechanismViolations(List.of(stateless)).isEmpty());
        JavaClass instanceRegistry =
                fixture("MechanismFixtures$ImmutableCatalogAndInstanceRegistryUse");
        assertTrue(
                ArchitecturePolicy.prohibitedMechanismViolations(List.of(instanceRegistry))
                        .isEmpty());
    }

    @Test
    void serviceDescriptorFixtureIsRejected() throws IOException {
        Path resources = Path.of(System.getProperty("map.architecture.fixtureResources"));
        List<String> violations =
                ArchitecturePolicy.discoveryResourceViolations(":fixtures:service", resources);

        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).contains(":fixtures:service"));
        assertTrue(violations.get(0).contains("META-INF/services/example.Service"));
    }

    private static void assertModuleFixtureRejected(
            String simpleName, ModuleDescriptor module, Map<String, String> owners) {
        JavaClass fixture = fixture(simpleName);
        List<String> violations =
                ArchitecturePolicy.moduleBoundaryViolations(module, List.of(fixture), owners);
        assertFalse(violations.isEmpty(), fixture.getName());
        assertTrue(
                violations.stream().anyMatch(value -> value.contains(fixture.getName())),
                () -> String.join("\n", violations));
    }

    private static JavaClass fixture(String simpleName) {
        return fixtureClasses.get(FIXTURE_PACKAGE + simpleName);
    }

    private static ModuleDescriptor fakeModule(String name, Set<String> allowedProjects) {
        return new ModuleDescriptor(
                ":fixtures:" + name,
                "JDK_RUNTIME",
                1,
                true,
                Path.of("."),
                Set.of(Path.of(".")),
                allowedProjects);
    }

    private static ModuleDescriptor moduleEndingWith(String suffix) {
        return modules.stream()
                .filter(module -> module.path().endsWith(suffix))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, String> owningModules(
            Map<ModuleDescriptor, JavaClasses> importedClasses) {
        Map<String, String> result = new HashMap<>();
        importedClasses.forEach(
                (module, classes) ->
                        classes.forEach(
                                javaClass -> result.put(javaClass.getName(), module.path())));
        return Map.copyOf(result);
    }

    private static List<ModuleDescriptor> parseModules(String encoded) {
        return encoded.lines()
                .filter(Predicate.not(String::isBlank))
                .map(
                        line -> {
                            String[] fields = line.split("\\t", -1);
                            Set<String> allowed =
                                    fields[6].isBlank() ? Set.of() : Set.of(fields[6].split(","));
                            return new ModuleDescriptor(
                                    fields[0],
                                    fields[1],
                                    Integer.parseInt(fields[2]),
                                    Boolean.parseBoolean(fields[3]),
                                    Path.of(fields[4]),
                                    Set.of(fields[5].split(java.io.File.pathSeparator)).stream()
                                            .map(Path::of)
                                            .collect(Collectors.toUnmodifiableSet()),
                                    allowed);
                        })
                .collect(Collectors.toUnmodifiableList());
    }
}
