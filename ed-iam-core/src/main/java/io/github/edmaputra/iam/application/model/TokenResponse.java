package io.github.edmaputra.iam.application.model;

import java.util.Objects;

/**
 * Authentication token response containing access and refresh tokens along with user profile data.
 *
 * @param accessToken  the signed JWT access token string
 * @param refreshToken the signed JWT refresh token string
 * @param tokenType    the token type (defaults to "Bearer")
 * @param expiresIn    the access token validity duration in seconds
 * @param user         the authenticated user profile response
 * @author edmaputra
 * @since 1.0.0
 */
public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UserProfileResponse user) {

	public TokenResponse {
		Objects.requireNonNull(accessToken, "AccessToken must not be null.");
		Objects.requireNonNull(refreshToken, "RefreshToken must not be null.");
		Objects.requireNonNull(user, "User profile must not be null.");
		if (tokenType == null || tokenType.isBlank()) {
			tokenType = "Bearer";
		}
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
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
	}
}
