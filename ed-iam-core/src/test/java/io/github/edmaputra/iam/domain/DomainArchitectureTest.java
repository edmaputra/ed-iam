package io.github.edmaputra.iam.domain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural tests verifying pure Java domain invariants in ed-iam-core.
 *
 * @author edmaputra
 * @since 0.1.0
 */
@AnalyzeClasses(
		packages = "io.github.edmaputra.iam.domain",
		importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainArchitectureTest {

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

	@ArchTest
	static final ArchRule domain_must_not_depend_on_application =
			noClasses().that().resideInAPackage("..domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("..application..", "..adapter..");
}
