package io.github.edmaputra.iam.application.port.out;

import java.util.List;
import java.util.Objects;

import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;

/**
 * Composite router that dispatches incoming {@link AuthCredentials} to the appropriate registered {@link AuthenticationProvider}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AuthenticationProviderRouter {

	private final List<AuthenticationProvider> providers;

	/**
	 * Constructs the router with a list of active authentication providers.
	 *
	 * @param providers list of providers (must not be empty)
	 */
	public AuthenticationProviderRouter(List<AuthenticationProvider> providers) {
		this.providers = List.copyOf(Objects.requireNonNull(providers, "Providers list must not be null."));
		if (this.providers.isEmpty()) {
			throw new IllegalStateException("At least one AuthenticationProvider must be configured.");
		}
	}

	/**
	 * Routes credentials to the matching provider and returns the authenticated identity.
	 *
	 * @param credentials the credentials to authenticate
	 * @return the authenticated identity
	 * @throws AuthenticationException if no matching provider is registered or authentication fails
	 */
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		Objects.requireNonNull(credentials, "Credentials must not be null.");
		return providers.stream()
				.filter(provider -> provider.supports(credentials.credentialType()))
				.findFirst()
				.orElseThrow(() -> new AuthenticationException(
						"No authentication provider registered for credential type: " + credentials.credentialType()))
				.authenticate(credentials);
	}
}
