package io.github.edmaputra.iam.adapter.security.provider;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.OidcAuthCredentials;
import io.github.edmaputra.iam.domain.model.ProviderType;

/**
 * Authentication provider for OpenID Connect (OIDC) federated credentials.
 * Delegates to {@link FederatedIdentityService} for JIT provisioning and group claim synchronization.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class OidcAuthProvider implements AuthenticationProvider {

	private final FederatedIdentityService federatedIdentityService;

	@Override
	public boolean supports(AuthCredentialType credentialType) {
		return credentialType == AuthCredentialType.OIDC_TOKEN;
	}

	@Override
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		if (!(credentials instanceof OidcAuthCredentials oidcCreds)) {
			throw new IllegalArgumentException("Expected OidcAuthCredentials but got: " + credentials.getClass().getName());
		}

		return federatedIdentityService.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				oidcCreds.subject(),
				oidcCreds.email(),
				oidcCreds.fullName(),
				oidcCreds.optionalIssuerUrl().orElse(null),
				oidcCreds.externalGroups(),
				oidcCreds.optionalTenantId().orElse(null));
	}
}
