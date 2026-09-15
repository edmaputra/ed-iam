package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique User identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record UserId(UUID value) {

	public UserId {
		Objects.requireNonNull(value, "User ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 User identifier.
	 *
	 * @return new {@link UserId}
	 */
	public static UserId generate() {
		return new UserId(UuidV7.generate());
	}
}
