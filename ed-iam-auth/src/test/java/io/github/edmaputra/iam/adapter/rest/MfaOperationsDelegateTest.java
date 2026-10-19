package io.github.edmaputra.iam.adapter.rest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.adapter.rest.dto.MfaActivateRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaDisableRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaLoginVerifyRequest;
import io.github.edmaputra.iam.adapter.security.properties.MfaProperties;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.in.MfaLoginVerifyCommand;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaOperationsDelegate}.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class MfaOperationsDelegateTest {

	private ManageMfaUseCase manageMfaUseCase;
	private CurrentActorProvider currentActorProvider;
	private MfaProperties mfaProperties;
	private MfaOperationsDelegate delegate;

	@BeforeEach
	void setUp() {
		manageMfaUseCase = mock(ManageMfaUseCase.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		mfaProperties = new MfaProperties("CustomApp", new MfaProperties.Endpoints(true));
		delegate = new MfaOperationsDelegate(manageMfaUseCase, currentActorProvider, mfaProperties);
	}

	@Test
	@DisplayName("Should enforce non-null dependencies in constructor")
	void shouldEnforceNonNullDependencies() {
		assertThatThrownBy(() -> new MfaOperationsDelegate(null, currentActorProvider, mfaProperties))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MfaOperationsDelegate(manageMfaUseCase, null, mfaProperties))
				.isInstanceOf(NullPointerException.class);

		MfaOperationsDelegate defaultPropsDelegate = new MfaOperationsDelegate(manageMfaUseCase, currentActorProvider, null);
		assertThat(defaultPropsDelegate).isNotNull();

		MfaOperationsDelegate twoArgDelegate = new MfaOperationsDelegate(manageMfaUseCase, currentActorProvider);
		assertThat(twoArgDelegate).isNotNull();
	}

	@Test
	@DisplayName("Should verify MFA login challenge with HttpServletRequest metadata")
	void shouldVerifyLoginWithHttpRequest() {
		TokenResponse expectedToken = createSampleTokenResponse();
		when(manageMfaUseCase.verifyLogin(any())).thenReturn(expectedToken);

		HttpServletRequest httpRequest = mock(HttpServletRequest.class);
		when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");
		when(httpRequest.getHeader("User-Agent")).thenReturn("CustomAgent/1.0");

		MfaLoginVerifyRequest request = new MfaLoginVerifyRequest("mfa-token-123", "654321");
		TokenResponse actual = delegate.verifyLogin(request, httpRequest);

		assertThat(actual).isSameAs(expectedToken);
		verify(manageMfaUseCase).verifyLogin(new MfaLoginVerifyCommand("mfa-token-123", "654321", "203.0.113.195", "CustomAgent/1.0"));
	}

	@Test
	@DisplayName("Should verify MFA login challenge with explicit IP and User-Agent")
	void shouldVerifyLoginWithExplicitMetadata() {
		TokenResponse expectedToken = createSampleTokenResponse();
		when(manageMfaUseCase.verifyLogin(any())).thenReturn(expectedToken);

		MfaLoginVerifyRequest request = new MfaLoginVerifyRequest("mfa-token-123", "654321");
		TokenResponse actual = delegate.verifyLogin(request, "10.0.0.1", "curl/7.68.0");

		assertThat(actual).isSameAs(expectedToken);
		verify(manageMfaUseCase).verifyLogin(new MfaLoginVerifyCommand("mfa-token-123", "654321", "10.0.0.1", "curl/7.68.0"));
	}

	@Test
	@DisplayName("Should verify MFA login challenge with raw parameters")
	void shouldVerifyLoginWithRawParameters() {
		TokenResponse expectedToken = createSampleTokenResponse();
		when(manageMfaUseCase.verifyLogin(any())).thenReturn(expectedToken);

		TokenResponse actual = delegate.verifyLogin("raw-token", "112233", "192.168.0.1", "Postman");

		assertThat(actual).isSameAs(expectedToken);
		verify(manageMfaUseCase).verifyLogin(new MfaLoginVerifyCommand("raw-token", "112233", "192.168.0.1", "Postman"));
	}

	@Test
	@DisplayName("Should retrieve MFA status for currently authenticated actor")
	void shouldGetStatusForCurrentActor() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaStatusResponse expected = new MfaStatusResponse(true, Instant.now());
		when(manageMfaUseCase.getStatus(new UserId(actorId))).thenReturn(expected);

		MfaStatusResponse actual = delegate.getStatus();

		assertThat(actual).isSameAs(expected);
	}

	@Test
	@DisplayName("Should retrieve MFA status for explicit UserId and UUID")
	void shouldGetStatusForExplicitUser() {
		UUID userIdVal = UUID.randomUUID();
		UserId userId = new UserId(userIdVal);
		MfaStatusResponse expected = new MfaStatusResponse(false, null);
		when(manageMfaUseCase.getStatus(userId)).thenReturn(expected);

		assertThat(delegate.getStatus(userId)).isSameAs(expected);
		assertThat(delegate.getStatus(userIdVal)).isSameAs(expected);
	}

	@Test
	@DisplayName("Should initiate setup using configured issuer for current actor")
	void shouldInitiateSetupWithDefaultIssuer() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaSetupResponse expected = new MfaSetupResponse("SECRET", "otpauth://...", List.of("CODE1"));
		when(manageMfaUseCase.initiateSetup(new UserId(actorId), "CustomApp")).thenReturn(expected);

		MfaSetupResponse actual = delegate.initiateSetup();

		assertThat(actual).isSameAs(expected);
	}

	@Test
	@DisplayName("Should initiate setup with custom issuer for current actor and explicit user")
	void shouldInitiateSetupWithCustomIssuer() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		MfaSetupResponse expected = new MfaSetupResponse("SECRET", "otpauth://...", List.of("CODE1"));
		when(manageMfaUseCase.initiateSetup(new UserId(actorId), "SpecialClinic")).thenReturn(expected);

		MfaSetupResponse actual = delegate.initiateSetup("SpecialClinic");
		assertThat(actual).isSameAs(expected);

		UserId explicitUser = new UserId(UUID.randomUUID());
		when(manageMfaUseCase.initiateSetup(explicitUser, "SpecialClinic")).thenReturn(expected);
		assertThat(delegate.initiateSetup(explicitUser, "SpecialClinic")).isSameAs(expected);
	}

	@Test
	@DisplayName("Should activate MFA with request DTO, raw code, and explicit user")
	void shouldActivateMfa() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		delegate.activate(new MfaActivateRequest("123456"));
		verify(manageMfaUseCase).activate(new UserId(actorId), "123456");

		delegate.activate("654321");
		verify(manageMfaUseCase).activate(new UserId(actorId), "654321");

		UserId otherUser = new UserId(UUID.randomUUID());
		delegate.activate(otherUser, "999888");
		verify(manageMfaUseCase).activate(otherUser, "999888");
	}

	@Test
	@DisplayName("Should disable MFA with request DTO, raw code/password, and explicit user")
	void shouldDisableMfa() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(currentActorProvider.requireCurrentActor()).thenReturn(actor);

		delegate.disable(new MfaDisableRequest("pwd123"));
		verify(manageMfaUseCase).disable(new UserId(actorId), "pwd123");

		delegate.disable("totp999");
		verify(manageMfaUseCase).disable(new UserId(actorId), "totp999");

		UserId otherUser = new UserId(UUID.randomUUID());
		delegate.disable(otherUser, "master-secret");
		verify(manageMfaUseCase).disable(otherUser, "master-secret");
	}

	@Test
	@DisplayName("Should reset MFA for UserId and UUID")
	void shouldResetMfa() {
		UUID targetUuid = UUID.randomUUID();
		UserId targetUserId = new UserId(targetUuid);

		delegate.resetMfa(targetUserId);
		verify(manageMfaUseCase).resetMfa(targetUserId);

		delegate.resetMfa(targetUuid);
		verify(manageMfaUseCase, org.mockito.Mockito.times(2)).resetMfa(targetUserId);
	}

	private TokenResponse createSampleTokenResponse() {
		UserProfileResponse profile = new UserProfileResponse(
				UUID.randomUUID(), "test@metro.org", "Test User", null, false, false,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		return new TokenResponse("access-token", "refresh-token", "Bearer", 900L, profile);
	}
}
