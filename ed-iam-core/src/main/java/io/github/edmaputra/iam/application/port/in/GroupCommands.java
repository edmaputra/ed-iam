package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Commands for administrative user group management and group role assignments.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class GroupCommands {

	private GroupCommands() {}

	/**
	 * Command to create a user group within a tenant.
	 *
	 * @param tenantId             the tenant ID
	 * @param code                 the unique group code
	 * @param name                 the display name
	 * @param description          group description
	 * @param externalIdpGroupName optional mapping to external IdP group claim
	 * @author edmaputra
	 * @since 1.0.0
	 */
	public record CreateGroupCommand(
			TenantId tenantId,
			String code,
			String name,
			String description,
			String externalIdpGroupName) {

		public CreateGroupCommand {
			Objects.requireNonNull(tenantId, "TenantId must not be null.");
			Objects.requireNonNull(code, "Group code must not be null.");
			Objects.requireNonNull(name, "Group name must not be null.");
			if (code.isBlank()) {
				throw new IllegalArgumentException("Group code must not be blank.");
			}
			if (name.isBlank()) {
				throw new IllegalArgumentException("Group name must not be blank.");
			}
		}
	}

	/**
	 * Command to update an existing group.
	 *
	 * @param groupId              the group ID
	 * @param name                 updated display name
	 * @param description          updated description
	 * @param externalIdpGroupName updated external IdP group mapping
	 * @author edmaputra
	 * @since 1.0.0
	 */
	public record UpdateGroupCommand(
			GroupId groupId,
			String name,
			String description,
			String externalIdpGroupName) {

		public UpdateGroupCommand {
			Objects.requireNonNull(groupId, "GroupId must not be null.");
			Objects.requireNonNull(name, "Group name must not be null.");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Group name must not be blank.");
			}
		}
	}

	/**
	 * Command to assign a role to a group.
	 *
	 * @param groupId     the target group ID
	 * @param roleId      the role ID
	 * @param tenantId    the tenant context
	 * @param scopeNodeId optional organizational scope boundary
	 * @author edmaputra
	 * @since 1.0.0
	 */
	public record AssignGroupRoleCommand(
			GroupId groupId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId) {

		public AssignGroupRoleCommand {
			Objects.requireNonNull(groupId, "GroupId must not be null.");
			Objects.requireNonNull(roleId, "RoleId must not be null.");
			Objects.requireNonNull(tenantId, "TenantId must not be null.");
		}
	}
}
