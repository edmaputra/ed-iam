package io.github.edmaputra.iam.adapter.security.provider;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.OidcAuthCredentials;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcAuthProviderTest {

	private FederatedIdentityService federatedIdentityService;
	private OidcAuthProvider provider;

	@BeforeEach
	void setUp() {
		federatedIdentityService = Mockito.mock(FederatedIdentityService.class);
		provider = new OidcAuthProvider(federatedIdentityService);
	}

	@Test
	@DisplayName("Should support OIDC_TOKEN credential type")
	void shouldSupportOidcType() {
		assertThat(provider.supports(AuthCredentialType.OIDC_TOKEN)).isTrue();
		assertThat(provider.supports(AuthCredentialType.PASSWORD)).isFalse();
	}

	@Test
	@DisplayName("Should delegate authentication to FederatedIdentityService")
	void shouldDelegateToFederatedIdentityService() {
		TenantId tenantId = TenantId.generate();
		OidcAuthCredentials credentials = new OidcAuthCredentials(
				"jwt-token",
				"alice@hospital.org",
				"sub-001",
				"Alice",
				"https://auth0.com",
				List.of("radiology_staff"),
				tenantId);

		AuthenticatedIdentity identity = new AuthenticatedIdentity(
				UserId.generate(),
				"alice@hospital.org",
				"Alice",
				false,
				ProviderType.OIDC_GENERIC);

		when(federatedIdentityService.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-001",
				"alice@hospital.org",
				"Alice",
				"https://auth0.com",
				List.of("radiology_staff"),
				tenantId)).thenReturn(identity);

		AuthenticatedIdentity result = provider.authenticate(credentials);

		assertThat(result).isEqualTo(identity);
		verify(federatedIdentityService).linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-001",
				"alice@hospital.org",
				"Alice",
				"https://auth0.com",
				List.of("radiology_staff"),
				tenantId);
	}
}
