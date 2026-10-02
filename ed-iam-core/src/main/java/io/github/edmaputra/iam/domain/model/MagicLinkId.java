package io.github.edmaputra.iam.domain.model;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Strongly-typed value object representing a unique Magic Link identifier (UUIDv7).
 *
 * @param value the underlying UUID value
 * @author edmaputra
 * @since 0.7.0
 */
public record MagicLinkId(UUID value) {

	public MagicLinkId {
		Objects.requireNonNull(value, "Magic Link ID must not be null.");
	}

	/**
	 * Generates a new time-ordered UUIDv7 Magic Link identifier.
	 *
	 * @return new {@link MagicLinkId}
	 */
	public static MagicLinkId generate() {
		return new MagicLinkId(UuidV7.generate());
	}

	/**
	 * Creates a {@link MagicLinkId} from an existing {@link UUID}.
	 *
	 * @param value the UUID value
	 * @return new {@link MagicLinkId}
	 */
	public static MagicLinkId of(UUID value) {
		return new MagicLinkId(value);
	}

	/**
	 * Parses a {@link MagicLinkId} from its string UUID representation.
	 *
	 * @param value the string representation
	 * @return new {@link MagicLinkId}
	 */
	public static MagicLinkId of(String value) {
		Objects.requireNonNull(value, "Magic Link ID string must not be null.");
		return new MagicLinkId(UUID.fromString(value.trim()));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
