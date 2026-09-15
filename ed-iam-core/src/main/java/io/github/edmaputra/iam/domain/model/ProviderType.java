package io.github.edmaputra.iam.domain.model;

/**
 * Supported identity and authentication provider types.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum ProviderType {
	LOCAL,
	OIDC_GENERIC,
	OIDC_KEYCLOAK,
	OIDC_AZURE,
	SAML_ADFS,
	API_KEY
}
