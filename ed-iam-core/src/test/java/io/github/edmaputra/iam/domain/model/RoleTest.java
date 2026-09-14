package io.github.edmaputra.iam.domain.model;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying Role entity invariants, immutability, and permission checks.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class RoleTest {

	@Test
	@DisplayName("Should create custom tenant role with permissions")
	void shouldCreateCustomRole() {
		TenantId tenantId = TenantId.generate();
		Role role = Role.createCustom(
				tenantId,
				"PHYSICIAN",
				"General Physician",
				"Can treat patients and write prescriptions",
				Set.of("PATIENT_READ", "PRESCRIPTION_CREATE"));

		assertThat(role.getId()).isNotNull();
		assertThat(role.getTenantId()).isEqualTo(tenantId);
		assertThat(role.optionalTenantId()).contains(tenantId);
		assertThat(role.getCode()).isEqualTo("PHYSICIAN");
		assertThat(role.getName()).isEqualTo("General Physician");
		assertThat(role.isSystemRole()).isFalse();
		assertThat(role.permissions()).containsExactlyInAnyOrder("PATIENT_READ", "PRESCRIPTION_CREATE");
		assertThat(role.hasPermission("PATIENT_READ")).isTrue();
		assertThat(role.hasPermission("BILLING_DELETE")).isFalse();
	}

	@Test
	@DisplayName("Should create system role with null tenant ID")
	void shouldCreateSystemRole() {
		Role systemRole = Role.createSystemRole(
				"SUPERADMIN",
				"Platform Superadmin",
				"Full access across the entire system",
				Set.of("SUPERADMIN_ACCESS"));

		assertThat(systemRole.getId()).isNotNull();
		assertThat(systemRole.getTenantId()).isNull();
		assertThat(systemRole.optionalTenantId()).isEmpty();
		assertThat(systemRole.isSystemRole()).isTrue();
		assertThat(systemRole.getCode()).isEqualTo("SUPERADMIN");
	}

	@Test
	@DisplayName("Should allow updating custom role but reject updating system role")
	void shouldGuardRoleUpdates() {
		TenantId tenantId = TenantId.generate();
		Role customRole = Role.createCustom(
				tenantId,
				"NURSE",
				"Staff Nurse",
				"Inpatient nurse",
				Set.of("PATIENT_READ"));

		customRole.update("Senior Staff Nurse", "Updated description", Set.of("PATIENT_READ", "MEDICATION_ADMINISTER"));
		assertThat(customRole.getName()).isEqualTo("Senior Staff Nurse");
		assertThat(customRole.hasPermission("MEDICATION_ADMINISTER")).isTrue();

		Role systemRole = Role.createSystemRole("SYSTEM_ADMIN", "System Admin", null, Set.of("ALL"));
		assertThatThrownBy(() -> systemRole.update("New Name", "Desc", Set.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable");
	}

	@Test
	@DisplayName("Should reject null or blank role code and name")
	void shouldRejectInvalidRoleCodeAndName() {
		TenantId tenantId = TenantId.generate();

		assertThatThrownBy(() -> Role.createCustom(tenantId, null, "Name", "Desc", Set.of()))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> Role.createCustom(tenantId, "   ", "Name", "Desc", Set.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Role code must not be blank.");

		assertThatThrownBy(() -> Role.createCustom(tenantId, "CODE", null, "Desc", Set.of()))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> Role.createCustom(tenantId, "CODE", "   ", "Desc", Set.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Role name must not be blank.");
	}

	@Test
	@DisplayName("Should safely handle null in hasPermission check")
	void shouldHandleNullInHasPermission() {
		TenantId tenantId = TenantId.generate();
		Role role = Role.createCustom(tenantId, "ROLE", "Role", null, Set.of("READ"));

		assertThat(role.hasPermission(null)).isFalse();
		assertThat(role.optionalDescription()).isEmpty();
	}
}
