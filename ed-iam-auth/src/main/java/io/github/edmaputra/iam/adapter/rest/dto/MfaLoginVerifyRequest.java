package io.github.edmaputra.iam.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.application.port.in.MfaLoginVerifyCommand;

/**
 * Inbound HTTP request payload for verifying an MFA login challenge token.
 *
 * @param mfaToken the temporary MFA challenge token string
 * @param code     the 6-digit TOTP code or single-use backup recovery code
 * @author edmaputra
 * @since 0.5.0
 */
public record MfaLoginVerifyRequest(
		@NotBlank(message = "MFA token must not be blank.")
		String mfaToken,

		@NotBlank(message = "Verification code must not be blank.")
		String code) {

	/**
	 * Converts this request payload to an {@link MfaLoginVerifyCommand}.
	 *
	 * @param ipAddress optional client IP address
	 * @param userAgent optional client User-Agent
	 * @return domain command instance
	 */
	public MfaLoginVerifyCommand toCommand(String ipAddress, String userAgent) {
		return MfaLoginVerifyCommand.of(mfaToken, code, ipAddress, userAgent);
	}
}
