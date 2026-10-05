package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthSessionService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

	@Mock
	private SessionRegistryPort sessionRegistry;

	@Mock
	private TokenRevocationPort tokenRevocationPort;

	@Mock
	private SecurityAuditRecorder auditRecorder;

	private AuthSessionService sessionService;

	@BeforeEach
	void setUp() {
		sessionService = new AuthSessionService(
				sessionRegistry,
				tokenRevocationPort,
				SessionProperties.defaultProperties(),
				auditRecorder);
	}

	@Test
	@DisplayName("Should register session when concurrent limit is not exceeded")
	void shouldRegisterSession() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();

		when(sessionRegistry.findActiveSessions(userId, tenantId)).thenReturn(List.of());

		sessionService.registerSession(userId, tenantId, "jti-123", 3600L, "192.168.1.1", "Mozilla/5.0");

		ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
		verify(sessionRegistry).registerSession(sessionCaptor.capture());

		UserSession created = sessionCaptor.getValue();
		assertThat(created.userId()).isEqualTo(userId);
		assertThat(created.tenantId()).isEqualTo(tenantId);
		assertThat(created.tokenIdentifier()).isEqualTo("jti-123");
		assertThat(created.ipAddress()).isEqualTo("192.168.1.1");
		assertThat(created.userAgent()).isEqualTo("Mozilla/5.0");
	}

	@Test
	@DisplayName("Should throw AuthenticationException when session limit strategy is REJECT_NEW and max reached")
	void shouldRejectNewSessionWhenLimitReached() {
		SessionProperties rejectProps = new SessionProperties(1, SessionProperties.SessionLimitStrategy.REJECT_NEW, 5, 900L);
		AuthSessionService rejectService = new AuthSessionService(sessionRegistry, tokenRevocationPort, rejectProps, auditRecorder);

		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		UserSession existing = new UserSession(SessionId.generate(), userId, tenantId, "jti-old", now, now.plusSeconds(3600), now, "1.1.1.1", "Agent", false);

		when(sessionRegistry.findActiveSessions(userId, tenantId)).thenReturn(List.of(existing));

		assertThatThrownBy(() -> rejectService.registerSession(userId, tenantId, "jti-new", 3600L, "2.2.2.2", "Agent"))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Maximum concurrent active sessions (1) exceeded");

		verify(sessionRegistry, never()).registerSession(any());
	}

	@Test
	@DisplayName("Should terminate oldest session when session limit strategy is TERMINATE_OLDEST and max reached")
	void shouldTerminateOldestSessionWhenLimitReached() {
		SessionProperties terminateProps = new SessionProperties(1, SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST, 5, 900L);
		AuthSessionService terminateService = new AuthSessionService(sessionRegistry, tokenRevocationPort, terminateProps, auditRecorder);

		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		UserSession oldSession = new UserSession(SessionId.generate(), userId, tenantId, "jti-old", now.minusSeconds(100), now.plusSeconds(3600), now, "1.1.1.1", "Agent", false);

		when(sessionRegistry.findActiveSessions(userId, tenantId)).thenReturn(List.of(oldSession));

		terminateService.registerSession(userId, tenantId, "jti-new", 3600L, "2.2.2.2", "Agent");

		verify(sessionRegistry).revokeSession(oldSession.id());
		verify(tokenRevocationPort).revokeToken(oldSession.tokenIdentifier(), oldSession.expiresAt());
		verify(sessionRegistry).registerSession(any(UserSession.class));
	}

	@Test
	@DisplayName("Should handle logout: revoking token and session, publishing SESSION_REVOKED event")
	void shouldLogoutSuccessfully() {
		Instant now = Instant.now();
		UserId userId = UserId.generate();
		UserSession session = new UserSession(SessionId.generate(), userId, null, "jti-logout", now, now.plusSeconds(900), now, "1.1.1.1", "curl", false);

		when(sessionRegistry.findByTokenIdentifier("jti-logout")).thenReturn(Optional.of(session));

		sessionService.logout("jti-logout", 900L);

		verify(tokenRevocationPort).revokeToken(eq("jti-logout"), any(Instant.class));
		verify(sessionRegistry).revokeSession(session.id());
		verify(auditRecorder).publish(argThat(event ->
				event.eventType().equals(IamEventTypes.SESSION_REVOKED) &&
						"jti-logout".equals(((Map<?, ?>) event.payload()).get("tokenIdentifier"))));
	}

	@Test
	@DisplayName("Should ignore logout when tokenIdentifier is null or blank")
	void shouldIgnoreBlankLogout() {
		sessionService.logout(null, 900L);
		sessionService.logout("   ", 900L);

		verify(tokenRevocationPort, never()).revokeToken(any(), any());
		verify(sessionRegistry, never()).revokeSession(any());
	}

	@Test
	@DisplayName("Should handle logoutAll: revoking user tokens and all active sessions, publishing SESSIONS_REVOKED_ALL event")
	void shouldLogoutAllSuccessfully() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		UserSession session1 = new UserSession(SessionId.generate(), userId, tenantId, "jti-1", now, now.plusSeconds(900), now, null, null, false);
		UserSession session2 = new UserSession(SessionId.generate(), userId, tenantId, "jti-2", now, now.plusSeconds(900), now, null, null, false);

		when(sessionRegistry.findActiveSessions(userId, tenantId)).thenReturn(List.of(session1, session2));

		sessionService.logoutAll(userId, tenantId);

		verify(tokenRevocationPort).revokeAllForUser(eq(userId), any(Instant.class));
		verify(sessionRegistry).revokeSession(session1.id());
		verify(sessionRegistry).revokeSession(session2.id());
		verify(tokenRevocationPort).revokeToken("jti-1", session1.expiresAt());
		verify(tokenRevocationPort).revokeToken("jti-2", session2.expiresAt());
		verify(auditRecorder).publish(argThat(event ->
				event.eventType().equals(IamEventTypes.SESSIONS_REVOKED_ALL) &&
						userId.value().equals(event.entityId())));
	}

	@Test
	@DisplayName("Should query active sessions and check token revocation")
	void shouldQuerySessionsAndTokenRevocation() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		UserSession session = new UserSession(SessionId.generate(), userId, tenantId, "jti-active", Instant.now(), Instant.now().plusSeconds(900), Instant.now(), null, null, false);

		when(sessionRegistry.findActiveSessions(userId, tenantId)).thenReturn(List.of(session));
		when(tokenRevocationPort.isTokenRevoked("jti-active")).thenReturn(true);
		when(tokenRevocationPort.isTokenRevoked("jti-unknown")).thenReturn(false);

		List<UserSession> activeSessions = sessionService.getActiveSessions(userId, tenantId);
		assertThat(activeSessions).containsExactly(session);

		assertThat(sessionService.isTokenRevoked("jti-active")).isTrue();
		assertThat(sessionService.isTokenRevoked("jti-unknown")).isFalse();
		assertThat(sessionService.isTokenRevoked(null)).isFalse();

		Instant expiry = Instant.now().plusSeconds(60);
		sessionService.revokeToken("jti-revoke", expiry);
		verify(tokenRevocationPort).revokeToken("jti-revoke", expiry);
	}

	@Test
	@DisplayName("Should safely handle null registry and null revocation port")
	void shouldHandleNullCollaborators() {
		AuthSessionService emptyService = new AuthSessionService(null, null);

		UserId userId = UserId.generate();
		emptyService.registerSession(userId, null, "tok", 100, null, null);
		emptyService.logout("tok", 100);
		emptyService.logoutAll(userId, null);
		assertThat(emptyService.getActiveSessions(userId, null)).isEmpty();
		assertThat(emptyService.isTokenRevoked("tok")).isFalse();
		emptyService.revokeToken("tok", Instant.now());
	}
}
