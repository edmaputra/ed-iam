package io.github.edmaputra.iam.adapter.security.provider;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;

/**
 * Authentication provider for machine-to-machine (M2M) API keys.
 * Validates API keys via {@link ApiKeyValidatorPort}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class ApiKeyAuthProvider implements AuthenticationProvider {

	private final ApiKeyValidatorPort apiKeyValidator;

	@Override
	public boolean supports(AuthCredentialType credentialType) {
		return credentialType == AuthCredentialType.API_KEY;
	}

	@Override
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		if (!(credentials instanceof ApiKeyAuthCredentials apiKeyCreds)) {
			throw new IllegalArgumentException("Expected ApiKeyAuthCredentials but got: " + credentials.getClass().getName());
		}

		return apiKeyValidator.validateApiKey(apiKeyCreds.apiKey())
				.orElseThrow(() -> new AuthenticationException("Invalid or expired API key."));
	}
}
