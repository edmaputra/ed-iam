package io.github.edmaputra.iam.application.model;

import java.util.List;
import java.util.Objects;

/**
 * Response payload returned during MFA enrollment initiation, containing the TOTP secret,
 * standard OTP Auth QR code URI, and plaintext single-use backup recovery codes.
 *
 * @param secret      the Base32-encoded TOTP secret key
 * @param qrCodeUri   the standardized {@code otpauth://} key URI for QR code generation
 * @param backupCodes list of plaintext backup recovery codes (displayed only once)
 * @author edmaputra
 * @since 0.5.0
 */
public record MfaSetupResponse(
		String secret,
		String qrCodeUri,
		List<String> backupCodes) {

	public MfaSetupResponse {
		Objects.requireNonNull(secret, "Secret must not be null.");
		Objects.requireNonNull(qrCodeUri, "QRCodeUri must not be null.");
		backupCodes = backupCodes == null ? List.of() : List.copyOf(backupCodes);
	}
}
