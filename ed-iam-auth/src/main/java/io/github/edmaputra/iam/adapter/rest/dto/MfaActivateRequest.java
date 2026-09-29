package io.github.edmaputra.iam.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound HTTP request payload for activating MFA with the first generated TOTP verification code.
 *
 * @param code the 6-digit TOTP verification code
 * @author edmaputra
 * @since 0.5.0
 */
public record MfaActivateRequest(
		@NotBlank(message = "Verification code must not be blank.")
		String code) {
}
