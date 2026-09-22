package io.github.edmaputra.iam.adapter.security;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural tests verifying package constraints and framework isolation for ed-iam-resource-server.
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
	static final ArchRule resource_server_classes_must_reside_in_adapter_security =
			classes().that().resideInAPackage("io.github.edmaputra.iam..")
					.should().resideInAPackage("io.github.edmaputra.iam.adapter.security..");

	@ArchTest
	static final ArchRule resource_server_must_not_depend_on_jpa_or_hibernate =
			noClasses().that().resideInAPackage("..adapter.security..")
					.should().dependOnClassesThat()
					.resideInAnyPackage("jakarta.persistence..", "org.hibernate..");
}
