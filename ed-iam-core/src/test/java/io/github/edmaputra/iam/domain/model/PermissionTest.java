package io.github.edmaputra.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying Permission domain entity invariants, immutability, and state transitions.
 *
 * @author edmaputra
 * @since 0.10.0
 */
class PermissionTest {

	@Test
	@DisplayName("Should create custom tenant permission")
	void shouldCreateCustomPermission() {
		TenantId tenantId = TenantId.generate();
		Permission permission = Permission.createCustom(
				tenantId,
				"ehr:patient:read",
				"Read Patient Records",
				"Allows viewing clinical charts and records",
				"EHR");

		assertThat(permission.id()).isNotNull();
		assertThat(permission.tenantId()).isEqualTo(tenantId);
		assertThat(permission.optionalTenantId()).contains(tenantId);
		assertThat(permission.code()).isEqualTo("ehr:patient:read");
		assertThat(permission.name()).isEqualTo("Read Patient Records");
		assertThat(permission.description()).isEqualTo("Allows viewing clinical charts and records");
		assertThat(permission.category()).isEqualTo("EHR");
		assertThat(permission.systemPermission()).isFalse();
		assertThat(permission.createdAt()).isNotNull();
		assertThat(permission.updatedAt()).isNotNull();
	}

	@Test
	@DisplayName("Should create global system permission")
	void shouldCreateSystemPermission() {
		Permission permission = Permission.createSystem(
				"iam:user:create",
				"Create Users",
				"Allows creating user accounts",
				"IAM_USER");

		assertThat(permission.id()).isNotNull();
		assertThat(permission.tenantId()).isNull();
		assertThat(permission.optionalTenantId()).isEmpty();
		assertThat(permission.code()).isEqualTo("iam:user:create");
		assertThat(permission.name()).isEqualTo("Create Users");
		assertThat(permission.systemPermission()).isTrue();
	}

	@Test
	@DisplayName("Should default category to GENERAL when null or blank")
	void shouldDefaultCategoryToGeneral() {
		Permission permission = Permission.createSystem(
				"iam:user:read",
				"Read Users",
				null,
				"   ");

		assertThat(permission.category()).isEqualTo("GENERAL");
		assertThat(permission.optionalDescription()).isEmpty();
	}

	@Test
	@DisplayName("Should allow updating custom permission but reject updating system permission")
	void shouldAllowUpdatingCustomPermissionOnly() {
		TenantId tenantId = TenantId.generate();
		Permission custom = Permission.createCustom(
				tenantId,
				"custom:read",
				"Old Name",
				"Old Desc",
				"CAT1");

		Permission updated = custom.update("New Name", "New Desc", "CAT2");
		assertThat(updated.name()).isEqualTo("New Name");
		assertThat(updated.description()).isEqualTo("New Desc");
		assertThat(updated.category()).isEqualTo("CAT2");
		assertThat(updated.code()).isEqualTo("custom:read");

		Permission system = Permission.createSystem("sys:read", "Sys Name", "Sys Desc", "SYS");
		assertThatThrownBy(() -> system.update("New Name", "New Desc", "SYS"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immutable and cannot be updated");
	}

	@Test
	@DisplayName("Should reject blank code or name")
	void shouldRejectBlankCodeOrName() {
		TenantId tenantId = TenantId.generate();

		assertThatThrownBy(() -> Permission.createCustom(tenantId, "", "Name", null, null))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> Permission.createCustom(tenantId, "code", "  ", null, null))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> Permission.createCustom(null, "code", "Name", null, null))
				.isInstanceOf(NullPointerException.class);
	}
}
