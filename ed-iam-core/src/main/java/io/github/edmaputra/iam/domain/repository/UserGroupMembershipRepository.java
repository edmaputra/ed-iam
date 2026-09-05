package io.github.edmaputra.iam.domain.repository;

import java.util.List;

import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Domain repository port for managing {@link UserGroupMembership} associations.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface UserGroupMembershipRepository {

	/**
	 * Finds all group memberships for a user.
	 *
	 * @param userId the user ID
	 * @return list of memberships
	 */
	List<UserGroupMembership> findAllByUserId(UserId userId);

	/**
	 * Finds all memberships within a specific group.
	 *
	 * @param groupId the group ID
	 * @return list of memberships
	 */
	List<UserGroupMembership> findAllByGroupId(GroupId groupId);

	/**
	 * Checks if a membership exists between a group and a user.
	 *
	 * @param groupId the group ID
	 * @param userId  the user ID
	 * @return true if the membership exists
	 */
	boolean existsByGroupIdAndUserId(GroupId groupId, UserId userId);

	/**
	 * Saves a group membership.
	 *
	 * @param membership the membership to persist
	 * @return the persisted membership
	 */
	UserGroupMembership save(UserGroupMembership membership);

	/**
	 * Deletes a group membership association.
	 *
	 * @param groupId the group ID
	 * @param userId  the user ID
	 */
	void delete(GroupId groupId, UserId userId);
}
