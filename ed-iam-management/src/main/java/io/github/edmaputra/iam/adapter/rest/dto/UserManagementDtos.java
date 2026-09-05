package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserStatus;

/**
 * Request and response DTOs for user administrative management endpoints.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class UserManagementDtos {

	private UserManagementDtos() {}

	public record CreateUserRequest(
			String email,
			String password,
			String fullName,
			boolean platformSuperAdmin) {}

	public record UpdateUserRequest(String fullName) {}

	public record ChangeUserStatusRequest(UserStatus status) {}

	public record AssignUserRoleRequest(
			UUID roleId,
			UUID tenantId,
			UUID scopeNodeId) {}

	public record UserResponse(
			UUID id,
			String email,
			String fullName,
			String status,
			boolean platformSuperAdmin,
			Instant createdAt,
			Instant updatedAt) {

		public static UserResponse fromDomain(User user) {
			return new UserResponse(
					user.getId().value(),
					user.getEmail(),
					user.getFullName(),
					user.getStatus().name(),
					user.isPlatformSuperAdmin(),
					user.getCreatedAt(),
					user.getUpdatedAt());
		}
	}

	public record UserRoleAssignmentResponse(
			UUID id,
			UUID userId,
			UUID roleId,
			UUID tenantId,
			UUID scopeNodeId) {

		public static UserRoleAssignmentResponse fromDomain(UserRoleAssignment a) {
			return new UserRoleAssignmentResponse(
					a.getId().value(),
					a.getUserId().value(),
					a.getRoleId().value(),
					a.getTenantId().value(),
					a.getScopeNodeId() != null ? a.getScopeNodeId().value() : null);
		}
	}
}
