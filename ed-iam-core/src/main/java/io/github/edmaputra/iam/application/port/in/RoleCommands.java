package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;
import java.util.Set;

import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Commands for administrative custom role creation, update, and deletion.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class RoleCommands {

	private RoleCommands() {}

	/**
	 * Command to define a new custom role within a tenant.
	 *
	 * @param tenantId    the tenant ID
	 * @param code        the unique role code (within tenant)
	 * @param name        the display name
	 * @param description role description
	 * @param permissions set of granted permission keys
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record CreateRoleCommand(
			TenantId tenantId,
			String code,
			String name,
			String description,
			Set<String> permissions) {

		public CreateRoleCommand {
			Objects.requireNonNull(tenantId, "TenantId must not be null.");
			Objects.requireNonNull(code, "Role code must not be null.");
			Objects.requireNonNull(name, "Role name must not be null.");
			if (code.isBlank()) {
				throw new IllegalArgumentException("Role code must not be blank.");
			}
			if (name.isBlank()) {
				throw new IllegalArgumentException("Role name must not be blank.");
			}
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}

	/**
	 * Command to update an existing role.
	 *
	 * @param roleId      the role ID
	 * @param name        updated display name
	 * @param description updated description
	 * @param permissions updated permission set
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record UpdateRoleCommand(
			RoleId roleId,
			String name,
			String description,
			Set<String> permissions) {

		public UpdateRoleCommand {
			Objects.requireNonNull(roleId, "RoleId must not be null.");
			Objects.requireNonNull(name, "Role name must not be null.");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Role name must not be blank.");
			}
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}
}
