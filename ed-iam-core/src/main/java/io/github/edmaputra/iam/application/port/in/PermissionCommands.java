package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Commands for administrative custom permission creation, update, and deletion.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public final class PermissionCommands {

	private PermissionCommands() {}

	/**
	 * Command to create a new custom permission within a tenant or as a global system permission.
	 *
	 * @param tenantId the tenant ID (nullable for global system permissions)
	 * @param code the unique permission code
	 * @param name the display name
	 * @param description optional description
	 * @param category optional category
	 *
	 * @author edmaputra
	 * @since 0.10.0
	 */
	public record CreatePermissionCommand(
			TenantId tenantId,
			String code,
			String name,
			String description,
			String category) {

		public CreatePermissionCommand {
			Objects.requireNonNull(code, "Permission code must not be null.");
			Objects.requireNonNull(name, "Permission name must not be null.");
			if (code.isBlank()) {
				throw new IllegalArgumentException("Permission code must not be blank.");
			}
			if (name.isBlank()) {
				throw new IllegalArgumentException("Permission name must not be blank.");
			}
			code = code.trim();
			name = name.trim();
			description = description == null ? null : description.trim();
			category = (category == null || category.isBlank()) ? "GENERAL" : category.trim();
		}
	}

	/**
	 * Command to update an existing permission.
	 *
	 * @param permissionId the permission ID
	 * @param name updated display name
	 * @param description updated description
	 * @param category updated category
	 *
	 * @author edmaputra
	 * @since 0.10.0
	 */
	public record UpdatePermissionCommand(
			PermissionId permissionId,
			String name,
			String description,
			String category) {

		public UpdatePermissionCommand {
			Objects.requireNonNull(permissionId, "PermissionId must not be null.");
			Objects.requireNonNull(name, "Permission name must not be null.");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Permission name must not be blank.");
			}
			name = name.trim();
			description = description == null ? null : description.trim();
			category = (category == null || category.isBlank()) ? "GENERAL" : category.trim();
		}
	}
}
