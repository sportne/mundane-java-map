package io.github.mundanej.map.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ArchitecturePolicy {
    private static final Map<String, String> JDK_MODULE_BY_PACKAGE = jdkModulesByPackage();
    private static final String NATIVE_SHAPEFILE_WORKSPACE =
            "io.github.mundanej.map.nativeimage.NativeFixtureWorkspace";
    private static final Pattern RESOURCE_PATTERN =
            Pattern.compile("\\\"pattern\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern MESSAGE_DIGEST_FACTORY_CALL =
            Pattern.compile(
                    "(?:java\\.security\\.)?MessageDigest\\s*\\.\\s*getInstance\\s*\\(([^)]*)\\)",
                    Pattern.DOTALL);

    private ArchitecturePolicy() {}

    static List<String> moduleBoundaryViolations(
            ModuleDescriptor module,
            Collection<JavaClass> classes,
            Map<String, String> owningModuleByClass) {
        List<String> violations = new ArrayList<>();
        Set<String> allowedModules = new HashSet<>(module.allowedRuntimeProjects());
        allowedModules.add(module.path());
        for (JavaClass javaClass : classes) {
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                String targetModule = owningModuleByClass.get(target.getName());
                if (targetModule != null
                        && !moduleAllowsTarget(module, targetModule, allowedModules)) {
                    violations.add(
                            diagnostic(
                                    "module dependency",
                                    module.path(),
                                    javaClass,
                                    target.getName()));
                    continue;
                }
                String jdkModule = JDK_MODULE_BY_PACKAGE.get(target.getPackageName());
                if (jdkModule != null && !allowedJdkModules(module).contains(jdkModule)) {
                    violations.add(
                            diagnostic(
                                    "JDK module " + jdkModule,
                                    module.path(),
                                    javaClass,
                                    target.getName()));
                } else if (module.releaseLevel() == 1
                        && module.category().equals("JDK_RUNTIME")
                        && targetModule == null
                        && jdkModule == null
                        && !target.getName().startsWith("io.github.mundanej.map.")) {
                    violations.add(
                            diagnostic(
                                    "external runtime type",
                                    module.path(),
                                    javaClass,
                                    target.getName()));
                }
            }
        }
        return violations;
    }

    static List<String> publicApiViolations(Collection<JavaClass> classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.getModifiers().contains(JavaModifier.PUBLIC)) {
                continue;
            }
            javaClass
                    .getRawSuperclass()
                    .ifPresent(
                            type ->
                                    addApiTypeViolation(
                                            violations, javaClass, javaClass.getName(), type));
            for (JavaClass type : javaClass.getRawInterfaces()) {
                addApiTypeViolation(violations, javaClass, javaClass.getName(), type);
            }
            for (JavaMember member : javaClass.getMembers()) {
                if (!member.getModifiers().contains(JavaModifier.PUBLIC)
                        && !member.getModifiers().contains(JavaModifier.PROTECTED)) {
                    continue;
                }
                for (JavaClass type : member.getAllInvolvedRawTypes()) {
                    addApiTypeViolation(violations, javaClass, member.getFullName(), type);
                }
            }
        }
        return violations;
    }

    static List<String> prohibitedMechanismViolations(Collection<JavaClass> classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            for (JavaMethod method : javaClass.getMethods()) {
                if (method.getModifiers().contains(JavaModifier.NATIVE)) {
                    violations.add(
                            diagnostic(
                                    "native method",
                                    "native-targeted production",
                                    javaClass,
                                    method.getFullName()));
                }
            }
            if (javaClass.getRawInterfaces().stream()
                    .anyMatch(type -> type.getName().equals("java.io.Serializable"))) {
                violations.add(
                        diagnostic(
                                "Java serialization",
                                "native-targeted production",
                                javaClass,
                                "java.io.Serializable"));
            }
            for (JavaMember member : javaClass.getMembers()) {
                for (JavaClass type : member.getAllInvolvedRawTypes()) {
                    if (isProhibitedDirectType(type.getName())) {
                        violations.add(
                                diagnostic(
                                        "prohibited runtime type",
                                        "native-targeted production",
                                        javaClass,
                                        type.getName()));
                    }
                }
            }
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                JavaClass targetOwner = access.getTargetOwner();
                String owner = targetOwner.getName();
                String name = access.getName();
                if (isProhibitedAccess(targetOwner, name)
                        && !isFixedImageIoQualification(javaClass, owner, name)) {
                    violations.add(
                            diagnostic(
                                    "prohibited runtime access",
                                    "native-targeted production",
                                    javaClass,
                                    owner + "." + name));
                }
            }
            if (hasMutableStaticRegistry(javaClass)) {
                violations.add(
                        diagnostic(
                                "mutable global registration",
                                "native-targeted production",
                                javaClass,
                                "static registry holder and mutation access"));
            }
        }
        return violations;
    }

    static List<String> discoveryResourceViolations(String modulePath, Path resourceRoot)
            throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(resourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .map(resourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(ArchitecturePolicy::isDiscoveryMetadata)
                    .forEach(
                            path ->
                                    violations.add(
                                            "resource discovery boundary violated by "
                                                    + modulePath
                                                    + ": "
                                                    + path));
        }
        return violations;
    }

    static List<String> nativeShapefileSupportViolations(
            Collection<JavaClass> nativeSupportClasses, Collection<JavaClass> shapefileClasses) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : shapefileClasses) {
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                if (access.getTargetOwner().getPackageName().startsWith("java.security")) {
                    violations.add(
                            diagnostic(
                                    "native Shapefile cryptography",
                                    "mundane-map-io-shapefile",
                                    javaClass,
                                    access.getTarget().getFullName()));
                }
            }
        }
        for (JavaClass javaClass : nativeSupportClasses) {
            int digestFactoryCalls = 0;
            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                String owner = access.getTargetOwner().getName();
                String name = access.getName();
                if (javaClass.getName().equals(NATIVE_SHAPEFILE_WORKSPACE)
                        && owner.equals("java.security.MessageDigest")
                        && name.equals("getInstance")) {
                    digestFactoryCalls++;
                }
                if (owner.startsWith("java.security.")
                        && !allowedWorkspaceDigestAccess(javaClass, owner, name, access)) {
                    violations.add(
                            diagnostic(
                                    "native Shapefile cryptography",
                                    "mundane-map-native-tests",
                                    javaClass,
                                    access.getTarget().getFullName()));
                }
                if (isResourceEnumerationOrWalking(owner, name)) {
                    violations.add(
                            diagnostic(
                                    "fixed native resource",
                                    "mundane-map-native-tests",
                                    javaClass,
                                    access.getTarget().getFullName()));
                }
            }
            if (javaClass.getName().equals(NATIVE_SHAPEFILE_WORKSPACE) && digestFactoryCalls != 1) {
                violations.add(
                        "native Shapefile digest factory boundary violated by "
                                + NATIVE_SHAPEFILE_WORKSPACE
                                + ": expected exactly one MessageDigest.getInstance call, found "
                                + digestFactoryCalls);
            }
        }
        return violations;
    }

    static List<String> fixedSha256WorkspaceSourceViolations(String source, String sourceName) {
        Matcher matcher = MESSAGE_DIGEST_FACTORY_CALL.matcher(source);
        List<String> violations = new ArrayList<>();
        int callCount = 0;
        while (matcher.find()) {
            callCount++;
            if (!matcher.group(1).trim().equals("\"SHA-256\"")) {
                violations.add(
                        "native Shapefile digest algorithm boundary violated by "
                                + sourceName
                                + ": MessageDigest.getInstance must use the literal \"SHA-256\"");
            }
        }
        if (callCount != 1) {
            violations.add(
                    "native Shapefile digest algorithm boundary violated by "
                            + sourceName
                            + ": expected exactly one source-level MessageDigest.getInstance call, found "
                            + callCount);
        }
        return List.copyOf(violations);
    }

    static List<String> explicitResourceConfigViolations(
            String resourceConfig, Set<String> expectedResourcePaths) {
        List<String> violations = new ArrayList<>();
        Matcher matcher = RESOURCE_PATTERN.matcher(resourceConfig);
        Set<String> actualPatterns = new HashSet<>();
        int patternCount = 0;
        while (matcher.find()) {
            patternCount++;
            actualPatterns.add(matcher.group(1));
        }
        Set<String> expectedPatterns = new HashSet<>();
        for (String path : expectedResourcePaths) {
            expectedPatterns.add("\\\\Q" + path + "\\\\E");
        }
        if (patternCount != expectedPatterns.size() || !actualPatterns.equals(expectedPatterns)) {
            violations.add(
                    "explicit native resource inventory violated: expected "
                            + expectedPatterns.stream().sorted().toList()
                            + ", actual "
                            + actualPatterns.stream().sorted().toList());
        }
        String reachability =
                "\"typeReachable\": \"io.github.mundanej.map.nativeimage.NativeSmokeMain\"";
        if (countOccurrences(resourceConfig, reachability) != expectedPatterns.size()) {
            violations.add(
                    "explicit native resource reachability violated: every resource must use "
                            + "NativeSmokeMain");
        }
        if (resourceConfig.contains("\"bundles\"") || resourceConfig.contains("\"excludes\"")) {
            violations.add("explicit native resource metadata contains discovery-oriented entries");
        }
        return List.copyOf(violations);
    }

    private static boolean allowedWorkspaceDigestAccess(
            JavaClass origin, String owner, String name, JavaAccess<?> access) {
        if (!origin.getName().equals(NATIVE_SHAPEFILE_WORKSPACE)
                || !owner.equals("java.security.MessageDigest")) {
            return false;
        }
        if (name.equals("digest")) {
            return true;
        }
        return name.equals("getInstance")
                && access.getTarget()
                        .getFullName()
                        .equals("java.security.MessageDigest.getInstance(java.lang.String)");
    }

    private static boolean isResourceEnumerationOrWalking(String owner, String name) {
        if (owner.equals("java.nio.file.Files")) {
            return name.equals("walk")
                    || name.equals("walkFileTree")
                    || name.equals("list")
                    || name.equals("find")
                    || name.equals("newDirectoryStream");
        }
        return owner.equals("java.io.File")
                && (name.equals("list") || name.equals("listFiles") || name.equals("listRoots"));
    }

    private static int countOccurrences(String value, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }

    private static Set<String> allowedJdkModules(ModuleDescriptor module) {
        if (module.path().endsWith("mundane-map-api")
                || module.path().endsWith("mundane-map-core")) {
            return Set.of("java.base");
        }
        if (module.path().endsWith("mundane-map-awt")) {
            return Set.of("java.base", "java.desktop");
        }
        Set<String> allowed = new HashSet<>(JDK_MODULE_BY_PACKAGE.values());
        allowed.remove("java.desktop");
        return Set.copyOf(allowed);
    }

    private static boolean moduleAllowsTarget(
            ModuleDescriptor module, String targetModule, Set<String> declaredAllowedModules) {
        if (module.path().endsWith("mundane-map-api")) {
            return targetModule.equals(module.path());
        }
        if (module.path().endsWith("mundane-map-core")) {
            return targetModule.equals(module.path()) || targetModule.endsWith("mundane-map-api");
        }
        if (module.path().endsWith("mundane-map-awt")) {
            return targetModule.equals(module.path())
                    || targetModule.endsWith("mundane-map-api")
                    || targetModule.endsWith("mundane-map-core");
        }
        if (module.path().contains(":mundane-map-io-")
                && targetModule.endsWith("mundane-map-awt")) {
            return false;
        }
        return declaredAllowedModules.contains(targetModule);
    }

    private static void addApiTypeViolation(
            List<String> violations, JavaClass owner, String signature, JavaClass type) {
        JavaClass baseType = type.getBaseComponentType();
        String packageName = baseType.getPackageName();
        if (baseType.isPrimitive()
                || packageName.equals("io.github.mundanej.map.api")
                || packageName.startsWith("io.github.mundanej.map.api.")
                || JDK_MODULE_BY_PACKAGE.get(packageName) != null) {
            return;
        }
        violations.add(
                diagnostic(
                        "public API signature",
                        "mundane-map-api",
                        owner,
                        signature + " -> " + type.getName()));
    }

    private static boolean isProhibitedDirectType(String typeName) {
        return typeName.startsWith("java.lang.reflect.")
                || typeName.startsWith("java.lang.invoke.")
                || typeName.equals("java.lang.reflect.Proxy")
                || typeName.equals("java.net.URLClassLoader")
                || typeName.equals("java.util.ServiceLoader")
                || typeName.startsWith("java.io.ObjectInput")
                || typeName.startsWith("java.io.ObjectOutput")
                || typeName.equals("java.io.Serializable")
                || typeName.equals("java.nio.MappedByteBuffer")
                || typeName.equals("java.lang.invoke.VarHandle")
                || typeName.equals("sun.misc.Unsafe")
                || typeName.startsWith("jdk.internal.")
                || typeName.startsWith("sun.");
    }

    private static boolean isFixedImageIoQualification(
            JavaClass caller, String owner, String name) {
        return caller.getName().equals("io.github.mundanej.map.awt.AwtRasterDecoders")
                && owner.equals("java.lang.Class")
                && name.equals("getModule");
    }

    private static boolean isProhibitedAccess(JavaClass ownerType, String name) {
        String owner = ownerType.getName();
        if (isProhibitedDirectType(owner)) {
            return true;
        }
        if (owner.equals("java.lang.Class")) {
            return !name.equals("getResource")
                    && !name.equals("getResourceAsStream")
                    && !name.equals("desiredAssertionStatus");
        }
        if (ownerType.isAssignableTo(ClassLoader.class)) {
            return name.equals("loadClass")
                    || name.equals("defineClass")
                    || name.equals("getResources")
                    || name.equals("getSystemResources")
                    || name.equals("resources");
        }
        if (owner.equals("java.lang.Thread") && name.equals("getContextClassLoader")) {
            return true;
        }
        if (owner.equals("java.nio.channels.FileChannel") && name.equals("map")) {
            return true;
        }
        return (owner.equals("java.lang.System") || owner.equals("java.lang.Runtime"))
                && (name.equals("load") || name.equals("loadLibrary"));
    }

    private static boolean hasMutableStaticRegistry(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
                .anyMatch(
                        method ->
                                accessesStaticRegistryHolder(method)
                                        && method.getMethodCallsFromSelf().stream()
                                                .anyMatch(
                                                        call ->
                                                                isMutationMethod(
                                                                        call.getTargetOwner()
                                                                                .getName(),
                                                                        call.getName())));
    }

    private static boolean accessesStaticRegistryHolder(JavaMethod method) {
        return method.getFieldAccesses().stream()
                .map(access -> access.getTarget().resolveMember())
                .flatMap(java.util.Optional::stream)
                .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
                .map(JavaField::getRawType)
                .anyMatch(ArchitecturePolicy::isRegistryHolderType);
    }

    private static boolean isRegistryHolderType(JavaClass type) {
        String name = type.getName();
        return type.isAssignableTo(Map.class)
                || type.isAssignableTo(Collection.class)
                || name.endsWith("Registry")
                || name.endsWith("PluginManager");
    }

    private static boolean isMutationMethod(String owner, String name) {
        if (owner.endsWith("Registry") || owner.endsWith("PluginManager")) {
            return name.startsWith("register") || name.startsWith("install");
        }
        return owner.startsWith("java.util.")
                && (name.equals("put")
                        || name.equals("putAll")
                        || name.equals("putIfAbsent")
                        || name.equals("compute")
                        || name.equals("computeIfAbsent")
                        || name.equals("computeIfPresent")
                        || name.equals("merge")
                        || name.equals("replace")
                        || name.equals("replaceAll")
                        || name.equals("remove")
                        || name.equals("clear")
                        || name.equals("add")
                        || name.equals("addAll"));
    }

    private static boolean isDiscoveryMetadata(String path) {
        return path.startsWith("META-INF/services/")
                || path.equals("META-INF/spring.factories")
                || path.equals(
                        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    }

    private static String diagnostic(
            String boundary, String module, JavaClass origin, String offendingSymbol) {
        return boundary
                + " boundary violated by "
                + module
                + ": "
                + origin.getName()
                + " -> "
                + offendingSymbol;
    }

    private static Map<String, String> jdkModulesByPackage() {
        Map<String, String> result = new HashMap<>();
        for (Module module : ModuleLayer.boot().modules()) {
            if (module.getName() != null) {
                module.getPackages()
                        .forEach(packageName -> result.put(packageName, module.getName()));
            }
        }
        return Map.copyOf(result);
    }

    record ModuleDescriptor(
            String path,
            String category,
            int releaseLevel,
            boolean nativeTarget,
            Path classesDirectory,
            Set<Path> resourcesDirectories,
            Set<String> allowedRuntimeProjects) {}
}
