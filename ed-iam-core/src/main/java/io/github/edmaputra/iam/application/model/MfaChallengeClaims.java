package io.github.edmaputra.iam.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Claims extracted and validated from a temporary signed JWT MFA challenge token.
 *
 * @param userId    the authenticated internal user ID awaiting second-factor confirmation
 * @param tenantId  optional tenant ID context selected during the initial login step
 * @param issuedAt  timestamp when the challenge token was issued
 * @param expiresAt timestamp when the challenge token expires
 * @author edmaputra
 * @since 0.5.0
 */
public record MfaChallengeClaims(
		UserId userId,
		TenantId tenantId,
		Instant issuedAt,
		Instant expiresAt) {

	public MfaChallengeClaims {
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
