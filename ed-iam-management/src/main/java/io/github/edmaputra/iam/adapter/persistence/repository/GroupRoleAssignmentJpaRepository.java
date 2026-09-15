package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.GroupRoleAssignmentJpaEntity;

/**
 * Spring Data JPA repository for {@link GroupRoleAssignmentJpaEntity}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface GroupRoleAssignmentJpaRepository extends JpaRepository<GroupRoleAssignmentJpaEntity, UUID> {

	/**
	 * Finds all role assignments for a group.
	 *
	 * @param groupId the group UUID
	 * @return list of assignments
	 */
	List<GroupRoleAssignmentJpaEntity> findAllByGroupId(UUID groupId);

	/**
	 * Finds all role assignments for a collection of group IDs.
	 *
	 * @param groupIds list of group UUIDs
	 * @return list of assignments
	 */
	List<GroupRoleAssignmentJpaEntity> findAllByGroupIdIn(List<UUID> groupIds);

	/**
	 * Finds all group role assignments in a tenant.
	 *
	 * @param tenantId the tenant UUID
	 * @return list of assignments
	 */
	List<GroupRoleAssignmentJpaEntity> findAllByTenantId(UUID tenantId);
}
