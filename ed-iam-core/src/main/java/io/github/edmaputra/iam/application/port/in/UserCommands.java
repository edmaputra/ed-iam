package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Commands for administrative user lifecycle, status changes, and role/group assignments.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class UserCommands {

	private UserCommands() {}

	/**
	 * Command to provision a new user account.
	 *
	 * @param email              user email address
	 * @param password           raw initial password (optional for SSO users)
	 * @param fullName           user display name
	 * @param platformSuperAdmin whether user has global superadmin privileges
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record CreateUserCommand(
			String email,
			String password,
			String fullName,
			boolean platformSuperAdmin) {

		public CreateUserCommand {
			Objects.requireNonNull(email, "Email must not be null.");
			Objects.requireNonNull(fullName, "FullName must not be null.");
			if (email.isBlank()) {
				throw new IllegalArgumentException("Email must not be blank.");
			}
			if (fullName.isBlank()) {
				throw new IllegalArgumentException("FullName must not be blank.");
			}
		}
	}

	/**
	 * Command to update user profile information.
	 *
	 * @param userId   the user ID
	 * @param fullName updated display name
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record UpdateUserCommand(UserId userId, String fullName) {
		public UpdateUserCommand {
			Objects.requireNonNull(userId, "UserId must not be null.");
			Objects.requireNonNull(fullName, "FullName must not be null.");
			if (fullName.isBlank()) {
				throw new IllegalArgumentException("FullName must not be blank.");
			}
		}
	}

	/**
	 * Command to transition user status (active, suspended, deactivated).
	 *
	 * @param userId the user ID
	 * @param status the target user status
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record ChangeUserStatusCommand(UserId userId, UserStatus status) {
		public ChangeUserStatusCommand {
			Objects.requireNonNull(userId, "UserId must not be null.");
			Objects.requireNonNull(status, "UserStatus must not be null.");
		}
	}

	/**
	 * Command to assign a role to a user.
	 *
	 * @param userId      the target user ID
	 * @param roleId      the role ID to assign
	 * @param tenantId    the tenant context
	 * @param scopeNodeId optional organizational scope boundary
	 * @author edmaputra
	 * @since 0.0.1
	 */
	public record AssignUserRoleCommand(
			UserId userId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId) {

		public AssignUserRoleCommand {
			Objects.requireNonNull(userId, "UserId must not be null.");
			Objects.requireNonNull(roleId, "RoleId must not be null.");
			Objects.requireNonNull(tenantId, "TenantId must not be null.");
		}
	}
}
