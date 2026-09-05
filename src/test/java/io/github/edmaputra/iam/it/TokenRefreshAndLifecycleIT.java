package io.github.edmaputra.iam.it;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test covering JWT refresh token rotation, cross-token injection defenses,
 * invalid token handling, and mid-session account revocation.
 *
 * @author edmaputra
 */
class TokenRefreshAndLifecycleIT extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

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

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();

		String initialAccessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
		String initialRefreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

		// Wait 1 second so that second-resolution JWT iat advances for rotation assertion
		Thread.sleep(1000);

		// 2. Call /refresh
		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(initialRefreshToken);

		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();

		String newAccessToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");
		String newRefreshToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");

		// Tokens should be distinct (rotated / new issuance)
		assertThat(newAccessToken).isNotEqualTo(initialAccessToken);

		// 3. New Access Token works on /me
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	@DisplayName("Should reject cross-token injection attacks (access token to /refresh and refresh token to /me)")
	void shouldRejectCrossTokenUsage() throws Exception {
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

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isOk())
				.andReturn();

		String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
		String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

		// Attack A: Pass ACCESS token to /refresh endpoint
		String badRefreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(accessToken);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(badRefreshJson))
				.andExpect(status().isUnauthorized());

		// Attack B: Pass REFRESH token as Bearer token to /me endpoint
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + refreshToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Should reject /refresh when user account is suspended or deactivated after token issuance")
	void shouldRejectRefreshForRevokedAccount() throws Exception {
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

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isOk())
				.andReturn();

		String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

		// 1. Suspend the user
		user.suspend();
		userRepository.save(user);

		String refreshJson = """
				{
				    "refreshToken": "%s"
				}
				""".formatted(refreshToken);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson))
				.andExpect(status().isUnauthorized());

		// 2. Deactivate the user
		user.deactivate();
		userRepository.save(user);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshJson))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Should reject /refresh with malformed, tampered, or missing refresh token")
	void shouldRejectInvalidRefreshTokens() throws Exception {
		// Missing / empty token triggers IllegalArgumentException -> 400 Bad Request
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"\"}"))
				.andExpect(status().isBadRequest());

		// Tampered token fails JWT signature verification -> 401 Unauthorized
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.invalid.signature\"}"))
				.andExpect(status().isUnauthorized());
	}
}
