package io.github.edmaputra.iam.adapter.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Redis-backed session stores:
 * {@link RedisTokenRevocationStore}, {@link RedisSessionRegistry}, and {@link RedisLoginAttemptTracker}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class RedisSessionStoresTest {

	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOps;
	private SetOperations<String, String> setOps;
	private HashOperations<String, Object, Object> hashOps;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		valueOps = mock(ValueOperations.class);
		setOps = mock(SetOperations.class);
		hashOps = mock(HashOperations.class);

		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		when(redisTemplate.opsForSet()).thenReturn(setOps);
		when(redisTemplate.opsForHash()).thenReturn(hashOps);
	}

	@Test
	@DisplayName("RedisTokenRevocationStore should set key with TTL and check existence")
	void testRedisTokenRevocationStore() {
		RedisTokenRevocationStore store = new RedisTokenRevocationStore(redisTemplate);

		Instant expiry = Instant.now().plusSeconds(600);
		store.revokeToken("jti-redis-1", expiry);
		verify(valueOps).set(eq("iam:revoked:token:jti-redis-1"), eq("1"), any(Duration.class));

		when(redisTemplate.hasKey("iam:revoked:token:jti-redis-1")).thenReturn(true);
		assertThat(store.isTokenRevoked("jti-redis-1")).isTrue();

		UserId userId = UserId.generate();
		Instant cutoff = Instant.now();
		store.revokeAllForUser(userId, cutoff);
		verify(valueOps).set(eq("iam:revoked:user:" + userId.value()), eq(String.valueOf(cutoff.toEpochMilli())), eq(Duration.ofDays(1)));

		when(valueOps.get("iam:revoked:user:" + userId.value())).thenReturn(String.valueOf(cutoff.toEpochMilli()));
		assertThat(store.isUserRevoked(userId, cutoff.minusSeconds(5))).isTrue();
		assertThat(store.isUserRevoked(userId, cutoff.plusSeconds(5))).isFalse();
	}

	@Test
	@DisplayName("RedisSessionRegistry should store and retrieve sessions")
	void testRedisSessionRegistry() {
		RedisSessionRegistry registry = new RedisSessionRegistry(redisTemplate);

		UserId userId = UserId.generate();
		UserSession session = UserSession.create(userId, null, "jti-reg", 3600, "127.0.0.1", "Chrome");

		registry.registerSession(session);
		verify(hashOps).putAll(eq("iam:session:" + session.id().value()), any(Map.class));
		verify(setOps).add(eq("iam:user:sessions:" + userId.value()), eq(session.id().value().toString()));
		verify(valueOps).set(eq("iam:token:session:jti-reg"), eq(session.id().value().toString()), any(Duration.class));

		// Find session
		Map<Object, Object> rawHash = Map.of(
				"id", session.id().value().toString(),
				"userId", userId.value().toString(),
				"tenantId", "",
				"tokenIdentifier", "jti-reg",
				"createdAt", String.valueOf(session.createdAt().toEpochMilli()),
				"expiresAt", String.valueOf(session.expiresAt().toEpochMilli()),
				"lastAccessAt", String.valueOf(session.lastAccessAt().toEpochMilli()),
				"ipAddress", "127.0.0.1",
				"userAgent", "Chrome",
				"revoked", "false");

		when(hashOps.entries("iam:session:" + session.id().value())).thenReturn(rawHash);

		Optional<UserSession> found = registry.findSession(session.id());
		assertThat(found).isPresent();
		assertThat(found.get().tokenIdentifier()).isEqualTo("jti-reg");

		// Find active sessions
		when(setOps.members("iam:user:sessions:" + userId.value())).thenReturn(Set.of(session.id().value().toString()));
		assertThat(registry.findActiveSessions(userId, null)).hasSize(1);
	}

	@Test
	@DisplayName("RedisLoginAttemptTracker should track attempts and enforce lockout")
	void testRedisLoginAttemptTracker() {
		SessionProperties properties = new SessionProperties(5, SessionProperties.SessionLimitStrategy.TERMINATE_OLDEST, 3, 300L);
		RedisLoginAttemptTracker tracker = new RedisLoginAttemptTracker(redisTemplate, properties);

		when(valueOps.increment("iam:lockout:attempts:user@test.org")).thenReturn(1L);
		tracker.recordFailedAttempt("user@test.org", Instant.now());
		verify(redisTemplate).expire(eq("iam:lockout:attempts:user@test.org"), any(Duration.class));

		// Lockout condition reached
		when(valueOps.increment("iam:lockout:attempts:user@test.org")).thenReturn(3L);
		tracker.recordFailedAttempt("user@test.org", Instant.now());
		verify(valueOps).set(eq("iam:lockout:locked_until:user@test.org"), any(String.class), any(Duration.class));

		// Check status
		Instant lockedUntil = Instant.now().plusSeconds(300);
		when(valueOps.get("iam:lockout:locked_until:user@test.org")).thenReturn(String.valueOf(lockedUntil.toEpochMilli()));
		when(valueOps.get("iam:lockout:attempts:user@test.org")).thenReturn("3");

		LockoutStatus status = tracker.getLockoutStatus("user@test.org");
		assertThat(status.locked()).isTrue();
		assertThat(status.failedAttempts()).isEqualTo(3);

		// Unlock
		tracker.unlock("user@test.org");
		verify(redisTemplate).delete(any(java.util.List.class));
	}
}
