package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

/**
 * Inbound command encapsulating a refresh token to request a new access token.
 *
 * @param refreshToken the signed JWT refresh token string
 *
 * @author edmaputra
 * @since 1.0.0
 */
public record RefreshTokenCommand(String refreshToken) {

	public RefreshTokenCommand {
		Objects.requireNonNull(refreshToken, "RefreshToken must not be null.");
		if (refreshToken.isBlank()) {
			throw new IllegalArgumentException("RefreshToken must not be blank.");
		}
	}
}
