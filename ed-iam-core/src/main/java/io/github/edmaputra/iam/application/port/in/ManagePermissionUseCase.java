package io.github.edmaputra.iam.application.port.in;

import java.util.List;

import io.github.edmaputra.iam.application.port.in.PermissionCommands.CreatePermissionCommand;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.UpdatePermissionCommand;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound port for managing permissions, querying the catalog, and maintaining custom permissions.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public interface ManagePermissionUseCase {

	/**
	 * Creates a new permission in the catalog.
	 *
	 * @param command the permission creation command
	 * @return the created {@link Permission}
	 */
	Permission createPermission(CreatePermissionCommand command);

	/**
	 * Retrieves a permission by its unique ID.
	 *
	 * @param id the permission ID
	 * @return the matching {@link Permission}
	 */
	Permission getPermissionById(PermissionId id);

	/**
	 * Retrieves a permission by its code for a tenant (falling back to global).
	 *
	 * @param tenantId the optional tenant ID
	 * @param code the permission code
	 * @return the matching {@link Permission}
	 */
	Permission getPermissionByCode(TenantId tenantId, String code);

	/**
	 * Lists all permissions available to a tenant, optionally filtered by category.
	 *
	 * @param tenantId the optional tenant ID
	 * @param category the optional category filter
	 * @return list of matching {@link Permission} entities
	 */
	List<Permission> getPermissions(TenantId tenantId, String category);

	/**
	 * Updates the display name, description, and category of an existing permission.
	 *
	 * @param command the update command
	 * @return the updated {@link Permission}
	 */
	Permission updatePermission(UpdatePermissionCommand command);

	/**
	 * Deletes a custom permission from the catalog.
	 *
	 * @param id the permission ID
	 */
	void deletePermission(PermissionId id);
}
