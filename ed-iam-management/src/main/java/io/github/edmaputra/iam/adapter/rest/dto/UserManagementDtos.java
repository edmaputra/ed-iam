package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
			@NotBlank(message = "Email must not be blank.")
			@Email(message = "Email must be a valid email address.")
			String email,

			@NotBlank(message = "Password must not be blank.")
			@Size(min = 8, message = "Password must be at least 8 characters.")
			String password,

			@NotBlank(message = "Full name must not be blank.")
			String fullName,

			Boolean platformSuperAdmin) {

		public boolean isPlatformSuperAdmin() {
			return Boolean.TRUE.equals(platformSuperAdmin);
		}
	}

	public record UpdateUserRequest(
			@NotBlank(message = "Full name must not be blank.")
			String fullName) {}

	public record ChangeUserStatusRequest(
			@NotNull(message = "Status must not be null.")
			UserStatus status) {}

	public record AssignUserRoleRequest(
			@NotNull(message = "Role ID must not be null.")
			UUID roleId,

			@NotNull(message = "Tenant ID must not be null.")
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
