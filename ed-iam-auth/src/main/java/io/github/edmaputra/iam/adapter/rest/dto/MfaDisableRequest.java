package io.github.edmaputra.iam.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound HTTP request payload for disabling MFA with a valid TOTP code or account password.
 *
 * @param codeOrPassword either a valid TOTP code or current user account password
 * @author edmaputra
 * @since 0.6.0
 */
public record MfaDisableRequest(
		@NotBlank(message = "Verification code or password must not be blank.")
		String codeOrPassword) {
}
