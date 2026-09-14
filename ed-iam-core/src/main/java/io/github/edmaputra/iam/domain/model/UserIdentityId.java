package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique UserIdentity identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record UserIdentityId(UUID value) {

	public UserIdentityId {
		Objects.requireNonNull(value, "UserIdentityId value must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 UserIdentity identifier.
	 *
	 * @return new {@link UserIdentityId}
	 */
	public static UserIdentityId generate() {
		return new UserIdentityId(UuidV7.generate());
	}
}
