package io.github.edmaputra.iam.domain.auth;

import java.util.Objects;

/**
 * Encapsulates machine-to-machine (M2M) API key authentication credentials.
 *
 * @param apiKey the raw API key token string
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record ApiKeyAuthCredentials(String apiKey) implements AuthCredentials {

	public ApiKeyAuthCredentials {
		Objects.requireNonNull(apiKey, "ApiKey must not be null.");
		if (apiKey.isBlank()) {
			throw new IllegalArgumentException("ApiKey must not be blank.");
		}
	}

	@Override
	public AuthCredentialType credentialType() {
		return AuthCredentialType.API_KEY;
	}
}
