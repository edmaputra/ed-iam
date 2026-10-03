package io.github.edmaputra.iam.adapter.security.jwt;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for HMAC-SHA256 JWT token generation and validation.
 *
 * @param secret                       the secret signing key (minimum 256 bits / 32 bytes)
 * @param accessTokenExpirationSeconds  access token validity duration in seconds (default 3600s = 1 hour)
 * @param refreshTokenExpirationSeconds refresh token validity duration in seconds (default 604800s = 7 days)
 * @param issuer                       the JWT issuer claim (default "ed-iam")
 * @param audience                     the JWT audience claim (default "ed-iam-api")
 * @author edmaputra
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "iam.jwt")
public record JwtProperties(
		String secret,
		long accessTokenExpirationSeconds,
		long refreshTokenExpirationSeconds,
		String issuer,
		String audience) {

	/** Test fallback secret key designated exclusively for testing harnesses. */
	public static final String TEST_SECRET = "iam-test-only-secret-key-for-unit-testing-purposes-minimum-256-bits!";
	/** Default 256-bit test fallback secret key (deprecated: use {@link #TEST_SECRET}). */
	@Deprecated
	public static final String DEFAULT_SECRET = TEST_SECRET;
	/** Default access token validity duration (1 hour). */
	public static final long DEFAULT_ACCESS_TOKEN_EXPIRATION = 3600L;
	/** Default refresh token validity duration (7 days). */
	public static final long DEFAULT_REFRESH_TOKEN_EXPIRATION = 604800L;
	/** Default token issuer. */
	public static final String DEFAULT_ISSUER = "ed-iam";
	/** Default token audience. */
	public static final String DEFAULT_AUDIENCE = "ed-iam-api";

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("iam.jwt.secret must not be null or blank. A minimum 256-bit (32 bytes) secret key is required.");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("iam.jwt.secret must be at least 256 bits (32 bytes) long.");
		}
		if (accessTokenExpirationSeconds <= 0) {
			accessTokenExpirationSeconds = DEFAULT_ACCESS_TOKEN_EXPIRATION;
		}
		if (refreshTokenExpirationSeconds <= 0) {
			refreshTokenExpirationSeconds = DEFAULT_REFRESH_TOKEN_EXPIRATION;
		}
		if (issuer == null || issuer.isBlank()) {
			issuer = DEFAULT_ISSUER;
		}
		if (audience == null || audience.isBlank()) {
			audience = DEFAULT_AUDIENCE;
		}
	}

	/**
	 * Factory method creating {@link JwtProperties} with default issuer and audience.
	 *
	 * @param secret                       the secret signing key
	 * @param accessTokenExpirationSeconds  access token validity duration in seconds
	 * @param refreshTokenExpirationSeconds refresh token validity duration in seconds
	 * @return {@link JwtProperties} instance
	 */
	public static JwtProperties of(String secret, long accessTokenExpirationSeconds, long refreshTokenExpirationSeconds) {
		return new JwtProperties(secret, accessTokenExpirationSeconds, refreshTokenExpirationSeconds, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
	}

	/**
	 * Factory method creating a test-only default properties configuration.
	 *
	 * @return default test {@link JwtProperties}
	 */
	public static JwtProperties defaultProperties() {
		return new JwtProperties(TEST_SECRET, DEFAULT_ACCESS_TOKEN_EXPIRATION, DEFAULT_REFRESH_TOKEN_EXPIRATION, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
	}

	/**
	 * Factory method creating a test-only properties configuration.
	 *
	 * @return test {@link JwtProperties}
	 */
	public static JwtProperties forTesting() {
		return defaultProperties();
	}
}
