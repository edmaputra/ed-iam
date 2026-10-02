package io.github.edmaputra.iam.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;

/**
 * REST request body for verifying and consuming a magic link token.
 *
 * @param token the secret magic link token
 * @author edmaputra
 * @since 0.6.0
 */
public record MagicLinkVerifyRequest(
		@NotBlank(message = "Token must not be blank.")
		String token) {

	/**
	 * Maps this REST request to the inbound {@link MagicLinkVerifyCommand} with client metadata.
	 *
	 * @param ipAddress client IP address
	 * @param userAgent client User-Agent string
	 * @return new {@link MagicLinkVerifyCommand}
	 */
	public MagicLinkVerifyCommand toCommand(String ipAddress, String userAgent) {
		return new MagicLinkVerifyCommand(token, ipAddress, userAgent);
	}
}
