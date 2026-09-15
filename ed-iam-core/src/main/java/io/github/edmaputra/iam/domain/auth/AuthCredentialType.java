package io.github.edmaputra.iam.domain.auth;

/**
 * Enumeration of supported inbound authentication credential types in the Uwati IAM subsystem.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum AuthCredentialType {
	/** Standard local username/email and password credentials. */
	PASSWORD,
	/** OpenID Connect ID token or authorization code. */
	OIDC_TOKEN,
	/** SAML 2.0 assertion token. */
	SAML_ASSERTION,
	/** Machine-to-machine API key credential. */
	API_KEY
}
