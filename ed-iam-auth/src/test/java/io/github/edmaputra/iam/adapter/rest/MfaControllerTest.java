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

import io.github.edmaputra.iam.adapter.rest.dto.MfaActivateRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaDisableRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaLoginVerifyRequest;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaController}.
 *
 * @author edmaputra
 * @since 0.6.0
 */
class MfaControllerTest {

	private ManageMfaUseCase manageMfaUseCase;
	private CurrentActorProvider currentActorProvider;
	private MfaController controller;

	@BeforeEach
	void setUp() {
		manageMfaUseCase = mock(ManageMfaUseCase.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		controller = new MfaController(manageMfaUseCase, currentActorProvider);
	}

	@Test
	@DisplayName("Should enforce non-null dependencies in constructor")
	void shouldEnforceNonNullDependencies() {
		assertThatThrownBy(() -> new MfaController(null, currentActorProvider))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MfaController(manageMfaUseCase, null))
				.isInstanceOf(NullPointerException.class);
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
