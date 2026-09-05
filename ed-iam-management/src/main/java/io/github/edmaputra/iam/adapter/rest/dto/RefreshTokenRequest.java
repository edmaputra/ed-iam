package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.Objects;

import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;

/**
 * REST request body for exchanging a refresh token.
 *
 * @param refreshToken the signed JWT refresh token string
 *
 * @author edmaputra
 * @since 1.0.0
 */
public record RefreshTokenRequest(String refreshToken) {

	public RefreshTokenRequest {
		Objects.requireNonNull(refreshToken, "RefreshToken must not be null.");
	}

	/**
	 * Maps this REST request to the inbound {@link RefreshTokenCommand}.
	 *
	 * @return new {@link RefreshTokenCommand}
	 */
	public RefreshTokenCommand toCommand() {
		return new RefreshTokenCommand(refreshToken);
	}
}
