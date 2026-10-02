package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MagicLinkService}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class MagicLinkServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MagicLinkTokenStorePort tokenStore;

	@Mock
	private MagicLinkNotifierPort notifier;

	@Mock
	private AuthenticationProviderRouter authRouter;

	@Mock
	private EffectiveAccessResolver effectiveAccessResolver;

	@Mock
	private TokenProviderPort tokenProvider;

	@Mock
	private UserMfaRepository userMfaRepository;

	@Mock
	private SessionRegistryPort sessionRegistry;

	@Mock
	private TokenRevocationPort tokenRevocationPort;

	private MagicLinkProperties properties;
	private SessionProperties sessionProperties;
	private MagicLinkService service;

	@BeforeEach
	void setUp() {
		properties = new MagicLinkProperties(true, 900L, "http://localhost:8080");
		sessionProperties = SessionProperties.defaultProperties();
		service = new MagicLinkService(
				properties,
				userRepository,
				tokenStore,
				notifier,
				authRouter,
				effectiveAccessResolver,
				tokenProvider,
				userMfaRepository,
				sessionRegistry,
				tokenRevocationPort,
				sessionProperties);
	}

	@Test
	@DisplayName("Should successfully request and dispatch magic link")
	void shouldRequestMagicLinkSuccessfully() {
		Instant now = Instant.now();
		User user = new User(UserId.generate(), "user@test.com", "hash", "Test User", UserStatus.ACTIVE, false, now, now);
		when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

		MagicLinkRequestCommand command = MagicLinkRequestCommand.of("user@test.com", null, "https://app.example.com/welcome");
		MagicLinkRequestResponse response = service.requestMagicLink(command);

		assertThat(response).isNotNull();
		assertThat(response.message()).contains("sign-in link");

		verify(tokenStore).save(any(MagicLinkToken.class));

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		verify(notifier).sendMagicLink(any(MagicLinkToken.class), urlCaptor.capture());
		assertThat(urlCaptor.getValue()).contains("http://localhost:8080/api/v1/auth/magic-link/verify?token=");
		assertThat(urlCaptor.getValue()).contains("&redirect=https%3A%2F%2Fapp.example.com%2Fwelcome");
	}

	@Test
	@DisplayName("Should return generic success message without dispatching when email does not exist (anti-enumeration)")
	void shouldReturnGenericMessageWhenUserNotFoundOnRequest() {
		when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

		MagicLinkRequestResponse response = service.requestMagicLink(MagicLinkRequestCommand.of("unknown@test.com"));

		assertThat(response).isNotNull();
		assertThat(response.message()).contains("sign-in link");
		verify(tokenStore, org.mockito.Mockito.never()).save(any());
		verify(notifier, org.mockito.Mockito.never()).sendMagicLink(any(), any());
	}

	@Test
	@DisplayName("Should return generic success message without dispatching when user is inactive (anti-enumeration)")
	void shouldReturnGenericMessageWhenUserInactiveOnRequest() {
		Instant now = Instant.now();
		User user = new User(UserId.generate(), "inactive@test.com", "hash", "Inactive User", UserStatus.ACTIVE, false, now, now);
		user.suspend();
		when(userRepository.findByEmail("inactive@test.com")).thenReturn(Optional.of(user));

		MagicLinkRequestResponse response = service.requestMagicLink(MagicLinkRequestCommand.of("inactive@test.com"));

		assertThat(response).isNotNull();
		assertThat(response.message()).contains("sign-in link");
		verify(tokenStore, org.mockito.Mockito.never()).save(any());
		verify(notifier, org.mockito.Mockito.never()).sendMagicLink(any(), any());
	}

	@Test
	@DisplayName("Should throw AuthenticationException when magic link feature is disabled")
	void shouldThrowWhenFeatureDisabled() {
		MagicLinkProperties disabledProps = new MagicLinkProperties(false, 900L, "http://localhost:8080");
		MagicLinkService disabledService = new MagicLinkService(
				disabledProps, userRepository, tokenStore, notifier, authRouter,
				effectiveAccessResolver, tokenProvider);

		assertThatThrownBy(() -> disabledService.requestMagicLink(MagicLinkRequestCommand.of("user@test.com")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("disabled");

		assertThatThrownBy(() -> disabledService.verifyMagicLink(MagicLinkVerifyCommand.of("some-tok")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("disabled");
	}

	@Test
	@DisplayName("Should successfully verify magic link and issue tokens")
	void shouldVerifyMagicLinkSuccessfully() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("valid-tok", userId, tenantId, "user@test.com", now.plusSeconds(300), now);
		User user = new User(userId, "user@test.com", "hash", "Test User", UserStatus.ACTIVE, false, now, now);

		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "user@test.com", "Test User", false, ProviderType.MAGIC_LINK);
		EffectiveAccess effectiveAccess = new EffectiveAccess(
				user.getId(),
				user.getEmail(),
				tenantId,
				false,
				false,
				Collections.singleton(tenantId),
				Set.of(),
				Set.of("ROLE_USER"),
				Set.of("read"),
				Set.of(),
				Set.of());

		when(tokenStore.findByToken("valid-tok")).thenReturn(Optional.of(token));
		when(authRouter.authenticate(new MagicLinkAuthCredentials("valid-tok"))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(effectiveAccess);
		when(tokenProvider.createAccessToken(eq(effectiveAccess), any())).thenReturn("access-token-xyz");
		when(tokenProvider.createRefreshToken(user.getId(), tenantId)).thenReturn("refresh-token-xyz");
		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);
		when(sessionRegistry.findActiveSessions(user.getId(), tenantId)).thenReturn(List.of());

		TokenResponse response = service.verifyMagicLink(MagicLinkVerifyCommand.of("valid-tok", "127.0.0.1", "curl/8.0"));

		assertThat(response).isNotNull();
		assertThat(response.accessToken()).isEqualTo("access-token-xyz");
		assertThat(response.refreshToken()).isEqualTo("refresh-token-xyz");
		assertThat(response.user().email()).isEqualTo("user@test.com");

		verify(sessionRegistry).registerSession(any(UserSession.class));
	}

	@Test
	@DisplayName("Should return MFA challenge when user has active MFA configured")
	void shouldReturnMfaChallengeWhenMfaActive() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("valid-tok", userId, null, "user@test.com", now.plusSeconds(300), now);
		User user = new User(userId, "user@test.com", "hash", "Test User", UserStatus.ACTIVE, false, now, now);
		UserMfa mfa = new UserMfa(userId, "BASE32SECRET", true, List.of(), now, now);

		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "user@test.com", "Test User", false, ProviderType.MAGIC_LINK);

		when(tokenStore.findByToken("valid-tok")).thenReturn(Optional.of(token));
		when(authRouter.authenticate(new MagicLinkAuthCredentials("valid-tok"))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
		when(tokenProvider.createMfaChallengeToken(userId, null)).thenReturn("mfa-challenge-tok");

		TokenResponse response = service.verifyMagicLink(MagicLinkVerifyCommand.of("valid-tok"));

		assertThat(response.mfaRequired()).isTrue();
		assertThat(response.mfaToken()).isEqualTo("mfa-challenge-tok");
		assertThat(response.accessToken()).isNull();
	}

	@Test
	@DisplayName("Should terminate oldest session when concurrent limit is exceeded")
	void shouldTerminateOldestSessionWhenLimitExceeded() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("tok", userId, tenantId, "user@test.com", now.plusSeconds(300), now);
		User user = new User(userId, "user@test.com", "hash", "Test User", UserStatus.ACTIVE, false, now, now);
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "user@test.com", "Test User", false, ProviderType.MAGIC_LINK);

		EffectiveAccess effectiveAccess = new EffectiveAccess(
				user.getId(), user.getEmail(), tenantId, false, false, Collections.singleton(tenantId), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		SessionProperties limitProps = new SessionProperties(1, SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST, 5, 900L);
		MagicLinkService limitedService = new MagicLinkService(
				properties, userRepository, tokenStore, notifier, authRouter,
				effectiveAccessResolver, tokenProvider, userMfaRepository,
				sessionRegistry, tokenRevocationPort, limitProps);

		UserSession oldSession = new UserSession(
				SessionId.generate(), userId, tenantId, "old-jti",
				now.minusSeconds(100), now.plusSeconds(1000), now.minusSeconds(50),
				"127.0.0.1", "curl", false);

		when(tokenStore.findByToken("tok")).thenReturn(Optional.of(token));
		when(authRouter.authenticate(new MagicLinkAuthCredentials("tok"))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(effectiveAccess);
		when(sessionRegistry.findActiveSessions(user.getId(), tenantId)).thenReturn(List.of(oldSession));
		when(tokenProvider.createAccessToken(eq(effectiveAccess), any())).thenReturn("access-token");
		when(tokenProvider.createRefreshToken(user.getId(), tenantId)).thenReturn("refresh-token");
		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

		TokenResponse response = limitedService.verifyMagicLink(MagicLinkVerifyCommand.of("tok"));
		assertThat(response.accessToken()).isEqualTo("access-token");

		verify(sessionRegistry).revokeSession(oldSession.id());
		verify(tokenRevocationPort).revokeToken(oldSession.tokenIdentifier(), oldSession.expiresAt());
	}
}
