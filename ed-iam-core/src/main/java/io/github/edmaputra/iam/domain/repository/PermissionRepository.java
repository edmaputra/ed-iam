package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Domain repository port for managing {@link Permission} catalog entities.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public interface PermissionRepository {

	/**
	 * Finds a permission by its unique identifier.
	 *
	 * @param id the unique permission ID
	 * @return optional containing the permission if found
	 */
	Optional<Permission> findById(PermissionId id);

	/**
	 * Finds a tenant-specific permission by tenant ID and code.
	 *
	 * @param tenantId the tenant ID
	 * @param code the permission code
	 * @return optional containing the permission if found
	 */
	Optional<Permission> findByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Finds a global system permission by code.
	 *
	 * @param code the system permission code
	 * @return optional containing the system permission if found
	 */
	Optional<Permission> findSystemPermissionByCode(String code);

	/**
	 * Finds a permission matching code either within the given tenant or as a global system permission.
	 *
	 * @param tenantId the tenant ID (may be null)
	 * @param code the permission code
	 * @return optional containing the permission if found
	 */
	Optional<Permission> findByTenantIdOrGlobalByCode(TenantId tenantId, String code);

	/**
	 * Finds all permissions available to a tenant (including global system permissions).
	 *
	 * @param tenantId the tenant ID
	 * @return list of available permissions
	 */
	List<Permission> findAllByTenantIdOrGlobal(TenantId tenantId);

	/**
	 * Finds all global system permissions.
	 *
	 * @return list of global permissions
	 */
	List<Permission> findAllGlobal();

	/**
	 * Finds all permissions available to a tenant filtered by category.
	 *
	 * @param tenantId the tenant ID
	 * @param category category name
	 * @return list of matching permissions
	 */
	List<Permission> findAllByTenantIdOrGlobalAndCategory(TenantId tenantId, String category);

	/**
	 * Finds all global permissions filtered by category.
	 *
	 * @param category category name
	 * @return list of matching permissions
	 */
	List<Permission> findAllGlobalAndCategory(String category);

	/**
	 * Finds all permissions matching the given IDs.
	 *
	 * @param ids iterable of permission IDs
	 * @return list of matching permissions
	 */
	List<Permission> findAllByIds(Iterable<PermissionId> ids);

	/**
	 * Checks if a permission with the given code exists in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param code the permission code
	 * @return true if the permission exists
	 */
	boolean existsByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Checks if a global system permission with the given code exists.
	 *
	 * @param code the system permission code
	 * @return true if the system permission exists
	 */
	boolean existsSystemPermissionByCode(String code);

	/**
	 * Saves or updates a permission entity.
	 *
	 * @param permission the permission to persist
	 * @return the persisted permission
	 */
	Permission save(Permission permission);

	/**
	 * Deletes a permission by unique identifier.
	 *
	 * @param id the permission ID
	 */
	void delete(PermissionId id);
}
