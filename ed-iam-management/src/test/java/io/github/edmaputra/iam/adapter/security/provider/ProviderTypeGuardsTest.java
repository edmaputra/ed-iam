package io.github.edmaputra.iam.adapter.security.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying provider credential type guards.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class ProviderTypeGuardsTest {

	@Test
	@DisplayName("Should enforce provider credential type checks in LocalPasswordAuthProvider and OidcAuthProvider")
	void shouldVerifyProviderTypeGuards() {
		UserRepository mockUserRepo = Mockito.mock(UserRepository.class);
		PasswordEncoderPort mockEncoder = Mockito.mock(PasswordEncoderPort.class);
		FederatedIdentityService mockFedService = Mockito.mock(FederatedIdentityService.class);

		LocalPasswordAuthProvider localProvider = new LocalPasswordAuthProvider(mockUserRepo, mockEncoder);
		OidcAuthProvider oidcProvider = new OidcAuthProvider(mockFedService);

		assertThat(localProvider.supports(null)).isFalse();
		assertThat(oidcProvider.supports(null)).isFalse();

		assertThatThrownBy(() -> localProvider.authenticate(new ApiKeyAuthCredentials("key")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected PasswordAuthCredentials");

		assertThatThrownBy(() -> oidcProvider.authenticate(new ApiKeyAuthCredentials("key")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected OidcAuthCredentials");
	}
}
