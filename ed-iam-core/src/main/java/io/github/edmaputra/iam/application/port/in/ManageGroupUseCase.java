package io.github.edmaputra.iam.application.port.in;

import java.util.List;
import java.util.UUID;

import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound port for managing user groups, group role assignments, and group membership listings.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface ManageGroupUseCase {

	/**
	 * Creates a new user group within a tenant.
	 *
	 * @param command the group creation command
	 * @return the created {@link Group}
	 */
	Group createGroup(CreateGroupCommand command);

	/**
	 * Retrieves a group by ID.
	 *
	 * @param id the group ID
	 * @return the matching {@link Group}
	 */
	Group getGroupById(GroupId id);

	/**
	 * Lists all groups in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of groups
	 */
	List<Group> getGroupsByTenant(TenantId tenantId);

	/**
	 * Updates the display name, description, or external IdP group mapping for a group.
	 *
	 * @param command the update command
	 * @return the updated {@link Group}
	 */
	Group updateGroup(UpdateGroupCommand command);

	/**
	 * Deletes a group, cascading removals to memberships and group role assignments.
	 *
	 * @param id the group ID
	 */
	void deleteGroup(GroupId id);

	/**
	 * Assigns a role to a group, optionally bounded by an organizational scope node.
	 *
	 * @param command the role assignment command
	 * @return the created {@link GroupRoleAssignment}
	 */
	GroupRoleAssignment assignRole(AssignGroupRoleCommand command);

	/**
	 * Revokes an existing group role assignment.
	 *
	 * @param assignmentId the assignment UUID
	 */
	void revokeRole(UUID assignmentId);

	/**
	 * Lists all role assignments for a group.
	 *
	 * @param groupId the group ID
	 * @return list of group role assignments
	 */
	List<GroupRoleAssignment> getRoleAssignments(GroupId groupId);

	/**
	 * Lists all user members belonging to a group.
	 *
	 * @param groupId the group ID
	 * @return list of user members
	 */
	List<User> getGroupMembers(GroupId groupId);
}
