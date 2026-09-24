package io.github.edmaputra.iam.adapter.rest;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.LoginRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RefreshTokenRequest;
import io.github.edmaputra.iam.adapter.rest.dto.SwitchTenantRequest;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
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
	private AuthController controller;

	@BeforeEach
	void setUp() {
		authenticateUserUseCase = mock(AuthenticateUserUseCase.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		controller = new AuthController(authenticateUserUseCase, currentActorProvider);
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
}
