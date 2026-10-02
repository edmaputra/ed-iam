package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;

/**
 * Inbound port defining operations for passwordless Magic Link authentication,
 * including token issuance, notification dispatch, and single-use link consumption.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public interface ManageMagicLinkUseCase {

	/**
	 * Issues a one-time high-entropy magic link token and dispatches the verification link.
	 *
	 * @param command the request command containing destination email and optional tenant/redirect parameters
	 * @return response containing status, token, and expiration timestamp
	 */
	MagicLinkRequestResponse requestMagicLink(MagicLinkRequestCommand command);

	/**
	 * Verifies and atomically consumes a magic link token, returning an authenticated {@link TokenResponse}
	 * or an MFA challenge if the user has multi-factor authentication active.
	 *
	 * @param command the verification command containing the token and client metadata
	 * @return authenticated token response or MFA challenge
	 */
	TokenResponse verifyMagicLink(MagicLinkVerifyCommand command);
}
