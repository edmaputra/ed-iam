package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound SPI port for generating and parsing security tokens (access and refresh tokens).
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
	 * Returns the configured expiration duration of issued access tokens in seconds.
	 *
	 * @return access token expiration in seconds
	 */
	long getAccessTokenExpirationSeconds();
}
