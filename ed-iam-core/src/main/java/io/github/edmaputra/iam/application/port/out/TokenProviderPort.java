package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MfaChallengeClaims;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound SPI port for generating and parsing security tokens (access, refresh, and MFA challenge tokens).
 *
 * @author edmaputra
 * @since 0.1.0
 */
public interface TokenProviderPort {

	/**
	 * Creates a signed access token encapsulating the specified actor effective access.
	 *
	 * @param access the resolved effective access model
	 * @return signed access token string
	 */
	String createAccessToken(EffectiveAccess access);

	/**
	 * Creates a signed access token encapsulating the specified actor effective access and token identifier.
	 *
	 * @param access  the resolved effective access model
	 * @param tokenId specific token identifier (jti)
	 * @return signed access token string
	 */
	default String createAccessToken(EffectiveAccess access, String tokenId) {
		return createAccessToken(access);
	}

	/**
	 * Creates a signed refresh token bound to the specified user and optional tenant scope.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID (may be null)
	 * @return signed refresh token string
	 */
	String createRefreshToken(UserId userId, TenantId tenantId);

	/**
	 * Validates and extracts claims from a signed refresh token.
	 *
	 * @param token the raw refresh token string
	 * @return parsed and verified {@link RefreshTokenClaims}
	 */
	RefreshTokenClaims parseRefreshToken(String token);

	/**
	 * Creates a signed temporary MFA challenge token bound to the specified user and optional tenant scope.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID (may be null)
	 * @return signed MFA challenge token string
	 */
	default String createMfaChallengeToken(UserId userId, TenantId tenantId) {
		throw new UnsupportedOperationException("MFA challenge token creation not supported.");
	}

	/**
	 * Validates and extracts claims from a signed MFA challenge token.
	 *
	 * @param token the raw MFA challenge token string
	 * @return parsed and verified {@link MfaChallengeClaims}
	 */
	default MfaChallengeClaims parseMfaChallengeToken(String token) {
		throw new UnsupportedOperationException("MFA challenge token parsing not supported.");
	}

	/**
	 * Returns the configured expiration duration of issued access tokens in seconds.
	 *
	 * @return access token expiration in seconds
	 */
	long getAccessTokenExpirationSeconds();
}
