package io.github.edmaputra.iam.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;

/**
 * REST request body for exchanging a refresh token.
 *
 * @param refreshToken the signed JWT refresh token string
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record RefreshTokenRequest(
		@NotBlank(message = "Refresh token must not be blank.")
		String refreshToken) {

	/**
	 * Maps this REST request to the inbound {@link RefreshTokenCommand}.
	 *
	 * @return new {@link RefreshTokenCommand}
	 */
	public RefreshTokenCommand toCommand() {
		return new RefreshTokenCommand(refreshToken);
	}
}
