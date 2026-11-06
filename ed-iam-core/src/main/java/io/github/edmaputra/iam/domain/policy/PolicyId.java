package io.github.edmaputra.iam.domain.policy;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique ABAC Policy identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 * @author edmaputra
 * @since 0.14.0
 */
public record PolicyId(UUID value) {

	public PolicyId {
		Objects.requireNonNull(value, "Policy ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Policy identifier.
	 *
	 * @return new {@link PolicyId}
	 */
	public static PolicyId generate() {
		return new PolicyId(UuidV7.generate());
	}

	/**
	 * Alias for {@link #generate()}.
	 *
	 * @return new {@link PolicyId}
	 */
	public static PolicyId random() {
		return generate();
	}

	/**
	 * Creates a PolicyId from an existing UUID.
	 *
	 * @param value UUID value
	 * @return new {@link PolicyId}
	 */
	public static PolicyId of(UUID value) {
		return new PolicyId(value);
	}

	/**
	 * Creates a PolicyId from a UUID string.
	 *
	 * @param value UUID string
	 * @return new {@link PolicyId}
	 */
	public static PolicyId of(String value) {
		return new PolicyId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
