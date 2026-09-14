package io.github.edmaputra.iam.application.port.in;

import java.util.List;
import java.util.UUID;

import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;

/**
 * Inbound port for managing user lifecycles, account statuses, role assignments, and group memberships.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface ManageUserUseCase {

	/**
	 * Creates and provisions a new user account.
	 *
	 * @param command the user creation command
	 * @return the created {@link User}
	 */
	User createUser(CreateUserCommand command);

	/**
	 * Retrieves a user by unique identifier.
	 *
	 * @param id the user ID
	 * @return the matching {@link User}
	 */
	User getUserById(UserId id);

	/**
	 * Retrieves a user by email address.
	 *
	 * @param email the email address
	 * @return the matching {@link User}
	 */
	User getUserByEmail(String email);

	/**
	 * Updates profile details for an existing user.
	 *
	 * @param command the user update command
	 * @return the updated {@link User}
	 */
	User updateUser(UpdateUserCommand command);

	/**
	 * Changes the lifecycle state (active, suspended, deactivated) of a user account.
	 *
	 * @param command the status transition command
	 * @return the updated {@link User}
	 */
	User changeUserStatus(ChangeUserStatusCommand command);

	/**
	 * Deletes a user account and cascades removals to identities, group memberships, and role assignments.
	 *
	 * @param id the user ID
	 */
	void deleteUser(UserId id);

	/**
	 * Assigns a role to a user within a tenant context, optionally bounded by an organizational scope node.
	 *
	 * @param command the role assignment command
	 * @return the created {@link UserRoleAssignment}
	 */
	UserRoleAssignment assignRole(AssignUserRoleCommand command);

	/**
	 * Revokes an existing user role assignment.
	 *
	 * @param assignmentId the unique assignment UUID
	 */
	void revokeRole(UUID assignmentId);

	/**
	 * Lists all active role assignments for a given user.
	 *
	 * @param userId the user ID
	 * @return list of role assignments
	 */
	List<UserRoleAssignment> getRoleAssignments(UserId userId);

	/**
	 * Adds a user as a member of a group.
	 *
	 * @param groupId the group ID
	 * @param userId  the user ID
	 */
	void addUserToGroup(GroupId groupId, UserId userId);

	/**
	 * Removes a user from group membership.
	 *
	 * @param groupId the group ID
	 * @param userId  the user ID
	 */
	void removeUserFromGroup(GroupId groupId, UserId userId);

	/**
	 * Lists all groups a user belongs to.
	 *
	 * @param userId the user ID
	 * @return list of groups
	 */
	List<Group> getUserGroups(UserId userId);
}
