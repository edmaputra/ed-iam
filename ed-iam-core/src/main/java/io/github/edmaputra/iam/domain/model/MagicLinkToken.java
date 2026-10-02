package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;

import io.github.edmaputra.iam.domain.exception.MagicLinkConsumedException;
import io.github.edmaputra.iam.domain.exception.MagicLinkExpiredException;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantOwned;

/**
 * Pure domain record representing a cryptographically secure, single-use magic link token
 * for passwordless authentication.
 *
 * @param id         unique magic link identifier
 * @param token      the secret high-entropy token string
 * @param userId     the ID of the user requesting authentication
 * @param tenantId   optional tenant context
 * @param email      the destination email address
 * @param expiresAt  timestamp when the token expires
 * @param consumedAt timestamp when the token was consumed, or null if unused
 * @param createdAt  timestamp when the token was created
 * @author edmaputra
 * @since 0.6.0
 */
public record MagicLinkToken(
		MagicLinkId id,
		String token,
		UserId userId,
		TenantId tenantId,
		String email,
		Instant expiresAt,
		Instant consumedAt,
		Instant createdAt) implements TenantOwned {

	public MagicLinkToken {
		Objects.requireNonNull(id, "Magic Link ID must not be null.");
		Objects.requireNonNull(token, "Token must not be null.");
		if (token.isBlank()) {
			throw new IllegalArgumentException("Token must not be blank.");
		}
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email must not be blank.");
		}
		Objects.requireNonNull(expiresAt, "ExpiresAt must not be null.");
		Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
	}

	/**
	 * Factory method issuing a new active, unconsumed magic link token.
	 *
	 * @param token     the secret high-entropy token string
	 * @param userId    the user ID requesting authentication
	 * @param tenantId  optional tenant ID context
	 * @param email     the destination email address
	 * @param expiresAt timestamp when the token expires
	 * @param createdAt timestamp when the token was issued
	 * @return new unconsumed {@link MagicLinkToken}
	 */
	public static MagicLinkToken issue(
			String token,
			UserId userId,
			TenantId tenantId,
			String email,
			Instant expiresAt,
			Instant createdAt) {
		return new MagicLinkToken(
				MagicLinkId.generate(),
				token,
				userId,
				tenantId,
				email,
				expiresAt,
				null,
				createdAt);
	}

	/**
	 * Indicates whether this magic link token has already been consumed.
	 *
	 * @return true if consumed
	 */
	public boolean isConsumed() {
		return consumedAt != null;
	}

	/**
	 * Determines whether the magic link token has expired relative to the given timestamp.
	 *
	 * @param now the current timestamp
	 * @return true if expired
	 */
	public boolean isExpired(Instant now) {
		Objects.requireNonNull(now, "Now must not be null.");
		return now.isAfter(expiresAt);
	}

	/**
	 * Determines whether the magic link token is currently valid and usable.
	 *
	 * @param now the current timestamp
	 * @return true if valid (not consumed and not expired)
	 */
	public boolean isValid(Instant now) {
		return !isConsumed() && !isExpired(now);
	}

	/**
	 * Produces a copy of this token marked as consumed at the specified timestamp.
	 *
	 * @param now the consumption timestamp
	 * @return updated {@link MagicLinkToken} with non-null consumedAt
	 * @throws MagicLinkConsumedException if already consumed
	 * @throws MagicLinkExpiredException  if already expired
	 */
	public MagicLinkToken consume(Instant now) {
		Objects.requireNonNull(now, "Now must not be null.");
		if (isConsumed()) {
			throw new MagicLinkConsumedException("Magic link token has already been consumed.");
		}
		if (isExpired(now)) {
			throw new MagicLinkExpiredException("Magic link token has expired.");
		}
		return new MagicLinkToken(
				id,
				token,
				userId,
				tenantId,
				email,
				expiresAt,
				now,
				createdAt);
	}
}
