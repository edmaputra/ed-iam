package io.github.edmaputra.iam.adapter.rest;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

	private AuthenticateUserUseCase authenticateUserUseCase;
	private CurrentActorProvider currentActorProvider;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		authenticateUserUseCase = Mockito.mock(AuthenticateUserUseCase.class);
		currentActorProvider = Mockito.mock(CurrentActorProvider.class);

		AuthController controller = new AuthController(authenticateUserUseCase, currentActorProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new IamExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("POST /api/v1/auth/login should return 200 and tokens on success")
	void shouldLoginSuccessfully() throws Exception {
		UUID userId = UUID.randomUUID();
		UserProfileResponse userProfile = new UserProfileResponse(
				userId,
				"doctor@hospital.org",
				"Dr. Doctor",
				null,
				false,
				true,
				Set.of(),
				Set.of("PHYSICIAN"),
				Set.of("PATIENT_READ"),
				Set.of(),
				Set.of());

		TokenResponse tokenResponse = TokenResponse.of("access-token-123", "refresh-token-456", 3600L, userProfile);

		when(authenticateUserUseCase.login(any(LoginCommand.class))).thenReturn(tokenResponse);

		String requestJson = """
				{
				    "email": "doctor@hospital.org",
				    "password": "SecretPassword123"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token-123"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.user.email").value("doctor@hospital.org"));
	}

	@Test
	@DisplayName("POST /api/v1/auth/login should return 401 on bad credentials")
	void shouldReturn401OnBadCredentials() throws Exception {
		when(authenticateUserUseCase.login(any(LoginCommand.class)))
				.thenThrow(new AuthenticationException("Invalid credentials."));

		String requestJson = """
				{
				    "email": "doctor@hospital.org",
				    "password": "WrongPassword"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid credentials."));
	}

	@Test
	@DisplayName("POST /api/v1/auth/refresh should return 200 and new tokens on success")
	void shouldRefreshSuccessfully() throws Exception {
		UUID userId = UUID.randomUUID();
		UserProfileResponse userProfile = new UserProfileResponse(
				userId,
				"doctor@hospital.org",
				"Dr. Doctor",
				null,
				false,
				true,
				Set.of(),
				Set.of("PHYSICIAN"),
				Set.of(),
				Set.of(),
				Set.of());

		TokenResponse tokenResponse = TokenResponse.of("new-access-token", "new-refresh-token", 3600L, userProfile);

		when(authenticateUserUseCase.refreshToken(any(RefreshTokenCommand.class))).thenReturn(tokenResponse);

		String requestJson = """
				{
				    "refreshToken": "existing-refresh-token"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("new-access-token"))
				.andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
	}

	@Test
	@DisplayName("GET /api/v1/auth/me should return 200 and profile for current actor")
	void shouldReturnCurrentActorProfile() throws Exception {
		UUID userId = UUID.randomUUID();
		CurrentActor actor = new SecurityContextCurrentActor(
				userId,
				"doctor@hospital.org",
				null,
				false,
				true,
				Set.of(),
				Set.of("PHYSICIAN"),
				Set.of("PATIENT_READ"),
				Set.of(),
				Set.of());

		UserProfileResponse userProfile = new UserProfileResponse(
				userId,
				"doctor@hospital.org",
				"Dr. Doctor",
				null,
				false,
				true,
				Set.of(),
				Set.of("PHYSICIAN"),
				Set.of("PATIENT_READ"),
				Set.of(),
				Set.of());

		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);
		when(authenticateUserUseCase.getMe(actor)).thenReturn(userProfile);

		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("doctor@hospital.org"))
				.andExpect(jsonPath("$.fullName").value("Dr. Doctor"))
				.andExpect(jsonPath("$.roles[0]").value("PHYSICIAN"));
	}
}
