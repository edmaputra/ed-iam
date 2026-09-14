package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique UserRoleAssignment identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record UserRoleAssignmentId(UUID value) {

	public UserRoleAssignmentId {
		Objects.requireNonNull(value, "UserRoleAssignmentId value must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 UserRoleAssignment identifier.
	 *
	 * @return new {@link UserRoleAssignmentId}
	 */
	public static UserRoleAssignmentId generate() {
		return new UserRoleAssignmentId(UuidV7.generate());
	}
}
