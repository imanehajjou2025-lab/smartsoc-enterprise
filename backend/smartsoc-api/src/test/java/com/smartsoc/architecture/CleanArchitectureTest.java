package com.smartsoc.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the dependency rules of ADR-002 at build time: any violation of
 * the Clean Architecture layering fails the CI. These rules protect the
 * design decisions from accidental erosion as the team grows the codebase.
 */
@AnalyzeClasses(packages = "com.smartsoc", importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    /**
     * The domain is pure Java: no Spring, no JPA, no Jackson, no servlet API.
     * (Lombok is allowed: compile-time only, invisible at runtime.)
     */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("com.smartsoc.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson..")
            .because("the domain must stay framework-free (ADR-002)")
            .allowEmptyShould(true);

    /**
     * Layer access rules: api -> application -> domain <- infrastructure.
     * Optional layers: some are still empty at this stage of the project.
     */
    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy("com.smartsoc.domain..")
            .layer("Application").definedBy("com.smartsoc.application..")
            .layer("Infrastructure").definedBy("com.smartsoc.infrastructure..")
            .layer("Api").definedBy("com.smartsoc.api..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Api")
            .because("dependencies must always point toward the domain (ADR-002)");
}
