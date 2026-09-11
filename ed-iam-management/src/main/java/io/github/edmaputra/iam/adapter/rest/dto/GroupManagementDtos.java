package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;

/**
 * Request and response DTOs for user group administrative management endpoints.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class GroupManagementDtos {

	private GroupManagementDtos() {}

	public record CreateGroupRequest(
			@NotNull(message = "Tenant ID must not be null.")
			UUID tenantId,

			@NotBlank(message = "Group code must not be blank.")
			String code,

			@NotBlank(message = "Group name must not be blank.")
			String name,

			String description,
			String externalIdpGroupName) {}

	public record UpdateGroupRequest(
			@NotBlank(message = "Group name must not be blank.")
			String name,

			String description,
			String externalIdpGroupName) {}

	public record AssignGroupRoleRequest(
			@NotNull(message = "Role ID must not be null.")
			UUID roleId,

			@NotNull(message = "Tenant ID must not be null.")
			UUID tenantId,

			UUID scopeNodeId) {}

	public record GroupResponse(
			UUID id,
			UUID tenantId,
			String code,
			String name,
			String description,
			String externalIdpGroupName,
			Instant createdAt,
			Instant updatedAt) {

		public static GroupResponse fromDomain(Group group) {
			return new GroupResponse(
					group.getId().value(),
					group.getTenantId().value(),
					group.getCode(),
					group.getName(),
					group.getDescription(),
					group.getExternalIdpGroupName(),
					group.getCreatedAt(),
					group.getUpdatedAt());
		}
	}

	public record GroupRoleAssignmentResponse(
			UUID id,
			UUID groupId,
			UUID roleId,
			UUID tenantId,
			UUID scopeNodeId) {

		public static GroupRoleAssignmentResponse fromDomain(GroupRoleAssignment a) {
			return new GroupRoleAssignmentResponse(
					a.getId().value(),
					a.getGroupId().value(),
					a.getRoleId().value(),
					a.getTenantId().value(),
					a.getScopeNodeId() != null ? a.getScopeNodeId().value() : null);
		}
	}
}
