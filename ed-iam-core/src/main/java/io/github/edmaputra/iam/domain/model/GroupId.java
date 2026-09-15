package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique Group identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record GroupId(UUID value) {

	public GroupId {
		Objects.requireNonNull(value, "Group ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Group identifier.
	 *
	 * @return new {@link GroupId}
	 */
	public static GroupId generate() {
		return new GroupId(UuidV7.generate());
	}
}
