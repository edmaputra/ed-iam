package io.github.edmaputra.iam.application.model;

import java.util.Objects;

/**
 * Authentication token response containing access and refresh tokens along with user profile data,
 * or an MFA challenge ticket when multi-factor authentication is required.
 *
 * @param accessToken  the signed JWT access token string (null if mfaRequired is true)
 * @param refreshToken the signed JWT refresh token string (null if mfaRequired is true)
 * @param tokenType    the token type (defaults to "Bearer")
 * @param expiresIn    the access token validity duration in seconds
 * @param user         the authenticated user profile response (null if mfaRequired is true)
 * @param mfaRequired  flag indicating if second-factor authentication is required
 * @param mfaToken     temporary MFA challenge token if second-factor verification is required
 * @author edmaputra
 * @since 0.0.1
 */
public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UserProfileResponse user,
		boolean mfaRequired,
		String mfaToken) {

	public TokenResponse {
		if (mfaRequired) {
			Objects.requireNonNull(mfaToken, "MfaToken must not be null when mfaRequired is true.");
			if (tokenType == null || tokenType.isBlank()) {
				tokenType = "Bearer";
			}
		}
		else {
			Objects.requireNonNull(accessToken, "AccessToken must not be null.");
			Objects.requireNonNull(refreshToken, "RefreshToken must not be null.");
			Objects.requireNonNull(user, "User profile must not be null.");
			if (tokenType == null || tokenType.isBlank()) {
				tokenType = "Bearer";
			}
		}
	}

	/**
	 * Backward-compatible 5-argument constructor for fully authenticated token responses.
	 *
	 * @param accessToken  the signed JWT access token
	 * @param refreshToken the signed JWT refresh token
	 * @param tokenType    the token type
	 * @param expiresIn    access token expiration in seconds
	 * @param user         the user profile
	 */
	public TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserProfileResponse user) {
		this(accessToken, refreshToken, tokenType, expiresIn, user, false, null);
	}

	/**
	 * Factory method creating a Bearer token response.
	 *
	 * @param accessToken  the access token string
	 * @param refreshToken the refresh token string
	 * @param expiresIn    access token expiration in seconds
	 * @param user         the user profile
	 * @return new {@link TokenResponse}
	 */
	public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user, false, null);
	}

	/**
	 * Factory method creating an MFA challenge token response.
	 *
	 * @param mfaToken the temporary MFA challenge token
	 * @return new {@link TokenResponse} with mfaRequired set to true
	 */
	public static TokenResponse mfaChallenge(String mfaToken) {
		return new TokenResponse(null, null, "Bearer", 0L, null, true, mfaToken);
	}
}
