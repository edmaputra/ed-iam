package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique GroupRoleAssignment identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 */
public record GroupRoleAssignmentId(UUID value) {

	public GroupRoleAssignmentId {
		Objects.requireNonNull(value, "GroupRoleAssignmentId value must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 GroupRoleAssignment identifier.
	 *
	 * @return new {@link GroupRoleAssignmentId}
	 */
	public static GroupRoleAssignmentId generate() {
		return new GroupRoleAssignmentId(UuidV7.generate());
	}
}
