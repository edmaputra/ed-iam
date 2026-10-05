package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link AuthenticationService}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class AuthenticationServiceTest {

	private AuthenticationProviderRouter authRouter;
	private UserRepository userRepository;
	private EffectiveAccessResolver effectiveAccessResolver;
	private TokenProviderPort tokenProvider;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		authRouter = mock(AuthenticationProviderRouter.class);
		userRepository = mock(UserRepository.class);
		effectiveAccessResolver = mock(EffectiveAccessResolver.class);
		tokenProvider = mock(TokenProviderPort.class);

		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

		service = new AuthenticationService(authRouter, userRepository, effectiveAccessResolver, tokenProvider);
	}

	@Test
	@DisplayName("Should successfully authenticate and return token pair on login")
	void shouldLogin() {
		UserId userId = UserId.generate();
		User user = User.create("test@clinic.org", "hash", "Test User", false);
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "test@clinic.org", "Test User", false, ProviderType.LOCAL);
		EffectiveAccess access = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(), Set.of(), Set.of("DOCTOR"), Set.of("READ"), Set.of(), Set.of());

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);
		when(tokenProvider.createAccessToken(access)).thenReturn("access-token-123");
		when(tokenProvider.createRefreshToken(user.getId(), null)).thenReturn("refresh-token-123");

		LoginCommand command = new LoginCommand("test@clinic.org", "Password123!", null);
		TokenResponse response = service.login(command);

		assertThat(response.accessToken()).isEqualTo("access-token-123");
		assertThat(response.refreshToken()).isEqualTo("refresh-token-123");
		assertThat(response.expiresIn()).isEqualTo(900L);
		assertThat(response.user().email()).isEqualTo("test@clinic.org");
	}

	@Test
	@DisplayName("Should refresh tokens when refresh token is valid")
	void shouldRefreshToken() {
		UserId userId = UserId.generate();
		User user = User.create("test@clinic.org", "hash", "Test User", false);
		RefreshTokenClaims claims = new RefreshTokenClaims(userId, null, java.time.Instant.now(), java.time.Instant.now().plusSeconds(900));
		EffectiveAccess access = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(tokenProvider.parseRefreshToken("valid-refresh")).thenReturn(claims);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);
		when(tokenProvider.createAccessToken(access)).thenReturn("new-access");
		when(tokenProvider.createRefreshToken(user.getId(), null)).thenReturn("new-refresh");

		TokenResponse response = service.refreshToken(new RefreshTokenCommand("valid-refresh"));

		assertThat(response.accessToken()).isEqualTo("new-access");
		assertThat(response.refreshToken()).isEqualTo("new-refresh");
	}

	@Test
	@DisplayName("Should reject token refresh when user is suspended or deactivated")
	void shouldRejectRefreshWhenSuspendedOrDeactivated() {
		UserId userId = UserId.generate();
		User suspendedUser = User.create("test@clinic.org", "hash", "Test User", false);
		suspendedUser.suspend();
		User deactivatedUser = User.create("test@clinic.org", "hash", "Test User", false);
		deactivatedUser.deactivate();
		RefreshTokenClaims claims = new RefreshTokenClaims(userId, null, java.time.Instant.now(), java.time.Instant.now().plusSeconds(900));

		when(tokenProvider.parseRefreshToken("refresh-token")).thenReturn(claims);

		when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));
		assertThatThrownBy(() -> service.refreshToken(new RefreshTokenCommand("refresh-token")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");

		when(userRepository.findById(userId)).thenReturn(Optional.of(deactivatedUser));
		assertThatThrownBy(() -> service.refreshToken(new RefreshTokenCommand("refresh-token")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("deactivated");
	}

	@Test
	@DisplayName("Should rotate refresh token and revoke old token id")
	void shouldRotateRefreshTokenAndRevokeOldTokenId() {
		UserId userId = UserId.generate();
		User user = User.create("test@clinic.org", "hash", "Test User", false);
		Instant expiresAt = Instant.now().plusSeconds(900);
		RefreshTokenClaims claims = new RefreshTokenClaims(userId, null, Instant.now(), expiresAt, "old-token-id");
		EffectiveAccess access = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		TokenRevocationPort tokenRevocationPort = mock(TokenRevocationPort.class);
		when(tokenRevocationPort.isTokenRevoked("old-token-id")).thenReturn(false);

		AuthenticationService serviceWithRevocation = new AuthenticationService(
				authRouter,
				userRepository,
				effectiveAccessResolver,
				tokenProvider,
				null,
				null,
				tokenRevocationPort,
				SessionProperties.defaultProperties(),
				null);

		when(tokenProvider.parseRefreshToken("valid-refresh")).thenReturn(claims);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);
		when(tokenProvider.createAccessToken(access)).thenReturn("new-access");
		when(tokenProvider.createRefreshToken(user.getId(), null)).thenReturn("new-refresh");

		TokenResponse response = serviceWithRevocation.refreshToken(new RefreshTokenCommand("valid-refresh"));

		assertThat(response.accessToken()).isEqualTo("new-access");
		assertThat(response.refreshToken()).isEqualTo("new-refresh");
		verify(tokenRevocationPort).revokeToken("old-token-id", expiresAt);
	}

	@Test
	@DisplayName("Should detect refresh token reuse, revoke all sessions, and reject request")
	void shouldDetectRefreshTokenReuseAndInvalidateAllSessions() {
		UserId userId = UserId.generate();
		Instant expiresAt = Instant.now().plusSeconds(900);
		RefreshTokenClaims claims = new RefreshTokenClaims(userId, null, Instant.now(), expiresAt, "revoked-token-id");

		TokenRevocationPort tokenRevocationPort = mock(TokenRevocationPort.class);
		when(tokenRevocationPort.isTokenRevoked("revoked-token-id")).thenReturn(true);

		AuthenticationService serviceWithRevocation = new AuthenticationService(
				authRouter,
				userRepository,
				effectiveAccessResolver,
				tokenProvider,
				null,
				null,
				tokenRevocationPort,
				SessionProperties.defaultProperties(),
				null);

		when(tokenProvider.parseRefreshToken("reused-refresh")).thenReturn(claims);

		assertThatThrownBy(() -> serviceWithRevocation.refreshToken(new RefreshTokenCommand("reused-refresh")))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Refresh token has been revoked.");

		verify(tokenRevocationPort).revokeAllForUser(any(), any());
	}

	@Test
	@DisplayName("Should switch tenant when user has access to target tenant")
	void shouldSwitchTenant() {
		UserId userId = UserId.generate();
		TenantId tenantA = TenantId.generate();
		TenantId tenantB = TenantId.generate();
		User user = User.create("test@clinic.org", "hash", "Test User", false);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(userId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);

		EffectiveAccess fullAccess = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(tenantA, tenantB), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		EffectiveAccess switchedAccess = new EffectiveAccess(userId, "test@clinic.org", tenantB, false, false, Set.of(tenantA, tenantB), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(fullAccess);
		when(effectiveAccessResolver.resolve(user, tenantB)).thenReturn(switchedAccess);
		when(tokenProvider.createAccessToken(switchedAccess)).thenReturn("switched-access");
		when(tokenProvider.createRefreshToken(user.getId(), tenantB)).thenReturn("switched-refresh");

		TokenResponse response = service.switchTenant(new SwitchTenantCommand(actor, tenantB));

		assertThat(response.accessToken()).isEqualTo("switched-access");
		assertThat(response.refreshToken()).isEqualTo("switched-refresh");
	}

	@Test
	@DisplayName("Should deny switch tenant when non-superadmin user lacks access to target tenant")
	void shouldDenySwitchTenantWhenAccessDenied() {
		UserId userId = UserId.generate();
		TenantId tenantA = TenantId.generate();
		TenantId tenantForbidden = TenantId.generate();
		User user = User.create("test@clinic.org", "hash", "Test User", false);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(userId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);

		EffectiveAccess fullAccess = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(tenantA), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(fullAccess);

		assertThatThrownBy(() -> service.switchTenant(new SwitchTenantCommand(actor, tenantForbidden)))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("User does not have access to tenant");
	}

	@Test
	@DisplayName("Should return user profile on getMe")
	void shouldGetMe() {
		UUID actorId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(actor.email()).thenReturn("me@clinic.org");
		when(actor.roles()).thenReturn(Set.of("DOC"));
		when(actor.groups()).thenReturn(Set.of());
		when(actor.permissions()).thenReturn(Set.of("READ"));
		when(actor.accessibleScopePaths()).thenReturn(Set.of("/"));
		when(actor.accessibleScopeNodeIds()).thenReturn(Set.of());
		User user = User.create("me@clinic.org", "hash", "Dr. Me", false);
		EffectiveAccess access = new EffectiveAccess(new UserId(actorId), "me@clinic.org", null, false, false, Set.of(), Set.of(), Set.of("DOC"), Set.of("READ"), Set.of(), Set.of("/"));

		when(userRepository.findById(new UserId(actorId))).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);

		UserProfileResponse profile = service.getMe(actor);

		assertThat(profile.id()).isEqualTo(actorId);
		assertThat(profile.fullName()).isEqualTo("Dr. Me");
		assertThat(profile.roles()).containsExactly("DOC");
	}

	@Test
	@DisplayName("Should block login if account is locked by brute-force tracker")
	void shouldBlockLoginWhenLocked() {
		LoginAttemptTrackerPort tracker = mock(LoginAttemptTrackerPort.class);
		Instant lockedUntil = Instant.now().plusSeconds(600);
		when(tracker.getLockoutStatus("locked@clinic.org"))
				.thenReturn(LockoutStatus.locked(5, lockedUntil));

		AuthenticationService serviceWithTracker = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				tracker, null, null, SessionProperties.defaultProperties());

		LoginCommand command = new LoginCommand("locked@clinic.org", "Pass123!", null);

		assertThatThrownBy(() -> serviceWithTracker.login(command))
				.isInstanceOf(AccountLockedException.class)
				.hasMessageContaining("temporarily locked");
	}

	@Test
	@DisplayName("Should enforce concurrent session limit and terminate oldest session")
	void shouldTerminateOldestSessionOnLimit() {
		SessionRegistryPort registry = mock(SessionRegistryPort.class);
		TokenRevocationPort revocation = mock(TokenRevocationPort.class);

		SessionProperties props = new SessionProperties(
				1, SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST, 5, 900);

		AuthenticationService serviceWithSession = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				null, registry, revocation, props);

		UserId userId = UserId.generate();
		User user = new User(userId, "test@clinic.org", "hash", "Test User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "test@clinic.org", "Test User", false, ProviderType.LOCAL);
		EffectiveAccess access = new EffectiveAccess(userId, "test@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);
		when(tokenProvider.createAccessToken(access)).thenReturn("new-token");
		when(tokenProvider.createRefreshToken(user.getId(), null)).thenReturn("new-refresh-token");

		UserSession existingSession = UserSession.create(
				userId, null, "old-token-jti", 3600, "127.0.0.1", "Old-Agent");
		when(registry.findActiveSessions(userId, null)).thenReturn(List.of(existingSession));

		TokenResponse response = serviceWithSession.login(new LoginCommand("test@clinic.org", "Pass123!", null));

		assertThat(response.accessToken()).isNotNull();
		verify(registry).revokeSession(existingSession.id());
		verify(revocation).revokeToken(org.mockito.ArgumentMatchers.eq("old-token-jti"), any());
		verify(registry).registerSession(any(UserSession.class));
	}

	@Test
	@DisplayName("Should handle logout and logout-all")
	void shouldHandleLogoutAndLogoutAll() {
		SessionRegistryPort registry = mock(SessionRegistryPort.class);
		TokenRevocationPort revocation = mock(TokenRevocationPort.class);

		AuthenticationService serviceWithSession = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				null, registry, revocation, SessionProperties.defaultProperties());

		UserId userId = UserId.generate();
		UserSession session = UserSession.create(
				userId, null, "logout-jti", 3600, null, null);
		when(registry.findByTokenIdentifier("logout-jti")).thenReturn(Optional.of(session));

		serviceWithSession.logout("logout-jti");
		verify(revocation).revokeToken(org.mockito.ArgumentMatchers.eq("logout-jti"), any());
		verify(registry).revokeSession(session.id());

		when(registry.findActiveSessions(userId, null)).thenReturn(List.of(session));
		serviceWithSession.logoutAll(userId, null);
		verify(revocation).revokeAllForUser(org.mockito.ArgumentMatchers.eq(userId), any());
	}

	@Test
	@DisplayName("Should return MFA challenge token when user has MFA enabled")
	void shouldIssueMfaChallengeWhenUserHasMfaEnabled() {
		UserMfaRepository mfaRepository = mock(UserMfaRepository.class);

		AuthenticationService mfaAuthService = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				null, null, null, SessionProperties.defaultProperties(),
				mfaRepository);

		UserId userId = UserId.generate();
		User user = new User(userId, "mfa-user@clinic.org", "hash", "MFA User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "mfa-user@clinic.org", "MFA User", false, ProviderType.LOCAL);

		UserMfa mfa = UserMfa.create(userId, "SECRET", List.of());
		mfa.activate();

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(mfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
		when(tokenProvider.createMfaChallengeToken(userId, null)).thenReturn("mfa-challenge-jwt");

		TokenResponse response = mfaAuthService.login(LoginCommand.of("mfa-user@clinic.org", "Secret123!"));

		assertThat(response.mfaRequired()).isTrue();
		assertThat(response.mfaToken()).isEqualTo("mfa-challenge-jwt");
		assertThat(response.accessToken()).isNull();
	}

	@Test
	@DisplayName("Should record telemetry and publish audit events on successful and failed login")
	void shouldRecordTelemetryAndPublishAuditEvents() {
		IamTelemetry telemetry = mock(IamTelemetry.class);
		EventPublisherPort publisher = mock(EventPublisherPort.class);

		AuthenticationService serviceWithTelemetry = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				null, null, null, SessionProperties.defaultProperties(),
				null, telemetry, publisher);

		UserId userId = UserId.generate();
		User user = new User(userId, "audit@clinic.org", "hash", "Audit User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "audit@clinic.org", "Audit User", false, ProviderType.LOCAL);
		EffectiveAccess effectiveAccess = new EffectiveAccess(userId, "audit@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(effectiveAccess);
		when(tokenProvider.createAccessToken(any(), any())).thenReturn("access-token-123");
		when(tokenProvider.createRefreshToken(any(), any())).thenReturn("refresh-token-123");

		TokenResponse response = serviceWithTelemetry.login(LoginCommand.of("audit@clinic.org", "Secret123!"));
		assertThat(response.accessToken()).isEqualTo("access-token-123");

		verify(telemetry).recordAuthenticationLatency(eq("password"), eq("success"), any(), any());
		verify(telemetry).recordAuthenticationAttempt(eq("password"), eq("success"), any());
		verify(publisher, atLeastOnce()).publish(argThat(event ->
				event.eventType().equals(IamEventTypes.LOGIN_SUCCESS) &&
						event.actor().equals("audit@clinic.org")));

		// Now test failure
		when(authRouter.authenticate(any(PasswordAuthCredentials.class)))
				.thenThrow(new AuthenticationException("Bad credentials"));

		assertThatThrownBy(() -> serviceWithTelemetry.login(LoginCommand.of("fail@clinic.org", "wrong")))
				.isInstanceOf(AuthenticationException.class);

		verify(telemetry).recordAuthenticationLatency(eq("password"), eq("failure"), any(), any());
		verify(telemetry).recordAuthenticationAttempt(eq("password"), eq("failure"), any());
		verify(telemetry).recordAccessDenied(eq("bad_credentials"), any(), any(), eq("fail@clinic.org"));
		verify(publisher).publish(argThat(event ->
				event.eventType().equals(IamEventTypes.LOGIN_FAILED) &&
						event.actor().equals("fail@clinic.org")));
	}

	@Test
	@DisplayName("Should use SecurityAuditRecorder directly on login success and failure")
	void shouldUseSecurityAuditRecorderDirectly() {
		SecurityAuditRecorder recorder = mock(SecurityAuditRecorder.class);

		AuthenticationService service = new AuthenticationService(
				authRouter, userRepository, effectiveAccessResolver, tokenProvider,
				null, null, null, SessionProperties.defaultProperties(),
				null, recorder);

		UserId userId = UserId.generate();
		User user = new User(userId, "rec@clinic.org", "hash", "Recorder User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "rec@clinic.org", "Recorder User", false, ProviderType.LOCAL);
		EffectiveAccess effectiveAccess = new EffectiveAccess(userId, "rec@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(authRouter.authenticate(any(PasswordAuthCredentials.class))).thenReturn(identity);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(effectiveAccess);
		when(tokenProvider.createAccessToken(any(), any())).thenReturn("access-token-rec");
		when(tokenProvider.createRefreshToken(any(), any())).thenReturn("refresh-token-rec");

		TokenResponse response = service.login(LoginCommand.of("rec@clinic.org", "Secret123!"));
		assertThat(response.accessToken()).isEqualTo("access-token-rec");
		assertThat(response.refreshToken()).isEqualTo("refresh-token-rec");

		verify(recorder).recordLoginSuccess(eq("password"), eq("rec@clinic.org"), eq(userId.value()), eq(null), any(), any(Long.class));

		when(authRouter.authenticate(any(PasswordAuthCredentials.class)))
				.thenThrow(new AuthenticationException("Invalid password"));

		assertThatThrownBy(() -> service.login(LoginCommand.of("fail-rec@clinic.org", "bad")))
				.isInstanceOf(AuthenticationException.class);

		verify(recorder).recordLoginFailure(eq("password"), eq("fail-rec@clinic.org"), eq(null), eq("bad_credentials"), eq("Invalid password"), any(), any(Long.class));
	}
}

