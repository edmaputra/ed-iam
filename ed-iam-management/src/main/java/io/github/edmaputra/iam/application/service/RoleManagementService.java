package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManageRoleUseCase} for custom role catalog management.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class RoleManagementService implements ManageRoleUseCase {

	private final RoleRepository roleRepository;

	@Override
	public Role createRole(CreateRoleCommand command) {
		Objects.requireNonNull(command, "CreateRoleCommand must not be null.");

		if (roleRepository.existsByTenantIdAndCode(command.tenantId(), command.code())) {
			throw new IllegalArgumentException("Role code already exists for tenant: " + command.code());
		}

		Role role = Role.createCustom(
				command.tenantId(),
				command.code(),
				command.name(),
				command.description(),
				command.permissions());

		return roleRepository.save(role);
	}

	@Override
	public Role getRoleById(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		return roleRepository.findById(id)
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + id.value()));
	}

	@Override
	public List<Role> getRolesByTenant(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		return roleRepository.findAllByTenantIdOrGlobal(tenantId);
	}

	@Override
	public Role updateRole(UpdateRoleCommand command) {
		Objects.requireNonNull(command, "UpdateRoleCommand must not be null.");
		Role role = getRoleById(command.roleId());
		role.update(command.name(), command.description(), command.permissions());
		return roleRepository.save(role);
	}

	@Override
	public void deleteRole(RoleId id) {
		Objects.requireNonNull(id, "RoleId must not be null.");
		Role role = getRoleById(id);
		if (role.isSystemRole()) {
			throw new IllegalStateException("System role cannot be deleted: " + role.getCode());
		}
		roleRepository.delete(id);
	}
}
