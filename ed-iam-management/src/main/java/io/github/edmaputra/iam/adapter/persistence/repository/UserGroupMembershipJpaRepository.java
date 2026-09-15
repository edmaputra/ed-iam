package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.entity.UserGroupMembershipJpaId;

/**
 * Spring Data JPA repository for {@link UserGroupMembershipJpaEntity}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface UserGroupMembershipJpaRepository extends JpaRepository<UserGroupMembershipJpaEntity, UserGroupMembershipJpaId> {

	/**
	 * Finds all memberships for a user.
	 *
	 * @param userId the user UUID
	 * @return list of memberships
	 */
	List<UserGroupMembershipJpaEntity> findAllByIdUserId(UUID userId);

	/**
	 * Finds all memberships in a group.
	 *
	 * @param groupId the group UUID
	 * @return list of memberships
	 */
	List<UserGroupMembershipJpaEntity> findAllByIdGroupId(UUID groupId);

	/**
	 * Checks existence of membership.
	 *
	 * @param groupId the group UUID
	 * @param userId  the user UUID
	 * @return true if membership exists
	 */
	boolean existsByIdGroupIdAndIdUserId(UUID groupId, UUID userId);
}
