package io.github.edmaputra.iam.application.port.in;

import java.util.List;

import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound port for managing custom roles, permissions, and tenant role catalogs.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface ManageRoleUseCase {

	/**
	 * Creates a new custom role within a tenant.
	 *
	 * @param command the role creation command
	 * @return the created {@link Role}
	 */
	Role createRole(CreateRoleCommand command);

	/**
	 * Retrieves a role by ID.
	 *
	 * @param id the role ID
	 * @return the matching {@link Role}
	 */
	Role getRoleById(RoleId id);

	/**
	 * Lists all roles available in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of roles
	 */
	List<Role> getRolesByTenant(TenantId tenantId);

	/**
	 * Updates the display name, description, and permissions of an existing role.
	 *
	 * @param command the update command
	 * @return the updated {@link Role}
	 */
	Role updateRole(UpdateRoleCommand command);

	/**
	 * Deletes a role from a tenant.
	 *
	 * @param id the role ID
	 */
	void deleteRole(RoleId id);
}
