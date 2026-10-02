package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Inbound port defining multi-factor authentication (MFA / TOTP) operations,
 * including enrollment setup, activation, disabling, status checks, and login challenge verification.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public interface ManageMfaUseCase {

	/**
	 * Initiates MFA enrollment by generating a new Base32 secret, standard OTP Auth QR code URI,
	 * and single-use plaintext backup recovery codes.
	 *
	 * @param userId the user ID initiating enrollment
	 * @param issuer the application or system issuer name (e.g. "ed-iam")
	 * @return setup response with secret, QR URI, and backup recovery codes
	 */
	MfaSetupResponse initiateSetup(UserId userId, String issuer);

	/**
	 * Activates MFA after verifying the user's first 6-digit TOTP code.
	 *
	 * @param userId the user ID activating MFA
	 * @param code   the 6-digit TOTP code from their authenticator app
	 */
	void activate(UserId userId, String code);

	/**
	 * Disables MFA for the specified user account after confirming their TOTP code or password.
	 *
	 * @param userId         the user ID
	 * @param codeOrPassword either a valid TOTP code or the user's raw password
	 */
	void disable(UserId userId, String codeOrPassword);

	/**
	 * Retrieves the current MFA enrollment and active status for the user.
	 *
	 * @param userId the user ID
	 * @return MFA status response
	 */
	MfaStatusResponse getStatus(UserId userId);

	/**
	 * Verifies an MFA login challenge token and verification code, issuing fully-authenticated JWT tokens.
	 *
	 * @param command the verification command containing the challenge token and code
	 * @return fully authenticated token response
	 */
	TokenResponse verifyLogin(MfaLoginVerifyCommand command);
}
