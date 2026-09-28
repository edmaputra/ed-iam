package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique Session identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 * @author edmaputra
 * @since 0.3.0
 */
public record SessionId(UUID value) {

	public SessionId {
		Objects.requireNonNull(value, "Session ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Session identifier.
	 *
	 * @return new {@link SessionId}
	 */
	public static SessionId generate() {
		return new SessionId(UuidV7.generate());
	}

	/**
	 * Creates a {@link SessionId} from an existing {@link UUID}.
	 *
	 * @param value the UUID value
	 * @return new {@link SessionId}
	 */
	public static SessionId of(UUID value) {
		return new SessionId(value);
	}

	/**
	 * Parses a {@link SessionId} from its string UUID representation.
	 *
	 * @param value the string representation
	 * @return new {@link SessionId}
	 */
	public static SessionId of(String value) {
		Objects.requireNonNull(value, "Session ID string must not be null.");
		return new SessionId(UUID.fromString(value.trim()));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
