package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique ScopeNode identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record ScopeNodeId(UUID value) {

	public ScopeNodeId {
		Objects.requireNonNull(value, "ScopeNode ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 ScopeNode identifier.
	 *
	 * @return new {@link ScopeNodeId}
	 */
	public static ScopeNodeId generate() {
		return new ScopeNodeId(UuidV7.generate());
	}

	/**
	 * Creates a ScopeNodeId from an existing UUID.
	 *
	 * @param value the UUID
	 * @return new {@link ScopeNodeId}
	 */
	public static ScopeNodeId of(UUID value) {
		return new ScopeNodeId(value);
	}

	/**
	 * Creates a ScopeNodeId from a UUID string representation.
	 *
	 * @param value the UUID string
	 * @return new {@link ScopeNodeId}
	 */
	public static ScopeNodeId from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("ScopeNode ID must not be blank.");
		}
		return new ScopeNodeId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
