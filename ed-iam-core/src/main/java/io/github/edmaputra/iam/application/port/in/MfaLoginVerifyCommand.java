package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

/**
 * Command encapsulating parameters required to complete second-factor verification during login.
 *
 * @param mfaToken  the temporary challenge token received from initial password authentication
 * @param code      the 6-digit TOTP code or backup recovery code entered by the user
 * @param ipAddress optional client IP address for session tracking
 * @param userAgent optional client User-Agent header for session identification
 * @author edmaputra
 * @since 0.6.0
 */
public record MfaLoginVerifyCommand(
		String mfaToken,
		String code,
		String ipAddress,
		String userAgent) {

	public MfaLoginVerifyCommand {
		Objects.requireNonNull(mfaToken, "MfaToken must not be null.");
		Objects.requireNonNull(code, "Verification code must not be null.");
		if (mfaToken.isBlank()) {
			throw new IllegalArgumentException("MfaToken must not be blank.");
		}
		if (code.isBlank()) {
			throw new IllegalArgumentException("Verification code must not be blank.");
		}
	}

	/**
	 * Backward-compatible factory method without client metadata.
	 *
	 * @param mfaToken the temporary MFA challenge token
	 * @param code     the verification code
	 * @return new {@link MfaLoginVerifyCommand}
	 */
	public static MfaLoginVerifyCommand of(String mfaToken, String code) {
		return new MfaLoginVerifyCommand(mfaToken, code, null, null);
	}

	/**
	 * Factory method with client metadata.
	 *
	 * @param mfaToken  the temporary MFA challenge token
	 * @param code      the verification code
	 * @param ipAddress client IP address
	 * @param userAgent client User-Agent
	 * @return new {@link MfaLoginVerifyCommand}
	 */
	public static MfaLoginVerifyCommand of(String mfaToken, String code, String ipAddress, String userAgent) {
		return new MfaLoginVerifyCommand(mfaToken, code, ipAddress, userAgent);
	}
}
