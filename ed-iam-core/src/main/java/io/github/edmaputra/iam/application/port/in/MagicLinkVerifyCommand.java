package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

/**
 * Command verifying a magic link token and requesting access/refresh tokens.
 *
 * @param token     the secret magic link token
 * @param ipAddress optional client IP address for session tracking
 * @param userAgent optional client User-Agent header
 * @author edmaputra
 * @since 0.7.0
 */
public record MagicLinkVerifyCommand(
		String token,
		String ipAddress,
		String userAgent) {

	public MagicLinkVerifyCommand {
		Objects.requireNonNull(token, "Token must not be null.");
		if (token.isBlank()) {
			throw new IllegalArgumentException("Token must not be blank.");
		}
	}

	/**
	 * Creates a verification command with token only.
	 *
	 * @param token magic link token
	 * @return new {@link MagicLinkVerifyCommand}
	 */
	public static MagicLinkVerifyCommand of(String token) {
		return new MagicLinkVerifyCommand(token, null, null);
	}

	/**
	 * Creates a verification command with token and client metadata.
	 *
	 * @param token     magic link token
	 * @param ipAddress client IP address
	 * @param userAgent client User-Agent
	 * @return new {@link MagicLinkVerifyCommand}
	 */
	public static MagicLinkVerifyCommand of(String token, String ipAddress, String userAgent) {
		return new MagicLinkVerifyCommand(token, ipAddress, userAgent);
	}
}
