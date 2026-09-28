package io.github.edmaputra.iam.adapter.security.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for in-memory session and security stores:
 * {@link InMemoryTokenRevocationStore}, {@link InMemorySessionRegistry}, and {@link InMemoryLoginAttemptTracker}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class InMemorySessionStoresTest {

	@Nested
	@DisplayName("InMemoryTokenRevocationStore Tests")
	class TokenRevocationTests {

		private final InMemoryTokenRevocationStore store = new InMemoryTokenRevocationStore();

		@Test
		@DisplayName("Should track revoked token until expiration")
		void shouldTrackRevokedToken() {
			String tokenId = "jti-12345";
			assertThat(store.isTokenRevoked(tokenId)).isFalse();

			store.revokeToken(tokenId, Instant.now().plusSeconds(300));
			assertThat(store.isTokenRevoked(tokenId)).isTrue();
		}

		@Test
		@DisplayName("Should expire revoked token after duration")
		void shouldExpireRevokedToken() {
			String tokenId = "jti-expired";
			store.revokeToken(tokenId, Instant.now().minusSeconds(10));

			assertThat(store.isTokenRevoked(tokenId)).isFalse();
		}

		@Test
		@DisplayName("Should track user revocation cutoff")
		void shouldTrackUserRevocationCutoff() {
			UserId userId = UserId.generate();
			Instant cutoff = Instant.now();

			assertThat(store.isUserRevoked(userId, cutoff.minusSeconds(10))).isFalse();

			store.revokeAllForUser(userId, cutoff);

			assertThat(store.isUserRevoked(userId, cutoff.minusSeconds(10))).isTrue();
			assertThat(store.isUserRevoked(userId, cutoff)).isTrue();
			assertThat(store.isUserRevoked(userId, cutoff.plusSeconds(10))).isFalse();
		}
	}

	@Nested
	@DisplayName("InMemorySessionRegistry Tests")
	class SessionRegistryTests {

		private final InMemorySessionRegistry registry = new InMemorySessionRegistry();

		@Test
		@DisplayName("Should register, find, touch, and revoke user sessions")
		void shouldManageSessionLifecycle() {
			UserId userId = UserId.generate();
			TenantId tenantId = new TenantId(UUID.randomUUID());
			UserSession session = UserSession.create(userId, tenantId, "jti-reg-1", 3600, "127.0.0.1", "Mozilla/5.0");

			registry.registerSession(session);

			Optional<UserSession> found = registry.findSession(session.id());
			assertThat(found).isPresent();
			assertThat(found.get().tokenIdentifier()).isEqualTo("jti-reg-1");

			Optional<UserSession> byToken = registry.findByTokenIdentifier("jti-reg-1");
			assertThat(byToken).isPresent();
			assertThat(byToken.get().id()).isEqualTo(session.id());

			List<UserSession> active = registry.findActiveSessions(userId, tenantId);
			assertThat(active).hasSize(1);
			assertThat(registry.countActiveSessions(userId, tenantId)).isEqualTo(1);

			Instant touchTime = Instant.now().plusSeconds(60);
			registry.touchSession(session.id(), touchTime);
			assertThat(registry.findSession(session.id()).get().lastAccessAt()).isEqualTo(touchTime);

			registry.revokeSession(session.id());
			assertThat(registry.findSession(session.id()).get().revoked()).isTrue();
			assertThat(registry.findActiveSessions(userId, tenantId)).isEmpty();
		}

		@Test
		@DisplayName("Should revoke all sessions for a user")
		void shouldRevokeAllSessionsForUser() {
			UserId userId = UserId.generate();
			UserSession s1 = UserSession.create(userId, null, "jti-a", 3600, "1.1.1.1", "App1");
			UserSession s2 = UserSession.create(userId, null, "jti-b", 3600, "2.2.2.2", "App2");

			registry.registerSession(s1);
			registry.registerSession(s2);

			assertThat(registry.findActiveSessions(userId, null)).hasSize(2);

			registry.revokeAllSessions(userId, null);

			assertThat(registry.findActiveSessions(userId, null)).isEmpty();
		}
	}

	@Nested
	@DisplayName("InMemoryLoginAttemptTracker Tests")
	class LoginAttemptTrackerTests {

		private final SessionProperties properties = new SessionProperties(
				5,
				SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST,
				3,
				300L);
		private final InMemoryLoginAttemptTracker tracker = new InMemoryLoginAttemptTracker(properties);

		@Test
		@DisplayName("Should increment attempts and lock account when threshold is reached")
		void shouldLockAccountOnThreshold() {
			String email = "victim@test.org";

			LockoutStatus s1 = tracker.getLockoutStatus(email);
			assertThat(s1.locked()).isFalse();
			assertThat(s1.failedAttempts()).isZero();

			tracker.recordFailedAttempt(email, Instant.now());
			assertThat(tracker.getLockoutStatus(email).failedAttempts()).isEqualTo(1);
			assertThat(tracker.getLockoutStatus(email).locked()).isFalse();

			tracker.recordFailedAttempt(email, Instant.now());
			assertThat(tracker.getLockoutStatus(email).failedAttempts()).isEqualTo(2);

			tracker.recordFailedAttempt(email, Instant.now());
			LockoutStatus locked = tracker.getLockoutStatus(email);
			assertThat(locked.locked()).isTrue();
			assertThat(locked.failedAttempts()).isEqualTo(3);
			assertThat(locked.lockedUntil()).isNotNull();

			// Unlock
			tracker.unlock(email);
			assertThat(tracker.getLockoutStatus(email).locked()).isFalse();
			assertThat(tracker.getLockoutStatus(email).failedAttempts()).isZero();
		}

		@Test
		@DisplayName("Should clear attempts on successful login")
		void shouldClearOnSuccess() {
			String email = "user@test.org";
			tracker.recordFailedAttempt(email, Instant.now());
			tracker.recordFailedAttempt(email, Instant.now());
			assertThat(tracker.getLockoutStatus(email).failedAttempts()).isEqualTo(2);

			tracker.recordSuccessfulAttempt(email);
			assertThat(tracker.getLockoutStatus(email).failedAttempts()).isZero();
		}
	}
}
