package io.github.edmaputra.iam.it;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

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
 * the REST endpoint {@code /api/test/api-key/authenticate} using {@link org.springframework.test.web.reactive.server.WebTestClient}
 * with JSON request bodies and Lenient JSON response assertions.
 *
 * @author edmaputra
 * @since 0.0.1
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
		String expectedResponseJson = """
				{
				    "email": "m2m-service@enterprise.org",
				    "fullName": "M2M Service Client",
				    "providerType": "API_KEY",
				    "platformSuperAdmin": false
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.header("X-API-Key", VALID_KEY)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.json(expectedResponseJson, JsonCompareMode.LENIENT)
				.jsonPath("$.userId").isNotEmpty();
	}

	@Test
	@DisplayName("Should successfully authenticate valid API key via request body hitting REST endpoint")
	void shouldAuthenticateValidApiKeyViaRequestBody() {
		String requestBody = """
				{
				    "apiKey": "%s"
				}
				""".formatted(VALID_KEY);

		String expectedResponseJson = """
				{
				    "email": "m2m-service@enterprise.org",
				    "fullName": "M2M Service Client",
				    "providerType": "API_KEY",
				    "platformSuperAdmin": false
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(requestBody)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.json(expectedResponseJson, JsonCompareMode.LENIENT)
				.jsonPath("$.userId").isNotEmpty();
	}

	@Test
	@DisplayName("Should reject invalid or expired API key via header with 401 Unauthorized")
	void shouldRejectInvalidApiKeyViaHeader() {
		String expectedErrorJson = """
				{
				    "status": 401,
				    "detail": "Invalid or expired API key."
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.header("X-API-Key", "invalid-api-key")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.json(expectedErrorJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should reject invalid or expired API key via request body with 401 Unauthorized")
	void shouldRejectInvalidApiKeyViaRequestBody() {
		String requestBody = """
				{
				    "apiKey": "invalid-api-key"
				}
				""";

		String expectedErrorJson = """
				{
				    "status": 401,
				    "detail": "Invalid or expired API key."
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(requestBody)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.json(expectedErrorJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should reject missing API key with 400 Bad Request hitting REST endpoint")
	void shouldRejectMissingApiKeyHittingEndpoint() {
		String expectedErrorJson = """
				{
				    "status": 400,
				    "detail": "API key must be provided in X-API-Key header or request body."
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.json(expectedErrorJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should reject blank API key in request body with 400 Bad Request hitting REST endpoint")
	void shouldRejectBlankApiKeyInRequestBodyHittingEndpoint() {
		String requestBody = """
				{
				    "apiKey": "   "
				}
				""";

		String expectedErrorJson = """
				{
				    "status": 400,
				    "detail": "API key must be provided in X-API-Key header or request body."
				}
				""";

		webTestClient.post()
				.uri("/api/test/api-key/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(requestBody)
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.json(expectedErrorJson, JsonCompareMode.LENIENT);
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
