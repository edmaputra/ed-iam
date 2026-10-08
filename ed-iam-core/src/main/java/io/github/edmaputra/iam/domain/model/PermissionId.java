package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique Permission identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.10.0
 */
public record PermissionId(UUID value) {

	public PermissionId {
		Objects.requireNonNull(value, "Permission ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Permission identifier.
	 *
	 * @return new {@link PermissionId}
	 */
	public static PermissionId generate() {
		return new PermissionId(UuidV7.generate());
	}
}
