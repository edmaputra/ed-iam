package io.github.edmaputra.iam.adapter.security.provider;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ApiKeyAuthProviderTest {

	private ApiKeyValidatorPort apiKeyValidator;
	private ApiKeyAuthProvider provider;

	@BeforeEach
	void setUp() {
		apiKeyValidator = Mockito.mock(ApiKeyValidatorPort.class);
		provider = new ApiKeyAuthProvider(apiKeyValidator);
	}

	@Test
	@DisplayName("Should support API_KEY credential type")
	void shouldSupportApiKeyType() {
		assertThat(provider.supports(AuthCredentialType.API_KEY)).isTrue();
		assertThat(provider.supports(AuthCredentialType.PASSWORD)).isFalse();
	}

	@Test
	@DisplayName("Should authenticate valid API key")
	void shouldAuthenticateValidApiKey() {
		AuthenticatedIdentity identity = new AuthenticatedIdentity(
				UserId.generate(),
				"device@uwati.org",
				"Lab Analyzer #1",
				false,
				ProviderType.LOCAL);

		when(apiKeyValidator.validateApiKey("api-key-12345")).thenReturn(Optional.of(identity));

		AuthenticatedIdentity result = provider.authenticate(new ApiKeyAuthCredentials("api-key-12345"));

		assertThat(result).isEqualTo(identity);
	}

	@Test
	@DisplayName("Should reject invalid API key")
	void shouldRejectInvalidApiKey() {
		when(apiKeyValidator.validateApiKey("bad-key")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> provider.authenticate(new ApiKeyAuthCredentials("bad-key")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid or expired API key");
	}
}
