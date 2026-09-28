package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionManagementService}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class SessionManagementServiceTest {

	private UserRepository userRepository;
	private SessionRegistryPort sessionRegistry;
	private TokenRevocationPort tokenRevocationPort;
	private LoginAttemptTrackerPort loginAttemptTracker;
	private SessionManagementService service;

	private UserId userId;
	private User user;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		sessionRegistry = mock(SessionRegistryPort.class);
		tokenRevocationPort = mock(TokenRevocationPort.class);
		loginAttemptTracker = mock(LoginAttemptTrackerPort.class);

		service = new SessionManagementService(userRepository, sessionRegistry, tokenRevocationPort, loginAttemptTracker);

		user = User.create("admin@test.org", "hash", "Admin User", true);
		userId = user.getId();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
	}

	@Test
	@DisplayName("Should list active sessions for user")
	void shouldListUserSessions() {
		UserSession s1 = UserSession.create(userId, null, "tok-1", 3600, "127.0.0.1", "curl");
		when(sessionRegistry.findActiveSessions(userId, null)).thenReturn(List.of(s1));

		List<UserSession> result = service.listUserSessions(userId, null);

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().tokenIdentifier()).isEqualTo("tok-1");
		verify(sessionRegistry).findActiveSessions(userId, null);
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when listing sessions for non-existent user")
	void shouldThrowWhenListingForMissingUser() {
		UserId missing = UserId.generate();
		when(userRepository.findById(missing)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.listUserSessions(missing, null))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should terminate session and revoke associated token")
	void shouldTerminateSession() {
		UserSession session = UserSession.create(userId, null, "tok-abc", 1800, "10.0.0.1", "Safari");
		SessionId sessionId = session.id();

		when(sessionRegistry.findSession(sessionId)).thenReturn(Optional.of(session));

		service.terminateSession(userId, sessionId);

		verify(sessionRegistry).revokeSession(sessionId);
		verify(tokenRevocationPort).revokeToken(eq("tok-abc"), any(Instant.class));
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when session belongs to a different user")
	void shouldThrowWhenSessionBelongsToDifferentUser() {
		UserId otherUser = UserId.generate();
		UserSession session = UserSession.create(otherUser, null, "tok-other", 1800, "10.0.0.1", "Safari");
		SessionId sessionId = session.id();

		when(sessionRegistry.findSession(sessionId)).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> service.terminateSession(userId, sessionId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not belong to user");
	}

	@Test
	@DisplayName("Should terminate all sessions and revoke all tokens for user")
	void shouldTerminateAllUserSessions() {
		UserSession session1 = UserSession.create(userId, null, "tok-1", 1800, "10.0.0.1", "Chrome");
		UserSession session2 = UserSession.create(userId, null, "tok-2", 1800, "10.0.0.2", "Firefox");

		when(sessionRegistry.findActiveSessions(userId, null)).thenReturn(List.of(session1, session2));

		service.terminateAllUserSessions(userId, null);

		verify(tokenRevocationPort).revokeAllForUser(eq(userId), any(Instant.class));
		verify(sessionRegistry).revokeSession(session1.id());
		verify(sessionRegistry).revokeSession(session2.id());
		verify(tokenRevocationPort).revokeToken(eq("tok-1"), any(Instant.class));
		verify(tokenRevocationPort).revokeToken(eq("tok-2"), any(Instant.class));
	}

	@Test
	@DisplayName("Should retrieve lockout status for user email")
	void shouldGetLockoutStatus() {
		LockoutStatus status = LockoutStatus.locked(5, Instant.now().plusSeconds(600));
		when(loginAttemptTracker.getLockoutStatus(user.getEmail())).thenReturn(status);

		LockoutStatus result = service.getLockoutStatus(userId);

		assertThat(result.locked()).isTrue();
		assertThat(result.failedAttempts()).isEqualTo(5);
		verify(loginAttemptTracker).getLockoutStatus("admin@test.org");
	}

	@Test
	@DisplayName("Should unlock user email")
	void shouldUnlockUser() {
		service.unlockUser(userId);

		verify(loginAttemptTracker).unlock("admin@test.org");
	}
}
