package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RoleManagementService}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class RoleManagementServiceTest {

	private RoleRepository roleRepository;
	private RoleManagementService service;

	@BeforeEach
	void setUp() {
		roleRepository = mock(RoleRepository.class);
		service = new RoleManagementService(roleRepository);
	}

	@Test
	@DisplayName("Should create role and check duplicate code")
	void shouldCreateRole() {
		TenantId tenantId = TenantId.generate();
		when(roleRepository.existsByTenantIdAndCode(tenantId, "NURSE")).thenReturn(false);
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		Role role = service.createRole(new CreateRoleCommand(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ")));
		assertThat(role.getCode()).isEqualTo("NURSE");

		// Duplicate code
		when(roleRepository.existsByTenantIdAndCode(tenantId, "NURSE")).thenReturn(true);
		assertThatThrownBy(() -> service.createRole(new CreateRoleCommand(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should get role by ID, get by tenant, and update role")
	void shouldGetAndUpdateRole() {
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		Role role = Role.createCustom(tenantId, "NURSE", "Nurse", "Desc", Set.of("READ"));

		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(roleRepository.findAllByTenantIdOrGlobal(tenantId)).thenReturn(List.of(role));
		when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(service.getRoleById(roleId)).isSameAs(role);
		assertThat(service.getRolesByTenant(tenantId)).containsExactly(role);

		Role updated = service.updateRole(new UpdateRoleCommand(roleId, "Senior Nurse", "Senior Desc", Set.of("READ", "WRITE")));
		assertThat(updated.getName()).isEqualTo("Senior Nurse");
	}

	@Test
	@DisplayName("Should throw RoleNotFoundException when role absent")
	void shouldThrowWhenAbsent() {
		RoleId missingId = RoleId.generate();
		when(roleRepository.findById(missingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getRoleById(missingId)).isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	@DisplayName("Should delete custom role but forbid deleting system role")
	void shouldDeleteRole() {
		RoleId customId = RoleId.generate();
		Role custom = Role.createCustom(TenantId.generate(), "CUSTOM", "Custom", "Desc", Set.of());
		when(roleRepository.findById(customId)).thenReturn(Optional.of(custom));

		service.deleteRole(customId);
		verify(roleRepository).delete(customId);

		RoleId systemId = RoleId.generate();
		Role sys = Role.createSystemRole("SYS_ADMIN", "System Admin", "Desc", Set.of());
		when(roleRepository.findById(systemId)).thenReturn(Optional.of(sys));

		assertThatThrownBy(() -> service.deleteRole(systemId)).isInstanceOf(IllegalStateException.class);
	}
}
