package io.github.edmaputra.iam.adapter.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HMAC-SHA256 JWT token generation and validation.
 *
 * @param secret                       the secret signing key (minimum 256 bits)
 * @param accessTokenExpirationSeconds  access token validity duration in seconds (default 3600s = 1 hour)
 * @param refreshTokenExpirationSeconds refresh token validity duration in seconds (default 604800s = 7 days)
 * @author edmaputra
 */
@ConfigurationProperties(prefix = "iam.jwt")
public record JwtProperties(
		String secret,
		long accessTokenExpirationSeconds,
		long refreshTokenExpirationSeconds) {

	/** Default 256-bit fallback secret key. */
	public static final String DEFAULT_SECRET = "iam-super-secure-production-ready-default-secret-key-minimum-256-bits-long!";
	/** Default access token validity duration (1 hour). */
	public static final long DEFAULT_ACCESS_TOKEN_EXPIRATION = 3600L;
	/** Default refresh token validity duration (7 days). */
	public static final long DEFAULT_REFRESH_TOKEN_EXPIRATION = 604800L;

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			secret = DEFAULT_SECRET;
		}
		if (accessTokenExpirationSeconds <= 0) {
			accessTokenExpirationSeconds = DEFAULT_ACCESS_TOKEN_EXPIRATION;
		}
		if (refreshTokenExpirationSeconds <= 0) {
			refreshTokenExpirationSeconds = DEFAULT_REFRESH_TOKEN_EXPIRATION;
		}
	}

	/**
	 * Factory method creating a default properties configuration.
	 *
	 * @return default {@link JwtProperties}
	 */
	public static JwtProperties defaultProperties() {
		return new JwtProperties(DEFAULT_SECRET, DEFAULT_ACCESS_TOKEN_EXPIRATION, DEFAULT_REFRESH_TOKEN_EXPIRATION);
	}
}
