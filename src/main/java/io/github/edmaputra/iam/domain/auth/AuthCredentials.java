package io.github.edmaputra.iam.domain.auth;

/**
 * Marker interface for strongly-typed authentication credentials accepted by {@link io.github.edmaputra.iam.application.port.out.AuthenticationProvider}s.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface AuthCredentials {

	/**
	 * Returns the type of credential represented by this instance.
	 *
	 * @return the {@link AuthCredentialType}
	 */
	AuthCredentialType credentialType();
}
