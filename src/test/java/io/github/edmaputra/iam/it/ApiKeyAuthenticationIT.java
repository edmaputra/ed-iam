package io.github.edmaputra.iam.it;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.github.edmaputra.iam.adapter.security.provider.ApiKeyAuthProvider;
import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test covering machine-to-machine (M2M) API Key authentication SPI provider.
 *
 * @author edmaputra
 */
@Import(ApiKeyAuthenticationIT.ApiKeyTestConfig.class)
class ApiKeyAuthenticationIT extends AbstractIntegrationTest {

	private static final String VALID_KEY = "m2m-valid-secret-key-xyz-789";

	@TestConfiguration
	static class ApiKeyTestConfig {

		@Bean
		ApiKeyValidatorPort apiKeyValidatorPort() {
			return apiKey -> {
				if (VALID_KEY.equals(apiKey)) {
					return Optional.of(new AuthenticatedIdentity(
							UserId.generate(),
							"m2m-service@enterprise.org",
							"M2M Service Client",
							false,
							ProviderType.API_KEY));
				}
				return Optional.empty();
			};
		}

		@Bean
		ApiKeyAuthProvider apiKeyAuthProvider(ApiKeyValidatorPort apiKeyValidatorPort) {
			return new ApiKeyAuthProvider(apiKeyValidatorPort);
		}
	}

	@Autowired
	private ApiKeyAuthProvider apiKeyAuthProvider;

	@Test
	@DisplayName("Should verify ApiKeyAuthProvider credential support")
	void shouldVerifySupportedCredentials() {
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.API_KEY)).isTrue();
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.PASSWORD)).isFalse();
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.OIDC_TOKEN)).isFalse();
	}

	@Test
	@DisplayName("Should successfully authenticate valid API key and return identity")
	void shouldAuthenticateValidApiKey() {
		AuthenticatedIdentity identity = apiKeyAuthProvider.authenticate(new ApiKeyAuthCredentials(VALID_KEY));

		assertThat(identity).isNotNull();
		assertThat(identity.email()).isEqualTo("m2m-service@enterprise.org");
		assertThat(identity.fullName()).isEqualTo("M2M Service Client");
		assertThat(identity.providerType()).isEqualTo(ProviderType.API_KEY);
		assertThat(identity.platformSuperAdmin()).isFalse();
	}

	@Test
	@DisplayName("Should reject invalid or expired API key with AuthenticationException")
	void shouldRejectInvalidApiKey() {
		assertThatThrownBy(() -> apiKeyAuthProvider.authenticate(new ApiKeyAuthCredentials("invalid-api-key")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid or expired API key.");
	}

	@Test
	@DisplayName("Should reject non-API key credentials with IllegalArgumentException")
	void shouldRejectNonApiKeyCredentials() {
		assertThatThrownBy(() -> apiKeyAuthProvider.authenticate(new PasswordAuthCredentials("test@test.org", "pass")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected ApiKeyAuthCredentials");
	}
}
