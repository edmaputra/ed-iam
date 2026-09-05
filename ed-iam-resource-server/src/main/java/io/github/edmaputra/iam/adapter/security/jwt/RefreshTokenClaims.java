package io.github.edmaputra.iam.adapter.security.jwt;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Claims extracted and validated from a signed JWT refresh token.
 *
 * @param userId    the internal user ID
 * @param tenantId  optional tenant ID the refresh token was scoped to
 * @param issuedAt  timestamp when the refresh token was issued
 * @param expiresAt timestamp when the refresh token expires
 * @author edmaputra
 * @since 1.0.0
 */
public record RefreshTokenClaims(
		UserId userId,
		TenantId tenantId,
		Instant issuedAt,
		Instant expiresAt) {

	public RefreshTokenClaims {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(issuedAt, "IssuedAt must not be null.");
		Objects.requireNonNull(expiresAt, "ExpiresAt must not be null.");
	}

	/**
	 * Returns the optional tenant ID context.
	 *
	 * @return optional {@link TenantId}
	 */
	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}
}
