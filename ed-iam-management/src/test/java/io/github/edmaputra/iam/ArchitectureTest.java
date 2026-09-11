package io.github.edmaputra.iam;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architectural fitness tests verifying Hexagonal Architecture (Ports and Adapters) boundaries
 * and layer dependency rules across the ed-iam module.
 *
 * @author edmaputra
 * @since 0.1.0
 */
@AnalyzeClasses(
		packages = "io.github.edmaputra.iam",
		importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

	// --- Domain layer must have ZERO framework dependencies ---

	@ArchTest
	static final ArchRule domain_must_not_depend_on_spring =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("org.springframework..");

	@ArchTest
	static final ArchRule domain_must_not_depend_on_jpa =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("jakarta.persistence..", "org.hibernate..");

	// --- Domain must not depend on application or adapter ---

	@ArchTest
	static final ArchRule domain_must_not_depend_on_application =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..application..", "..adapter..");

	// --- Application must not depend on adapters ---

	@ArchTest
	static final ArchRule application_must_not_depend_on_adapters =
			noClasses().that().resideInAPackage("..application..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..adapter..");

	// --- Adapters must not depend on each other ---

	@ArchTest
	static final ArchRule adapters_must_not_depend_on_each_other =
			slices().matching("..adapter.(*)..")
					.should().notDependOnEachOther();

	// --- Controllers must not access repositories directly ---

	@ArchTest
	static final ArchRule controllers_must_not_access_persistence =
			noClasses().that().resideInAPackage("..adapter.rest..")
					.should().dependOnClassesThat()
					.resideInAPackage("..adapter.persistence..");
}
