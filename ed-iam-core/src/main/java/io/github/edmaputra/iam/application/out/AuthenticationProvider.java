package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;

/**
 * Service Provider Interface (SPI) for pluggable authentication mechanisms.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface AuthenticationProvider {

	/**
	 * Determines whether this provider supports the given credential type.
	 *
	 * @param credentialType the credential type
	 * @return true if supported
	 */
	boolean supports(AuthCredentialType credentialType);

	/**
	 * Authenticates credentials against local storage or an external identity provider.
	 *
	 * @param credentials the authentication credentials
	 * @return the verified {@link AuthenticatedIdentity}
	 * @throws AuthenticationException if verification fails
	 */
	AuthenticatedIdentity authenticate(AuthCredentials credentials);
}
