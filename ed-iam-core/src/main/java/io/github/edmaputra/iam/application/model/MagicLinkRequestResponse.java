package io.github.edmaputra.iam.application.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Response model returning the status of a requested magic link dispatch.
 *
 * @param message   human-readable status message
 * @param token     the issued magic link token (available for testing/logging)
 * @param expiresAt the timestamp when the magic link token will expire
 * @author edmaputra
 * @since 0.5.0
 */
public record MagicLinkRequestResponse(
		String message,
		String token,
		Instant expiresAt) {

	public MagicLinkRequestResponse {
		Objects.requireNonNull(message, "Message must not be null.");
		Objects.requireNonNull(token, "Token must not be null.");
		Objects.requireNonNull(expiresAt, "ExpiresAt must not be null.");
	}

	/**
	 * Creates a new {@link MagicLinkRequestResponse}.
	 *
	 * @param message   status message
	 * @param token     the issued token string
	 * @param expiresAt expiration timestamp
	 * @return new {@link MagicLinkRequestResponse}
	 */
	public static MagicLinkRequestResponse of(String message, String token, Instant expiresAt) {
		return new MagicLinkRequestResponse(message, token, expiresAt);
	}
}
