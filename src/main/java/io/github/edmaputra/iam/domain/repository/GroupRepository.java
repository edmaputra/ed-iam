package io.github.edmaputra.iam.domain.repository;

import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;

/**
 * Domain repository port for managing {@link Group} entities.
 *
 * @author edmaputra
 */
public interface GroupRepository {

	/**
	 * Finds a group by unique identifier.
	 *
	 * @param id the unique group ID
	 * @return optional containing the group if found
	 */
	Optional<Group> findById(GroupId id);

	/**
	 * Finds a group by tenant ID and unique code.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the group code
	 * @return optional containing the group if found
	 */
	Optional<Group> findByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Finds all groups within a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of groups
	 */
	List<Group> findAllByTenantId(TenantId tenantId);

	/**
	 * Finds all groups matching the given group IDs.
	 *
	 * @param ids iterable of group IDs
	 * @return list of matching groups
	 */
	List<Group> findAllByIds(Iterable<GroupId> ids);

	/**
	 * Checks if a group with the given code exists in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the group code
	 * @return true if the group exists
	 */
	boolean existsByTenantIdAndCode(TenantId tenantId, String code);

	/**
	 * Finds a group mapped to an external identity provider group name.
	 *
	 * @param tenantId              the tenant ID
	 * @param externalIdpGroupName  the external group name claim from the IdP
	 * @return optional containing the matching group if found
	 */
	Optional<Group> findByTenantIdAndExternalIdpGroupName(TenantId tenantId, String externalIdpGroupName);

	/**
	 * Saves or updates a group entity.
	 *
	 * @param group the group to persist
	 * @return the persisted group
	 */
	Group save(Group group);

	/**
	 * Deletes a group by unique identifier.
	 *
	 * @param id the group ID
	 */
	void delete(GroupId id);
}
