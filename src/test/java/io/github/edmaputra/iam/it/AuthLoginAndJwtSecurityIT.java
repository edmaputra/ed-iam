package io.github.edmaputra.iam.it;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying authentication, password verification,
 * JWT generation, {@link io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter}
 * security context propagation, and refresh token exchange using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class AuthLoginAndJwtSecurityIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should successfully login with local BCrypt password and retrieve tokens")
	void shouldLoginSuccessfullyWithLocalPassword() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		String email = "doctor-" + UUID.randomUUID() + "@hospital.org";
		String rawPassword = "P@ssw0rdSecure123!";

		// 1. Seed user, role, and role assignment
		User user = User.create(email, passwordEncoder.encode(rawPassword), "Dr. Gregory House", false);
		userRepository.save(user);

		Role role = Role.createCustom(tenantId, "CLINICIAN", "Clinician", "Clinical staff", Set.of("PATIENT_READ", "PATIENT_WRITE"));
		roleRepository.save(role);

		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId);
		userRoleAssignmentRepository.save(assignment);

		// 2. Perform login request
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, rawPassword, tenantUuid);

		String expectedLoginJson = """
				{
				    "tokenType": "Bearer",
				    "user": {
				        "email": "%s",
				        "fullName": "Dr. Gregory House"
				    }
				}
				""".formatted(email);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedLoginJson, JsonCompareMode.LENIENT)
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
		String loginResponseStr = new String(loginBytes, StandardCharsets.UTF_8);
		String accessToken = JsonPath.read(loginResponseStr, "$.accessToken");
		String refreshToken = JsonPath.read(loginResponseStr, "$.refreshToken");

		assertThat(accessToken).isNotBlank();
		assertThat(refreshToken).isNotBlank();

		// 3. Test protected endpoint /api/v1/auth/me with Bearer token
		String expectedMeJson = """
				{
				    "email": "%s",
				    "fullName": "Dr. Gregory House"
				}
				""".formatted(email);

		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedMeJson, JsonCompareMode.LENIENT)
				.jsonPath("$.permissions").isArray();

		// 4. Test token refresh
		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(refreshToken);

		byte[] refreshBytes = webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(refreshJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(refreshBytes).isNotNull();
		String newAccessToken = JsonPath.read(new String(refreshBytes, StandardCharsets.UTF_8), "$.accessToken");
		assertThat(newAccessToken).isNotBlank();

		// Verify the new access token can access /api/v1/auth/me
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + newAccessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedMeJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should return 401 Unauthorized on invalid password")
	void shouldReturn401OnInvalidPassword() {
		String email = "nurse-" + UUID.randomUUID() + "@hospital.org";
		User user = User.create(email, passwordEncoder.encode("correctPassword123"), "Nurse Jackie", false);
		userRepository.save(user);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "wrongPassword"
				}
				""".formatted(email);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should return 401 Unauthorized when user does not exist")
	void shouldReturn401WhenUserNotFound() {
		String loginJson = """
				{
				    "email": "nonexistent@hospital.org",
				    "password": "somePassword"
				}
				""";

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should reject /api/v1/auth/me without or with invalid token")
	void shouldRejectMeEndpointWithoutValidToken() {
		// Missing token: actor context absent triggers IllegalStateException mapped to 400 Bad Request
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.exchange()
				.expectStatus().isBadRequest();

		// Tampered / Invalid token: JwtAuthenticationFilter rejects invalid token with 401 Unauthorized
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer invalid.jwt.token")
				.exchange()
				.expectStatus().isUnauthorized();
	}
}
