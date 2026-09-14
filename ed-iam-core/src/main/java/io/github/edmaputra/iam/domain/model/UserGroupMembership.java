package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing membership of a user in a group.
 *
 * @param groupId  the group ID
 * @param userId   the user ID
 * @param joinedAt the timestamp when the user joined the group
 * @author edmaputra
 * @since 0.0.1
 */
public record UserGroupMembership(
		GroupId groupId,
		UserId userId,
		Instant joinedAt) {

	public UserGroupMembership {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(joinedAt, "JoinedAt must not be null.");
	}

	/**
	 * Factory method creating a new membership record with the current timestamp.
	 *
	 * @param groupId the group ID
	 * @param userId  the user ID
	 * @return new {@link UserGroupMembership}
	 */
	public static UserGroupMembership of(GroupId groupId, UserId userId) {
		return new UserGroupMembership(groupId, userId, Instant.now());
	}
}
