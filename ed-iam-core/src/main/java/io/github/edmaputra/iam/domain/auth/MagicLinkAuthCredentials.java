package io.github.edmaputra.iam.domain.auth;

import java.util.Objects;

/**
 * Encapsulates a one-time magic link token credential for passwordless authentication.
 *
 * @param token the cryptographically secure magic link token
 * @author edmaputra
 * @since 0.6.0
 */
public record MagicLinkAuthCredentials(String token) implements AuthCredentials {

	public MagicLinkAuthCredentials {
		Objects.requireNonNull(token, "Token must not be null.");
		if (token.isBlank()) {
			throw new IllegalArgumentException("Token must not be blank.");
		}
	}

	@Override
	public AuthCredentialType credentialType() {
		return AuthCredentialType.MAGIC_LINK;
	}
}
