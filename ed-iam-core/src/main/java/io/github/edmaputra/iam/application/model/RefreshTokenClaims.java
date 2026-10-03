package io.github.edmaputra.iam.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Claims extracted and validated from a signed JWT refresh token.
 *
 * @param userId    the internal user ID
 * @param tenantId  optional tenant ID the refresh token was scoped to
 * @param issuedAt  timestamp when the refresh token was issued
 * @param expiresAt timestamp when the refresh token expires
 * @param tokenId   unique JWT ID (jti) of the refresh token
 * @author edmaputra
 * @since 0.1.0
 */
public record RefreshTokenClaims(
		UserId userId,
		TenantId tenantId,
		Instant issuedAt,
		Instant expiresAt,
		String tokenId) {

	public RefreshTokenClaims {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(issuedAt, "IssuedAt must not be null.");
		Objects.requireNonNull(expiresAt, "ExpiresAt must not be null.");
	}

	/**
	 * Secondary constructor without explicit token ID for backward compatibility.
	 *
	 * @param userId    the internal user ID
	 * @param tenantId  optional tenant ID
	 * @param issuedAt  issued timestamp
	 * @param expiresAt expiration timestamp
	 */
	public RefreshTokenClaims(UserId userId, TenantId tenantId, Instant issuedAt, Instant expiresAt) {
		this(userId, tenantId, issuedAt, expiresAt, null);
	}

	/**
	 * Returns the optional tenant ID context.
	 *
	 * @return optional {@link TenantId}
	 */
	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	/**
	 * Returns the optional token ID (jti) context.
	 *
	 * @return optional token ID string
	 */
	public Optional<String> optionalTokenId() {
		return Optional.ofNullable(tokenId);
	}
}
