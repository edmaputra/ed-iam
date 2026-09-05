package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique Role identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 1.0.0
 */
public record RoleId(UUID value) {

	public RoleId {
		Objects.requireNonNull(value, "Role ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Role identifier.
	 *
	 * @return new {@link RoleId}
	 */
	public static RoleId generate() {
		return new RoleId(UuidV7.generate());
	}
}
