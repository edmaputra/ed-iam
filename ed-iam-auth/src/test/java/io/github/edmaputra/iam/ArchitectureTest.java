package io.github.edmaputra.iam;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural tests verifying package constraints and framework isolation for ed-iam-auth.
 *
 * @author edmaputra
 * @since 0.3.0
 */
@AnalyzeClasses(
		packages = "io.github.edmaputra.iam",
		importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

	@ArchTest
	static final ArchRule auth_module_must_not_depend_on_jpa_or_hibernate =
			noClasses().that().resideInAPackage("io.github.edmaputra.iam..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("jakarta.persistence..", "org.hibernate..");
}
