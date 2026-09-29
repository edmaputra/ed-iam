package io.github.edmaputra.iam.adapter.rest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.LoginRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaActivateRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaDisableRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaLoginVerifyRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RefreshTokenRequest;
import io.github.edmaputra.iam.adapter.rest.dto.SwitchTenantRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserSessionResponse;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthController}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class AuthControllerTest {

	private AuthenticateUserUseCase authenticateUserUseCase;
	private CurrentActorProvider currentActorProvider;
	private ManageMfaUseCase manageMfaUseCase;
	private AuthController controller;

	@BeforeEach
	void setUp() {
		authenticateUserUseCase = mock(AuthenticateUserUseCase.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		manageMfaUseCase = mock(ManageMfaUseCase.class);
		controller = new AuthController(authenticateUserUseCase, currentActorProvider, manageMfaUseCase);
	}

	@Test
	@DisplayName("Should successfully authenticate user on login")
	void shouldLogin() {
		UserProfileResponse profile = new UserProfileResponse(UUID.randomUUID(), "user@clinic.org", "User", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		TokenResponse expected = new TokenResponse("access-token", "refresh-token", "Bearer", 900L, profile);
		when(authenticateUserUseCase.login(any(LoginCommand.class))).thenReturn(expected);

		LoginRequest request = new LoginRequest("user@clinic.org", "Password123!");
		ResponseEntity<TokenResponse> response = controller.login(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(authenticateUserUseCase).login(any(LoginCommand.class));
	}

	@Test
	@DisplayName("Should exchange refresh token for new token pair")
	void shouldRefresh() {
		UserProfileResponse profile = new UserProfileResponse(UUID.randomUUID(), "user@clinic.org", "User", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		TokenResponse expected = new TokenResponse("new-access", "new-refresh", "Bearer", 900L, profile);
		when(authenticateUserUseCase.refreshToken(any(RefreshTokenCommand.class))).thenReturn(expected);

		RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
		ResponseEntity<TokenResponse> response = controller.refresh(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(authenticateUserUseCase).refreshToken(any(RefreshTokenCommand.class));
	}

	@Test
	@DisplayName("Should retrieve current actor profile on /me")
	void shouldGetMe() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		UserProfileResponse profile = new UserProfileResponse(actorId, "me@clinic.org", "Me", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);
		when(authenticateUserUseCase.getMe(actor)).thenReturn(profile);

		ResponseEntity<UserProfileResponse> response = controller.me();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(profile);
	}

	@Test
	@DisplayName("Should switch tenant for authenticated actor")
	void shouldSwitchTenant() {
		UUID targetTenantId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		UserProfileResponse profile = new UserProfileResponse(actorId, "me@clinic.org", "Me", targetTenantId, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		TokenResponse expected = new TokenResponse("switched-access", "switched-refresh", "Bearer", 900L, profile);

		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);
		when(authenticateUserUseCase.switchTenant(any(SwitchTenantCommand.class))).thenReturn(expected);

		SwitchTenantRequest request = new SwitchTenantRequest(targetTenantId);
		ResponseEntity<TokenResponse> response = controller.switchTenant(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
	}

	@Test
	@DisplayName("Should throw exception when target tenant ID is missing on switch tenant")
	void shouldThrowWhenSwitchTenantMissingTenantId() {
		assertThatThrownBy(() -> controller.switchTenant(null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Target tenant ID must be provided");

		assertThatThrownBy(() -> controller.switchTenant("   ", new SwitchTenantRequest(null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Target tenant ID must be provided");
	}

	@Test
	@DisplayName("Should successfully logout and terminate session")
	void shouldLogout() {
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tokenId()).thenReturn("token-xyz");
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		ResponseEntity<Void> response = controller.logout();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(authenticateUserUseCase).logout("token-xyz");
	}

	@Test
	@DisplayName("Should successfully logout-all sessions")
	void shouldLogoutAll() {
		UUID actorId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(actor.tenantId()).thenReturn(tenantId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		ResponseEntity<Void> response = controller.logoutAll();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(authenticateUserUseCase).logoutAll(
				new io.github.edmaputra.iam.domain.model.UserId(actorId),
				new io.github.edmaputra.iam.domain.tenancy.TenantId(tenantId));
	}

	@Test
	@DisplayName("Should retrieve active sessions for calling actor")
	void shouldGetSessions() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(actor.tenantId()).thenReturn(null);
		when(actor.tokenId()).thenReturn("token-current");
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		UserSession session = UserSession.create(
				new UserId(actorId),
				null,
				"token-current",
				3600,
				"127.0.0.1",
				"Test-Agent");

		when(authenticateUserUseCase.getActiveSessions(new UserId(actorId), null))
				.thenReturn(List.of(session));

		ResponseEntity<List<UserSessionResponse>> response = controller.getSessions();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).hasSize(1);
		assertThat(response.getBody().get(0).current()).isTrue();
		assertThat(response.getBody().get(0).tokenIdentifier()).isEqualTo("token-current");
	}

	@Test
	@DisplayName("Should verify MFA login challenge and return token response")
	void shouldVerifyMfaLogin() {
		UserProfileResponse profile = new UserProfileResponse(UUID.randomUUID(), "user@clinic.org", "User", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		TokenResponse expected = new TokenResponse("access-token", "refresh-token", "Bearer", 900L, profile);
		when(manageMfaUseCase.verifyLogin(any())).thenReturn(expected);

		MfaLoginVerifyRequest request = new MfaLoginVerifyRequest("mfa-token", "123456");

		ResponseEntity<TokenResponse> response = controller.verifyMfa(request, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(manageMfaUseCase).verifyLogin(any());
	}

	@Test
	@DisplayName("Should retrieve MFA status for calling actor")
	void shouldGetMfaStatus() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaStatusResponse expected = new MfaStatusResponse(true, Instant.now());
		when(manageMfaUseCase.getStatus(new UserId(actorId))).thenReturn(expected);

		ResponseEntity<MfaStatusResponse> response = controller.getMfaStatus();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
	}

	@Test
	@DisplayName("Should initiate MFA setup for calling actor")
	void shouldSetupMfa() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaSetupResponse expected = new MfaSetupResponse("SECRET", "otpauth://...", List.of("C1", "C2"));
		when(manageMfaUseCase.initiateSetup(new UserId(actorId), "ed-iam")).thenReturn(expected);

		ResponseEntity<MfaSetupResponse> response = controller.setupMfa();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
	}

	@Test
	@DisplayName("Should activate MFA for calling actor")
	void shouldActivateMfa() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaActivateRequest request = new MfaActivateRequest("123456");

		ResponseEntity<Void> response = controller.activateMfa(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(manageMfaUseCase).activate(new UserId(actorId), "123456");
	}

	@Test
	@DisplayName("Should disable MFA for calling actor")
	void shouldDisableMfa() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaDisableRequest request = new MfaDisableRequest("123456");

		ResponseEntity<Void> response = controller.disableMfa(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(manageMfaUseCase).disable(new UserId(actorId), "123456");
	}
}

