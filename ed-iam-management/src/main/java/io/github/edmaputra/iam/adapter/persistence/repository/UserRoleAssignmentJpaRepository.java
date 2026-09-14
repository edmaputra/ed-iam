package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.UserRoleAssignmentJpaEntity;

/**
 * Spring Data JPA repository for {@link UserRoleAssignmentJpaEntity}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface UserRoleAssignmentJpaRepository extends JpaRepository<UserRoleAssignmentJpaEntity, UUID> {

	/**
	 * Finds all role assignments for a user.
	 *
	 * @param userId the user UUID
	 * @return list of assignments
	 */
	List<UserRoleAssignmentJpaEntity> findAllByUserId(UUID userId);

	/**
	 * Finds all role assignments for a user in a tenant.
	 *
	 * @param userId   the user UUID
	 * @param tenantId the tenant UUID
	 * @return list of assignments
	 */
	List<UserRoleAssignmentJpaEntity> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);
}
