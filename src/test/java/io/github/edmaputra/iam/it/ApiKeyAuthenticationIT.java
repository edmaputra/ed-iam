package io.github.edmaputra.iam.it;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import io.github.edmaputra.iam.adapter.security.provider.ApiKeyAuthProvider;
import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test covering machine-to-machine (M2M) API Key authentication by hitting
 * the REST endpoint {@code /api/test/api-key/authenticate} using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
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
	}

	@Autowired
	private ApiKeyAuthProvider apiKeyAuthProvider;

	@Test
	@DisplayName("Should successfully authenticate valid API key via X-API-Key header hitting REST endpoint")
	void shouldAuthenticateValidApiKeyViaHeader() {
		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.header("X-API-Key", VALID_KEY)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.email").isEqualTo("m2m-service@enterprise.org")
				.jsonPath("$.fullName").isEqualTo("M2M Service Client")
				.jsonPath("$.providerType").isEqualTo("API_KEY")
				.jsonPath("$.platformSuperAdmin").isEqualTo(false)
				.jsonPath("$.userId").isNotEmpty();
	}

	@Test
	@DisplayName("Should successfully authenticate valid API key via request body hitting REST endpoint")
	void shouldAuthenticateValidApiKeyViaRequestBody() {
		String body = "{\"apiKey\":\"" + VALID_KEY + "\"}";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.email").isEqualTo("m2m-service@enterprise.org")
				.jsonPath("$.fullName").isEqualTo("M2M Service Client")
				.jsonPath("$.providerType").isEqualTo("API_KEY")
				.jsonPath("$.platformSuperAdmin").isEqualTo(false);
	}

	@Test
	@DisplayName("Should reject invalid or expired API key with 401 Unauthorized hitting REST endpoint")
	void shouldRejectInvalidApiKeyHittingEndpoint() {
		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.header("X-API-Key", "invalid-api-key")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.detail").isEqualTo("Invalid or expired API key.");
	}

	@Test
	@DisplayName("Should reject missing or blank API key with 400 Bad Request hitting REST endpoint")
	void shouldRejectMissingApiKeyHittingEndpoint() {
		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").isEqualTo("API key must be provided in X-API-Key header or request body.");
	}

	@Test
	@DisplayName("Should verify ApiKeyAuthProvider credential support")
	void shouldVerifySupportedCredentials() {
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.API_KEY)).isTrue();
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.PASSWORD)).isFalse();
		assertThat(apiKeyAuthProvider.supports(AuthCredentialType.OIDC_TOKEN)).isFalse();
		assertThat(apiKeyAuthProvider.supports(null)).isFalse();
	}

	@Test
	@DisplayName("Should reject non-API key credentials with IllegalArgumentException")
	void shouldRejectNonApiKeyCredentials() {
		PasswordAuthCredentials credentials = new PasswordAuthCredentials("test@test.org", "pass");
		assertThatThrownBy(() -> apiKeyAuthProvider.authenticate(credentials))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Expected ApiKeyAuthCredentials");
	}
}
