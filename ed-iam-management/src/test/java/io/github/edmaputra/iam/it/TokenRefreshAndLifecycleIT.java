package io.github.edmaputra.iam.it;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test covering JWT refresh token rotation, cross-token injection defenses,
 * invalid token handling, and mid-session account revocation using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class TokenRefreshAndLifecycleIT extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoderPort passwordEncoder;

	@Test
	@DisplayName("Should successfully exchange valid refresh token for rotated tokens and authenticate")
	void shouldExchangeRefreshTokenSuccessfully() throws Exception {
		String email = "refresh-user-" + UUID.randomUUID() + "@clinic.org";
		String password = "Password123!";
		User user = User.create(email, passwordEncoder.encode(password), "Refresh User", false);
		userRepository.save(user);

		// 1. Initial Login
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
		String loginResponseStr = new String(loginBytes, StandardCharsets.UTF_8);
		String initialAccessToken = JsonPath.read(loginResponseStr, "$.accessToken");
		String initialRefreshToken = JsonPath.read(loginResponseStr, "$.refreshToken");

		// Wait 1 second so that second-resolution JWT iat advances for rotation assertion
		Thread.sleep(1000);

		// 2. Call /refresh
		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(initialRefreshToken);

		byte[] refreshBytes = webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(refreshJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(refreshBytes).isNotNull();
		String refreshResponseStr = new String(refreshBytes, StandardCharsets.UTF_8);
		String newAccessToken = JsonPath.read(refreshResponseStr, "$.accessToken");

		// Tokens should be distinct (rotated / new issuance)
		assertThat(newAccessToken).isNotEqualTo(initialAccessToken);

		// 3. New Access Token works on /me
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + newAccessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(email);
	}

	@Test
	@DisplayName("Should reject cross-token injection attacks (access token to /refresh and refresh token to /me)")
	void shouldRejectCrossTokenUsage() {
		String email = "cross-token-" + UUID.randomUUID() + "@clinic.org";
		String password = "Password123!";
		User user = User.create(email, passwordEncoder.encode(password), "Cross User", false);
		userRepository.save(user);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
		String loginResponseStr = new String(loginBytes, StandardCharsets.UTF_8);
		String accessToken = JsonPath.read(loginResponseStr, "$.accessToken");
		String refreshToken = JsonPath.read(loginResponseStr, "$.refreshToken");

		// Attack A: Pass ACCESS token to /refresh endpoint
		String badRefreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(accessToken);

		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(badRefreshJson)
				.exchange()
				.expectStatus().isUnauthorized();

		// Attack B: Pass REFRESH token as Bearer token to /me endpoint
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + refreshToken)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should reject /refresh when user account is suspended or deactivated after token issuance")
	void shouldRejectRefreshForRevokedAccount() {
		String email = "revoked-user-" + UUID.randomUUID() + "@clinic.org";
		String password = "Password123!";
		User user = User.create(email, passwordEncoder.encode(password), "Revoked User", false);
		user = userRepository.save(user);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
		String refreshToken = JsonPath.read(new String(loginBytes, StandardCharsets.UTF_8), "$.refreshToken");

		// 1. Suspend the user
		user.suspend();
		userRepository.save(user);

		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(refreshToken);

		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(refreshJson)
				.exchange()
				.expectStatus().isUnauthorized();

		// 2. Deactivate the user
		user.deactivate();
		userRepository.save(user);

		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(refreshJson)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should reject /refresh with malformed, tampered, or missing refresh token")
	void shouldRejectInvalidRefreshTokens() {
		// Missing / empty token triggers IllegalArgumentException -> 400 Bad Request
		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"refreshToken\":\"\"}")
				.exchange()
				.expectStatus().isBadRequest();

		// Tampered token fails JWT signature verification -> 401 Unauthorized
		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.invalid.signature\"}")
				.exchange()
				.expectStatus().isUnauthorized();
	}
}
