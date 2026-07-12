package io.github.mundanej.map.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.github.mundanej.map")
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule API_STAYS_TOOLKIT_NEUTRAL =
            noClasses()
                    .that()
                    .resideInAPackage("..map.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("java.awt..", "javax.swing..", "..map.core..", "..map.awt..");

    @ArchTest
    static final ArchRule CORE_STAYS_TOOLKIT_NEUTRAL =
            noClasses()
                    .that()
                    .resideInAPackage("..map.core..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("java.awt..", "javax.swing..", "..map.awt..");

    @ArchTest
    static final ArchRule NATIVE_TARGETED_CODE_AVOIDS_DYNAMIC_RUNTIME_APIS =
            noClasses()
                    .that()
                    .resideInAnyPackage("..map.api..", "..map.core..", "..map.awt..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "java.lang.reflect..",
                            "java.lang.invoke..",
                            "java.io.ObjectInputStream",
                            "java.io.ObjectOutputStream",
                            "java.net.URLClassLoader");
}

