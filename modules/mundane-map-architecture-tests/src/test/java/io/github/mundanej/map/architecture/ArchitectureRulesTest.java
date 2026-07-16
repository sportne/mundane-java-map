package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.mundanej.map.architecture.ArchitecturePolicy.ModuleDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private static final String NATIVE_METADATA_DIRECTORY =
            "META-INF/native-image/io.github.mundanej/mundane-map-native-tests/";
    private static final String NATIVE_RESOURCE_DIRECTORY = "io/github/mundanej/map/nativeimage/";
    private static final Set<String> NATIVE_SHAPEFILE_RESOURCES =
            Set.of(
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/polygon-smoke.shp",
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/polygon-smoke.shx",
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/polygon-smoke.dbf",
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/polygon-smoke.cpg",
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/polygon-smoke.prj",
                    NATIVE_RESOURCE_DIRECTORY + "shapefile/malformed-record.shp");
    private static final Set<String> NATIVE_RASTER_RESOURCES =
            Set.of(
                    NATIVE_RESOURCE_DIRECTORY + "raster/png-affine-smoke.png",
                    NATIVE_RESOURCE_DIRECTORY + "raster/png-affine-smoke.pgw",
                    NATIVE_RESOURCE_DIRECTORY + "raster/jpeg-affine-smoke.jpg",
                    NATIVE_RESOURCE_DIRECTORY + "raster/jpeg-affine-smoke.jgw",
                    NATIVE_RESOURCE_DIRECTORY + "raster/malformed-idat-crc.png");

    private static List<ModuleDescriptor> modules;
    private static Map<ModuleDescriptor, JavaClasses> classesByModule;
    private static JavaClasses fixtureClasses;
    private static JavaClasses nativeSupportClasses;
    private static Path nativeSupportSources;
    private static List<Path> nativeSupportResources;
    private static Path performanceSources;
    private static Path performanceResources;
    private static Path performanceBuild;
    private static Path rootBuild;
    private static Path settings;

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
        nativeSupportSources = Path.of(System.getProperty("map.architecture.nativeSupportSources"));
        nativeSupportResources =
                List.of(
                                System.getProperty("map.architecture.nativeSupportResources")
                                        .split(java.util.regex.Pattern.quote(File.pathSeparator)))
                        .stream()
                        .map(Path::of)
                        .toList();
        performanceSources = Path.of(System.getProperty("map.architecture.performanceSources"));
        performanceResources = Path.of(System.getProperty("map.architecture.performanceResources"));
        performanceBuild = Path.of(System.getProperty("map.architecture.performanceBuild"));
        rootBuild = Path.of(System.getProperty("map.architecture.rootBuild"));
        settings = Path.of(System.getProperty("map.architecture.settings"));
    }

    @Test
    void performanceEvidenceIsSupportOnlyExplicitOfflineAndNonPublished() throws IOException {
        String supportProjects = System.getProperty("map.architecture.supportProjects");
        assertTrue(supportProjects.contains(":modules:mundane-map-performance-tests"));
        String inventory = Files.readString(settings);
        assertTrue(
                inventory.contains(
                        "[path: ':modules:mundane-map-performance-tests', category: 'SUPPORT'"));
        String moduleBuild = Files.readString(performanceBuild);
        assertFalse(moduleBuild.contains("mundane-map.publishing-conventions"));
        assertTrue(moduleBuild.contains("verifyPerformanceLaneIsolation"));
        assertTrue(moduleBuild.contains("runPerformanceEvidence"));
        assertTrue(moduleBuild.contains("performanceJfr"));
        String root = Files.readString(rootBuild);
        assertTrue(root.contains("tasks.register('performanceEvidence')"));
        assertTrue(
                root.contains(
                        "dependsOn ':modules:mundane-map-performance-tests:runPerformanceEvidence'"));

        List<String> sourceText;
        try (var paths = Files.walk(performanceSources)) {
            sourceText =
                    paths.filter(path -> path.toString().endsWith(".java"))
                            .map(
                                    path -> {
                                        try {
                                            return Files.readString(path);
                                        } catch (IOException failure) {
                                            throw new java.io.UncheckedIOException(failure);
                                        }
                                    })
                            .toList();
        }
        String joined = String.join("\n", sourceText);
        assertFalse(joined.contains("java.net."));
        assertFalse(joined.contains("ProcessBuilder"));
        assertFalse(joined.contains("Runtime.getRuntime().exec"));
        assertFalse(joined.contains("ServiceLoader"));
        assertFalse(joined.contains("Class.forName"));
        assertEquals(
                Set.of(
                        "io/github/mundanej/map/performance/fixture/raster-1024x768-v1/evidence.png",
                        "io/github/mundanej/map/performance/fixture/raster-1024x768-v1/evidence.jpg",
                        "io/github/mundanej/map/performance/fixture/raster-1024x768-v1/evidence.pgw",
                        "io/github/mundanej/map/performance/fixture/raster-1024x768-v1/evidence.jgw",
                        "io/github/mundanej/map/performance/fixture/raster-1024x768-v1/PROVENANCE.md"),
                resourceInventory(performanceResources));
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
    void crsRegistryAndProjectionBoundaryRemainExplicitAndNativeSafe() {
        ModuleDescriptor api = moduleEndingWith("mundane-map-api");
        ModuleDescriptor core = moduleEndingWith("mundane-map-core");
        JavaClass projection =
                classesByModule.get(api).get("io.github.mundanej.map.api.Projection");
        JavaClass registry =
                classesByModule.get(core).get("io.github.mundanej.map.core.CrsRegistry");

        assertTrue(
                projection.getMethods().stream()
                        .filter(method -> method.getOwner().equals(projection))
                        .allMatch(method -> method.getModifiers().contains(JavaModifier.ABSTRACT)),
                "Projection must not acquire an unsafe default transform");
        assertTrue(
                registry.getFields().stream()
                        .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                        .allMatch(field -> field.getModifiers().contains(JavaModifier.FINAL)),
                "CrsRegistry must not acquire mutable static state");

        List<JavaClass> crsClasses =
                classesByModule.get(core).stream()
                        .filter(
                                type ->
                                        type.getSimpleName().contains("Crs")
                                                || type.getSimpleName().contains("Projection"))
                        .toList();
        assertTrue(
                ArchitecturePolicy.prohibitedMechanismViolations(crsClasses).isEmpty(),
                "CRS production code must remain explicit and native-safe");
    }

    @Test
    void featureSourceSliceRemainsSynchronousAndToolkitNeutral() {
        ModuleDescriptor api = moduleEndingWith("mundane-map-api");
        ModuleDescriptor core = moduleEndingWith("mundane-map-core");
        List<JavaClass> sourceSlice =
                java.util.stream.Stream.concat(
                                classesByModule.get(api).stream(),
                                classesByModule.get(core).stream())
                        .filter(
                                type ->
                                        type.getSimpleName().contains("Source")
                                                || type.getSimpleName().contains("FeatureCursor")
                                                || type.getSimpleName().contains("FeatureQuery")
                                                || type.getSimpleName().contains("FeatureRecord")
                                                || type.getSimpleName().startsWith("Attribute")
                                                || type.getSimpleName().startsWith("Diagnostic")
                                                || type.getSimpleName().startsWith("Cancellation")
                                                || type.getSimpleName().startsWith("Multi"))
                        .toList();

        List<String> violations =
                sourceSlice.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(
                                target ->
                                        target.startsWith("java.awt.")
                                                || target.startsWith("javax.swing.")
                                                || target.equals("java.lang.Thread")
                                                || target.startsWith(
                                                        "java.util.concurrent.Executor")
                                                || target.startsWith("java.util.concurrent.Flow")
                                                || target.startsWith("java.util.concurrent.Future")
                                                || target.startsWith(
                                                        "java.util.concurrent.CompletableFuture"))
                        .distinct()
                        .sorted()
                        .toList();

        assertFalse(sourceSlice.isEmpty(), "Expected the production feature-source slice");
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void featureSourceCompositionAddsNoPrefetchOrRetainedRecordCache() {
        List<JavaClass> production =
                classesByModule.values().stream().flatMap(JavaClasses::stream).toList();
        List<String> speculativeWorkers =
                production.stream()
                        .map(JavaClass::getSimpleName)
                        .filter(
                                name ->
                                        name.contains("Prefetch")
                                                || name.contains("FeatureQueryCache")
                                                || name.contains("FeatureRecordCache"))
                        .sorted()
                        .toList();
        JavaClass mapView =
                classesByModule
                        .get(moduleEndingWith("mundane-map-awt"))
                        .get("io.github.mundanej.map.awt.MapView");
        String featureRecordType = "io.github.mundanej.map.api.FeatureRecord";
        List<JavaClass> awtComposition =
                classesByModule.get(moduleEndingWith("mundane-map-awt")).stream()
                        .filter(
                                type ->
                                        type.getSimpleName().equals("MapView")
                                                || type.getSimpleName().equals("MapLayerBinding")
                                                || type.getSimpleName().startsWith("AwtSymbol"))
                        .toList();
        List<String> hiddenWorkers =
                awtComposition.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(
                                target ->
                                        target.equals("java.lang.Thread")
                                                || target.startsWith(
                                                        "java.util.concurrent.Executor")
                                                || target.startsWith("java.util.concurrent.Flow")
                                                || target.startsWith("java.util.concurrent.Future")
                                                || target.startsWith(
                                                        "java.util.concurrent.CompletableFuture"))
                        .distinct()
                        .sorted()
                        .toList();
        List<String> retainedRecords =
                mapView.getFields().stream()
                        .filter(
                                field ->
                                        field.getAllInvolvedRawTypes().stream()
                                                .anyMatch(
                                                        type ->
                                                                type.getName()
                                                                        .equals(featureRecordType)))
                        .map(field -> field.getFullName())
                        .toList();
        List<String> emptyFormatModules =
                modules.stream()
                        .filter(module -> module.path().contains(":mundane-map-io-"))
                        .filter(module -> classesByModule.get(module).isEmpty())
                        .map(ModuleDescriptor::path)
                        .sorted()
                        .toList();

        assertTrue(speculativeWorkers.isEmpty(), () -> String.join("\n", speculativeWorkers));
        assertTrue(hiddenWorkers.isEmpty(), () -> String.join("\n", hiddenWorkers));
        assertTrue(retainedRecords.isEmpty(), () -> String.join("\n", retainedRecords));
        assertTrue(emptyFormatModules.isEmpty(), () -> String.join("\n", emptyFormatModules));
    }

    @Test
    void packedFeatureIndexRemainsPrivatePrimitiveAndStateless() {
        JavaClasses core = classesByModule.get(moduleEndingWith("mundane-map-core"));
        JavaClass index = core.get("io.github.mundanej.map.core.PackedFeatureSpatialIndex");
        JavaClass limits = core.get("io.github.mundanej.map.core.FeatureIndexLimits");

        assertFalse(index.getModifiers().contains(JavaModifier.PUBLIC));
        assertTrue(limits.getModifiers().contains(JavaModifier.PUBLIC));
        assertTrue(
                index.getFields().stream()
                        .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                        .allMatch(field -> field.getModifiers().contains(JavaModifier.FINAL)),
                "Packed index must not acquire mutable global state");
        assertTrue(
                index.getFields().stream()
                        .filter(field -> field.getRawType().isArray())
                        .allMatch(field -> field.getRawType().getBaseComponentType().isPrimitive()),
                "Packed index arrays must remain primitive");
        List<String> forbidden =
                index.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass().getName())
                        .filter(
                                target ->
                                        target.startsWith("java.awt.")
                                                || target.startsWith("javax.swing.")
                                                || target.equals("java.lang.Thread")
                                                || target.startsWith("java.util.concurrent.")
                                                || target.startsWith("org.locationtech."))
                        .sorted()
                        .toList();
        assertTrue(forbidden.isEmpty(), () -> String.join("\n", forbidden));
        assertTrue(
                classesByModule.values().stream()
                        .flatMap(JavaClasses::stream)
                        .filter(
                                type ->
                                        !type.getPackageName()
                                                .equals("io.github.mundanej.map.core"))
                        .noneMatch(
                                type ->
                                        type.getDirectDependenciesFromSelf().stream()
                                                .anyMatch(
                                                        dependency ->
                                                                dependency
                                                                        .getTargetClass()
                                                                        .equals(index))),
                "Packed index implementation leaked outside core");
    }

    @Test
    void rasterSourceSliceRemainsDirectSynchronousAndToolkitNeutral() {
        ModuleDescriptor api = moduleEndingWith("mundane-map-api");
        ModuleDescriptor core = moduleEndingWith("mundane-map-core");
        ModuleDescriptor awt = moduleEndingWith("mundane-map-awt");
        List<JavaClass> rasterContracts =
                java.util.stream.Stream.concat(
                                classesByModule.get(api).stream(),
                                classesByModule.get(core).stream())
                        .filter(
                                type ->
                                        type.getSimpleName().startsWith("Raster")
                                                || type.getSimpleName().equals("RgbaPixelBuffer")
                                                || type.getSimpleName()
                                                        .equals("SyntheticRasterSource"))
                        .toList();
        List<String> toolkitOrWorkerDependencies =
                rasterContracts.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(
                                target ->
                                        target.startsWith("java.awt.")
                                                || target.startsWith("javax.imageio.")
                                                || target.startsWith("javax.swing.")
                                                || target.equals("java.lang.Thread")
                                                || target.startsWith(
                                                        "java.util.concurrent.Executor")
                                                || target.startsWith("java.util.concurrent.Flow")
                                                || target.startsWith("java.util.concurrent.Future")
                                                || target.startsWith(
                                                        "java.util.concurrent.CompletableFuture"))
                        .distinct()
                        .sorted()
                        .toList();
        List<JavaClass> production =
                classesByModule.values().stream().flatMap(JavaClasses::stream).toList();
        List<JavaClass> awtClasses = classesByModule.get(awt).stream().toList();
        List<String> implicitAwtRasterDiscovery =
                awtClasses.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(target -> target.equals("java.util.ServiceLoader"))
                        .distinct()
                        .sorted()
                        .toList();
        List<String> prematureRasterInfrastructure =
                production.stream()
                        .map(JavaClass::getSimpleName)
                        .filter(
                                name ->
                                        name.contains("RasterCache")
                                                || name.contains("RasterWorker")
                                                || name.contains("RasterLoader")
                                                || name.contains("RasterWarp"))
                        .sorted()
                        .toList();
        JavaClass mapView = classesByModule.get(awt).get("io.github.mundanej.map.awt.MapView");
        JavaClass converter =
                classesByModule.get(awt).get("io.github.mundanej.map.awt.AwtRgbaPixels");
        List<String> retainedMapViewImages =
                mapView.getFields().stream()
                        .filter(
                                field ->
                                        field.getAllInvolvedRawTypes().stream()
                                                .anyMatch(
                                                        type ->
                                                                type.getName()
                                                                        .equals(
                                                                                "java.awt.image.BufferedImage")))
                        .map(field -> field.getFullName())
                        .toList();
        boolean directConversionWired =
                mapView.getMethods().stream()
                        .flatMap(method -> method.getMethodCallsFromSelf().stream())
                        .anyMatch(
                                call ->
                                        call.getTargetOwner().equals(converter)
                                                && call.getName().equals("toBufferedImage"));
        Set<String> converterDependencies =
                converter.getDirectDependenciesFromSelf().stream()
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .collect(Collectors.toSet());

        assertFalse(rasterContracts.isEmpty(), "Expected the production raster-source slice");
        assertTrue(
                toolkitOrWorkerDependencies.isEmpty(),
                () -> String.join("\n", toolkitOrWorkerDependencies));
        assertTrue(
                implicitAwtRasterDiscovery.isEmpty(),
                () -> String.join("\n", implicitAwtRasterDiscovery));
        assertTrue(
                prematureRasterInfrastructure.isEmpty(),
                () -> String.join("\n", prematureRasterInfrastructure));
        assertTrue(retainedMapViewImages.isEmpty(), () -> String.join("\n", retainedMapViewImages));
        assertTrue(directConversionWired, "MapView must call the direct packed-pixel converter");
        assertTrue(
                converter.getModifiers().contains(JavaModifier.FINAL)
                        && !converter.getModifiers().contains(JavaModifier.PUBLIC),
                "The direct converter must remain final and package-private");
        assertTrue(
                converterDependencies.contains("io.github.mundanej.map.api.RgbaPixelBuffer")
                        && converterDependencies.contains("java.awt.image.BufferedImage")
                        && converterDependencies.contains("java.awt.image.DataBufferInt"),
                "The direct converter must bridge the toolkit-neutral buffer to owned AWT pixels");
    }

    @Test
    void imageModuleIsAwtFreeAndUsesOnlyTheExplicitFixedAwtDecoderBridge() {
        ModuleDescriptor image = moduleEndingWith("mundane-map-io-image");
        ModuleDescriptor awt = moduleEndingWith("mundane-map-awt");
        JavaClasses formatClasses = classesByModule.get(image);
        JavaClasses awtClasses = classesByModule.get(awt);
        Set<String> imageSecurityOwners =
                formatClasses.stream()
                        .filter(
                                type ->
                                        type.getDirectDependenciesFromSelf().stream()
                                                .anyMatch(
                                                        dependency ->
                                                                dependency
                                                                        .getTargetClass()
                                                                        .getPackageName()
                                                                        .startsWith(
                                                                                "java.security")))
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet());
        long imageDigestFactories =
                formatClasses.stream()
                        .flatMap(type -> type.getAccessesFromSelf().stream())
                        .filter(
                                access ->
                                        access.getTargetOwner()
                                                        .getName()
                                                        .equals("java.security.MessageDigest")
                                                && access.getName().equals("getInstance"))
                        .count();
        List<String> toolkitDependencies =
                formatClasses.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(
                                target ->
                                        target.startsWith("java.awt.")
                                                || target.startsWith("javax.swing.")
                                                || target.startsWith("javax.imageio."))
                        .distinct()
                        .sorted()
                        .toList();
        Set<String> publicFormatTypes =
                formatClasses.stream()
                        .filter(
                                type ->
                                        type.getPackageName()
                                                .equals("io.github.mundanej.map.io.image"))
                        .filter(type -> type.getName().indexOf('$') < 0)
                        .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> imageIoOwners =
                awtClasses.stream()
                        .filter(
                                type ->
                                        type.getDirectDependenciesFromSelf().stream()
                                                .anyMatch(
                                                        dependency ->
                                                                dependency
                                                                        .getTargetClass()
                                                                        .getBaseComponentType()
                                                                        .getPackageName()
                                                                        .startsWith(
                                                                                "javax.imageio")))
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> supportProjects =
                java.util.Arrays.stream(
                                System.getProperty("map.architecture.supportProjects").split(","))
                        .filter(Predicate.not(String::isBlank))
                        .collect(Collectors.toUnmodifiableSet());
        List<String> unsafeImageCacheMechanisms = imageCacheMechanismViolations(formatClasses);
        List<String> imageCacheStructure =
                imageRasterSourceCacheStructureViolations(
                        formatClasses.get("io.github.mundanej.map.io.image.ImageRasterSource"));
        List<String> imageModuleCacheOwnership =
                imageModuleCacheOwnershipViolations(
                        formatClasses, "io.github.mundanej.map.io.image.ImageRasterSource");
        JavaClass cacheKey =
                formatClasses.get("io.github.mundanej.map.io.image.ImageRasterSource$CacheKey");
        JavaClass cacheEntry =
                formatClasses.get("io.github.mundanej.map.io.image.ImageRasterSource$CacheEntry");

        assertEquals("JDK_RUNTIME", image.category());
        assertEquals(1, image.releaseLevel());
        assertTrue(image.nativeTarget());
        assertEquals(Set.of("ImageSnapshots"), imageSecurityOwners);
        assertEquals(1, imageDigestFactories, "Image snapshots must select SHA-256 exactly once");
        Path imageSnapshotsSource =
                Path.of(System.getProperty("map.architecture.imageSources"))
                        .resolve("io/github/mundanej/map/io/image/ImageSnapshots.java");
        try {
            assertTrue(
                    ArchitecturePolicy.fixedSha256WorkspaceSourceViolations(
                                    Files.readString(imageSnapshotsSource), "ImageSnapshots.java")
                            .isEmpty());
        } catch (IOException failure) {
            throw new AssertionError("Could not inspect the image snapshot source", failure);
        }
        assertEquals(
                Set.of(":modules:mundane-map-api", ":modules:mundane-map-core"),
                image.allowedRuntimeProjects());
        assertFalse(formatClasses.isEmpty(), "Expected the working image format module");
        assertTrue(toolkitDependencies.isEmpty(), () -> String.join("\n", toolkitDependencies));
        assertTrue(
                unsafeImageCacheMechanisms.isEmpty(),
                () -> String.join("\n", unsafeImageCacheMechanisms));
        assertTrue(imageCacheStructure.isEmpty(), () -> String.join("\n", imageCacheStructure));
        assertTrue(
                imageModuleCacheOwnership.isEmpty(),
                () -> String.join("\n", imageModuleCacheOwnership));
        assertEquals(
                Map.of(
                        "version", "io.github.mundanej.map.io.image.ImageContentVersion",
                        "window", "io.github.mundanej.map.api.RasterWindow",
                        "outputWidth", "int",
                        "outputHeight", "int",
                        "interpolation", "io.github.mundanej.map.api.RasterInterpolation"),
                fieldShape(cacheKey));
        assertEquals(
                Map.of(
                        "pixels", "io.github.mundanej.map.api.RgbaPixelBuffer",
                        "bytes", "long"),
                fieldShape(cacheEntry));
        assertEquals(
                Set.of(
                        "RasterImages",
                        "ImageOpenOptions",
                        "ImagePlacement",
                        "ImageSourceLimits",
                        "ImageCachePolicy"),
                publicFormatTypes);
        assertEquals(
                Set.of("AwtRasterDecoders", "ImageInputFactory", "ImageIoRasterDecoder"),
                imageIoOwners);
        assertTrue(supportProjects.contains(":examples:raster-viewer"));

        List<String> rejectedFixtures =
                imageCacheMechanismViolations(
                        List.of(
                                fixture("MechanismFixtures$SharedEncodedCacheUse"),
                                fixture("MechanismFixtures$SoftCacheUse"),
                                fixture("MechanismFixtures$CacheWorkerUse"),
                                fixture("MechanismFixtures$PublicCacheMetrics")));
        assertEquals(4, rejectedFixtures.size(), () -> String.join("\n", rejectedFixtures));
        for (String fixtureName :
                List.of(
                        "MechanismFixtures$StaticStoreUse",
                        "MechanismFixtures$SecondInstanceMapsUse",
                        "MechanismFixtures$EncodedStorageUse",
                        "MechanismFixtures$ArgbStorageUse",
                        "MechanismFixtures$AwtStorageUse")) {
            List<String> violations =
                    imageRasterSourceCacheStructureViolations(fixture(fixtureName));
            assertFalse(violations.isEmpty(), fixtureName);
        }
        JavaClass fixtureOwner = fixture("MechanismFixtures$SoleCacheOwnerUse");
        assertTrue(
                imageModuleCacheOwnershipViolations(List.of(fixtureOwner), fixtureOwner.getName())
                        .isEmpty());
        for (String helper :
                List.of(
                        "MechanismFixtures$ExtraHelperMapUse",
                        "MechanismFixtures$EvasiveHelperStorageUse")) {
            assertFalse(
                    imageModuleCacheOwnershipViolations(
                                    List.of(fixtureOwner, fixture(helper)), fixtureOwner.getName())
                            .isEmpty(),
                    helper);
        }
        assertTrue(
                modules.stream()
                        .noneMatch(module -> module.path().equals(":examples:raster-viewer")),
                "The viewer must remain outside the production architecture graph");
    }

    @Test
    void shapefileModuleIsJdkOnlyAwtFreeNativeSafeAndItsViewerIsSupportOnly() {
        ModuleDescriptor shapefile = moduleEndingWith("mundane-map-io-shapefile");
        JavaClasses formatClasses = classesByModule.get(shapefile);
        List<String> toolkitDependencies =
                formatClasses.stream()
                        .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                        .map(
                                dependency ->
                                        dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .getName())
                        .filter(
                                target ->
                                        target.startsWith("java.awt.")
                                                || target.startsWith("javax.swing.")
                                                || target.startsWith("javax.imageio."))
                        .distinct()
                        .sorted()
                        .toList();
        Set<String> publicFormatTypes =
                formatClasses.stream()
                        .filter(
                                type ->
                                        type.getPackageName()
                                                .equals("io.github.mundanej.map.io.shapefile"))
                        .filter(type -> type.getName().indexOf('$') < 0)
                        .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> supportProjects =
                java.util.Arrays.stream(
                                System.getProperty("map.architecture.supportProjects").split(","))
                        .filter(Predicate.not(String::isBlank))
                        .collect(Collectors.toUnmodifiableSet());

        assertEquals("JDK_RUNTIME", shapefile.category());
        assertEquals(1, shapefile.releaseLevel());
        assertTrue(shapefile.nativeTarget());
        assertEquals(
                Set.of(":modules:mundane-map-api", ":modules:mundane-map-core"),
                shapefile.allowedRuntimeProjects());
        assertFalse(formatClasses.isEmpty(), "Expected the working shapefile format module");
        assertTrue(toolkitDependencies.isEmpty(), () -> String.join("\n", toolkitDependencies));
        assertTrue(
                ArchitecturePolicy.prohibitedMechanismViolations(formatClasses).isEmpty(),
                "Shapefile production must remain compatible with the native-targeted boundary");
        assertTrue(
                formatClasses.stream()
                        .map(JavaClass::getSimpleName)
                        .noneMatch(
                                name ->
                                        name.contains("Adversarial")
                                                || name.contains("Mutation")
                                                || name.contains("Fuzz")),
                "Shapefile adversarial and mutation helpers must remain test-only");
        assertEquals(
                Set.of("Shapefiles", "ShapefileOpenOptions", "ShapefileLimits", "DbfEncoding"),
                publicFormatTypes);
        assertTrue(supportProjects.contains(":examples:shapefile-viewer"));
        assertTrue(
                modules.stream()
                        .noneMatch(module -> module.path().equals(":examples:shapefile-viewer")),
                "The viewer must remain outside the production architecture graph");
    }

    @Test
    void nativeSmokeSupportAvoidsProhibitedMechanisms() {
        List<String> violations =
                ArchitecturePolicy.prohibitedMechanismViolations(nativeSupportClasses);

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeFormatSupportUsesOnlyFixedPathsAndItsOneDigestWorkspace() throws IOException {
        JavaClasses shapefileClasses =
                classesByModule.get(moduleEndingWith("mundane-map-io-shapefile"));
        List<String> bytecodeViolations =
                ArchitecturePolicy.nativeShapefileSupportViolations(
                        nativeSupportClasses, shapefileClasses);

        assertTrue(bytecodeViolations.isEmpty(), () -> String.join("\n", bytecodeViolations));

        Path workspaceSource =
                nativeSupportSources.resolve(
                        "io/github/mundanej/map/nativeimage/NativeFixtureWorkspace.java");
        List<String> sourceViolations =
                ArchitecturePolicy.fixedSha256WorkspaceSourceViolations(
                        Files.readString(workspaceSource), workspaceSource.toString());
        assertTrue(sourceViolations.isEmpty(), () -> String.join("\n", sourceViolations));
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
    void nativeSmokeSourceAndProcessedResourceInventoriesAreExact() throws IOException {
        Path sourceResources =
                nativeSupportResources.stream()
                        .filter(path -> path.endsWith(Path.of("src", "main", "resources")))
                        .findFirst()
                        .orElseThrow();
        Path processedResources =
                nativeSupportResources.stream()
                        .filter(Predicate.not(sourceResources::equals))
                        .findFirst()
                        .orElseThrow();
        Set<String> metadata =
                Set.of(
                        NATIVE_METADATA_DIRECTORY + "jni-config.json",
                        NATIVE_METADATA_DIRECTORY + "reflect-config.json",
                        NATIVE_METADATA_DIRECTORY + "resource-config.json");
        Set<String> checkedIn =
                java.util.stream.Stream.of(
                                metadata,
                                Set.of(
                                        NATIVE_RESOURCE_DIRECTORY + "symbol-smoke-4x2.rgba",
                                        NATIVE_RESOURCE_DIRECTORY
                                                + "symbol-smoke-4x2.rgba.provenance.txt",
                                        NATIVE_RESOURCE_DIRECTORY
                                                + "shapefile/malformed-record.shp"),
                                NATIVE_RASTER_RESOURCES)
                        .flatMap(Set::stream)
                        .collect(Collectors.toUnmodifiableSet());
        Set<String> processed =
                java.util.stream.Stream.concat(
                                checkedIn.stream(), NATIVE_SHAPEFILE_RESOURCES.stream())
                        .collect(Collectors.toUnmodifiableSet());

        assertEquals(checkedIn, resourceInventory(sourceResources));
        assertEquals(processed, resourceInventory(processedResources));
    }

    @Test
    void nativeSmokeResourceConfigurationNamesOnlyLiteralRuntimeResources() throws IOException {
        Path sourceResources =
                nativeSupportResources.stream()
                        .filter(path -> path.endsWith(Path.of("src", "main", "resources")))
                        .findFirst()
                        .orElseThrow();
        Path resourceConfig =
                sourceResources.resolve(NATIVE_METADATA_DIRECTORY + "resource-config.json");
        Set<String> expected =
                java.util.stream.Stream.concat(
                                Set.of(NATIVE_RESOURCE_DIRECTORY + "symbol-smoke-4x2.rgba")
                                        .stream(),
                                java.util.stream.Stream.concat(
                                        NATIVE_SHAPEFILE_RESOURCES.stream(),
                                        NATIVE_RASTER_RESOURCES.stream()))
                        .collect(Collectors.toUnmodifiableSet());
        List<String> violations =
                ArchitecturePolicy.explicitResourceConfigViolations(
                        Files.readString(resourceConfig), expected);

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeRasterJniCompatibilityMetadataIsNarrowAndExact() throws IOException {
        String metadata = nativeJniMetadata();

        List<String> violations = NativeJniMetadataPolicy.rasterCompatibilityViolations(metadata);

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void nativeRasterJniCompatibilityMetadataRejectsBroadAndDuplicateRegistration()
            throws IOException {
        assertJniMetadataRejected(
                "[{\"name\":\"duplicate\"},{\"name\":\"duplicate\"}]", "Duplicate JNI class");
        assertJniMetadataRejected(
                "[{\"name\":\"duplicate-field\",\"fields\":[{\"name\":\"value\"},{\"name\":\"value\"}]}]",
                "Duplicate JNI field");
        assertJniMetadataRejected(
                "[{\"name\":\"duplicate-method\",\"methods\":["
                        + "{\"name\":\"read\",\"parameterTypes\":[\"int\"]},"
                        + "{\"name\":\"read\",\"parameterTypes\":[\"int\"]}]}]",
                "Duplicate JNI method overload");
        for (String flag :
                List.of(
                        "allDeclaredMethods",
                        "allQueriedFields",
                        "allPublicConstructors",
                        "queryAllDeclaredClasses")) {
            assertJniMetadataRejected(
                    "[{\"name\":\"broad\",\"" + flag + "\":true}]",
                    "Broad JNI registration flag " + flag);
        }
        assertJniMetadataRejected(
                "[{\"name\":\"com.sun.imageio.plugins.jpeg.JPEGImageReader\",\"methods\":["
                        + "{\"name\":\"readInputData\","
                        + "\"parameterTypes\":[\"byte[]\",\"long\",\"int\"]}]}]",
                "Unexpected raster JNI method signatures");

        String actual = nativeJniMetadata();
        assertJniMetadataRejected(
                appendJniClass(actual, "{\"name\":\"example.UnapprovedJniClass\"}"),
                "Unexpected complete JNI metadata snapshot");
        String extraExistingMember =
                actual.replace(
                        "\"methods\":[{\"name\":\"getRGB\",\"parameterTypes\":[] }]",
                        "\"methods\":[{\"name\":\"getRGB\",\"parameterTypes\":[] },"
                                + "{\"name\":\"getRed\",\"parameterTypes\":[] }]");
        assertFalse(actual.equals(extraExistingMember), "Expected the java.awt.Color mutation");
        assertJniMetadataRejected(extraExistingMember, "Unexpected complete JNI metadata snapshot");
        assertJniMetadataRejected(
                appendJniClass(
                        actual,
                        "{\"name\":\"com.sun.imageio.plugins.png.PNGImageReader\","
                                + "\"methods\":[{\"name\":\"readHeader\","
                                + "\"parameterTypes\":[]}]}"),
                "Unexpected complete JNI metadata snapshot");
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
                        "MemoryMappingUse",
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
    void nativeShapefilePolicyRejectsResourceWalkingAndArbitraryDigestSelection()
            throws IOException {
        List<JavaClass> fixtures =
                List.of(
                        fixture("MechanismFixtures$ResourceWalkingUse"),
                        fixture("MechanismFixtures$ArbitraryDigestUse"));
        List<String> violations =
                ArchitecturePolicy.nativeShapefileSupportViolations(fixtures, List.of());

        assertTrue(
                violations.stream().anyMatch(value -> value.contains("ResourceWalkingUse")),
                () -> String.join("\n", violations));
        assertTrue(
                violations.stream().anyMatch(value -> value.contains("ArbitraryDigestUse")),
                () -> String.join("\n", violations));

        Path fixtureResources = Path.of(System.getProperty("map.architecture.fixtureResources"));
        for (String fixtureName :
                List.of("CallerSelectedWorkspace.java.txt", "PropertySelectedWorkspace.java.txt")) {
            Path fixture = fixtureResources.resolve("native-digest").resolve(fixtureName);
            List<String> sourceViolations =
                    ArchitecturePolicy.fixedSha256WorkspaceSourceViolations(
                            Files.readString(fixture), fixtureName);
            assertFalse(sourceViolations.isEmpty(), fixtureName);
            assertTrue(
                    sourceViolations.stream()
                            .allMatch(value -> value.contains("must use the literal \"SHA-256\"")),
                    () -> String.join("\n", sourceViolations));
        }
    }

    @Test
    void nativeResourcePolicyRejectsWildcardAndBundleMetadata() {
        String unsafe =
                """
                {
                  "resources": {
                    "includes": [{"pattern": ".*"}],
                    "bundles": [{"name": "example.Messages"}]
                  }
                }
                """;

        assertFalse(
                ArchitecturePolicy.explicitResourceConfigViolations(
                                unsafe, Set.of("known/resource.bin"))
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

    private static List<String> imageCacheMechanismViolations(Iterable<JavaClass> classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            boolean forbiddenDependency =
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .map(
                                    dependency ->
                                            dependency
                                                    .getTargetClass()
                                                    .getBaseComponentType()
                                                    .getName())
                            .anyMatch(
                                    name ->
                                            name.equals("java.lang.ref.SoftReference")
                                                    || name.equals("java.lang.ref.WeakReference")
                                                    || name.startsWith(
                                                            "java.util.concurrent.Executor")
                                                    || name.startsWith(
                                                            "java.util.concurrent.Executors")
                                                    || name.startsWith(
                                                            "java.util.concurrent.Future")
                                                    || name.equals("java.lang.Thread"));
            boolean sharedCache =
                    javaClass.getFields().stream()
                            .anyMatch(
                                    field ->
                                            field.getModifiers().contains(JavaModifier.STATIC)
                                                    && field.getName()
                                                            .toUpperCase(java.util.Locale.ROOT)
                                                            .contains("CACHE"));
            boolean publicImplementation =
                    javaClass.getModifiers().contains(JavaModifier.PUBLIC)
                            && javaClass.getName().indexOf('$') >= 0
                            && (javaClass.getSimpleName().contains("CacheMetrics")
                                    || javaClass.getSimpleName().contains("CacheEntry")
                                    || javaClass.getSimpleName().contains("ContentVersion"));
            if (forbiddenDependency || sharedCache || publicImplementation) {
                violations.add(javaClass.getName());
            }
        }
        return List.copyOf(violations);
    }

    private static List<String> imageRasterSourceCacheStructureViolations(JavaClass javaClass) {
        List<String> violations = new ArrayList<>();
        var maps =
                javaClass.getFields().stream()
                        .filter(field -> field.getRawType().isAssignableTo(java.util.Map.class))
                        .toList();
        if (maps.size() != 1) {
            violations.add(javaClass.getName() + " must own exactly one map");
        } else {
            var map = maps.get(0);
            if (map.getModifiers().contains(JavaModifier.STATIC)) {
                violations.add(javaClass.getName() + " map must be per-instance");
            }
            if (!map.getRawType().getName().equals("java.util.LinkedHashMap")) {
                violations.add(javaClass.getName() + " map must be a LinkedHashMap");
            }
        }
        boolean pixelOrEncodedArray =
                javaClass.getFields().stream()
                        .map(field -> field.getRawType().getBaseComponentType().getName())
                        .anyMatch(name -> name.equals("byte") || name.equals("int"));
        if (pixelOrEncodedArray) {
            violations.add(javaClass.getName() + " must not retain encoded or ARGB arrays");
        }
        boolean awtStorage =
                javaClass.getFields().stream()
                        .map(field -> field.getRawType().getBaseComponentType().getPackageName())
                        .anyMatch(
                                name ->
                                        name.startsWith("java.awt")
                                                || name.startsWith("javax.imageio"));
        if (awtStorage) {
            violations.add(javaClass.getName() + " must not retain AWT storage");
        }
        return List.copyOf(violations);
    }

    private static List<String> imageModuleCacheOwnershipViolations(
            Iterable<JavaClass> classes, String expectedOwner) {
        List<String> mapOwners = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            javaClass.getFields().stream()
                    .filter(field -> field.getRawType().isAssignableTo(java.util.Map.class))
                    .forEach(field -> mapOwners.add(javaClass.getName() + '#' + field.getName()));
        }
        if (mapOwners.size() != 1 || !mapOwners.get(0).startsWith(expectedOwner + '#')) {
            return List.of("Only " + expectedOwner + " may own cache map storage: " + mapOwners);
        }
        return List.of();
    }

    private static Map<String, String> fieldShape(JavaClass javaClass) {
        return javaClass.getFields().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                field -> field.getName(), field -> field.getRawType().getName()));
    }

    private static void assertJniMetadataRejected(String metadata, String expectedViolation) {
        List<String> violations = NativeJniMetadataPolicy.rasterCompatibilityViolations(metadata);
        assertTrue(
                violations.stream().anyMatch(value -> value.contains(expectedViolation)),
                () -> "Expected " + expectedViolation + " in " + violations);
    }

    private static String nativeJniMetadata() throws IOException {
        Path sourceResources =
                nativeSupportResources.stream()
                        .filter(path -> path.endsWith(Path.of("src", "main", "resources")))
                        .findFirst()
                        .orElseThrow();
        return Files.readString(
                sourceResources.resolve(NATIVE_METADATA_DIRECTORY + "jni-config.json"));
    }

    private static String appendJniClass(String metadata, String classBlock) {
        int end = metadata.lastIndexOf(']');
        assertTrue(end >= 0, "Expected JNI metadata array");
        return metadata.substring(0, end) + ",\n" + classBlock + "\n]";
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

    private static Set<String> resourceInventory(Path resourceRoot) throws IOException {
        try (var paths = Files.walk(resourceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .map(resourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .collect(Collectors.toUnmodifiableSet());
        }
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
