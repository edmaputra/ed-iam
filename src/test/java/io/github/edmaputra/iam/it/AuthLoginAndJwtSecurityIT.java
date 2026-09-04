package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test verifying authentication, password verification,
 * JWT generation, {@link io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter}
 * security context propagation, and refresh token exchange.
 *
 * @author edmaputra
 */
class AuthLoginAndJwtSecurityIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should successfully login with local BCrypt password and retrieve tokens")
	void shouldLoginSuccessfullyWithLocalPassword() throws Exception {
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

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.fullName").value("Dr. Gregory House"))
				.andExpect(jsonPath("$.user.permissions[0]").isNotEmpty())
				.andReturn();

		String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
		String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

		assertThat(accessToken).isNotBlank();
		assertThat(refreshToken).isNotBlank();

		// 3. Test protected endpoint /api/v1/auth/me with Bearer token
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.permissions").isArray());

		// 4. Test token refresh
		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(refreshToken);

		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String newAccessToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");
		assertThat(newAccessToken).isNotBlank();

		// Verify the new access token can access /api/v1/auth/me
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	@DisplayName("Should return 401 Unauthorized on invalid password")
	void shouldReturn401OnInvalidPassword() throws Exception {
		String email = "nurse-" + UUID.randomUUID() + "@hospital.org";
		User user = User.create(email, passwordEncoder.encode("correctPassword123"), "Nurse Jackie", false);
		userRepository.save(user);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "wrongPassword"
				}
				""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Should return 401 Unauthorized when user does not exist")
	void shouldReturn401WhenUserNotFound() throws Exception {
		String loginJson = """
				{
				    "email": "nonexistent@hospital.org",
				    "password": "somePassword"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Should reject /api/v1/auth/me without or with invalid token")
	void shouldRejectMeEndpointWithoutValidToken() throws Exception {
		// Missing token: actor context absent triggers IllegalStateException mapped to 400 Bad Request
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isBadRequest());

		// Tampered / Invalid token: JwtAuthenticationFilter rejects invalid token with 401 Unauthorized
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer invalid.jwt.token"))
				.andExpect(status().isUnauthorized());
	}
}
