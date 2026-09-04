package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;

/**
 * Domain repository port for managing {@link Role} catalog entities.
 *
 * @author edmaputra
 */
public interface RoleRepository {

	/**
	 * Finds a role by unique identifier.
	 *
	 * @param id the unique role ID
	 * @return optional containing the role if found
	 */
	Optional<Role> findById(RoleId id);

	/**
	 * Finds a tenant-specific role by tenant ID and code.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the role code
	 * @return optional containing the role if found
	 */
	Optional<Role> findByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Finds a global system role by code.
	 *
	 * @param code the system role code
	 * @return optional containing the system role if found
	 */
	Optional<Role> findSystemRoleByCode(String code);

	/**
	 * Finds all roles available to a tenant (including global system roles).
	 *
	 * @param tenantId the tenant ID
	 * @return list of available roles
	 */
	List<Role> findAllByTenantIdOrGlobal(TenantId tenantId);

	/**
	 * Finds all roles matching the given IDs.
	 *
	 * @param ids iterable of role IDs
	 * @return list of matching roles
	 */
	List<Role> findAllByIds(Iterable<RoleId> ids);

	/**
	 * Checks if a role with the given code exists in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the role code
	 * @return true if the role exists
	 */
	boolean existsByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Saves or updates a role entity.
	 *
	 * @param role the role to persist
	 * @return the persisted role
	 */
	Role save(Role role);

	/**
	 * Deletes a role by unique identifier.
	 *
	 * @param id the role ID
	 */
	void delete(RoleId id);
}
