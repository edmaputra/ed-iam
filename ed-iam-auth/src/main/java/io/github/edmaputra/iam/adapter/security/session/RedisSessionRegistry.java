package io.github.edmaputra.iam.adapter.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Distributed, Redis-backed implementation of {@link SessionRegistryPort}.
 * Stores session records in Redis hashes and maintains user session index sets
 * for cluster-wide concurrency control and administration.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class RedisSessionRegistry implements SessionRegistryPort {

	private static final String KEY_PREFIX_SESSION = "iam:session:";
	private static final String KEY_PREFIX_USER_SESSIONS = "iam:user:sessions:";
	private static final String KEY_PREFIX_TOKEN_SESSION = "iam:token:session:";

	private final StringRedisTemplate redisTemplate;

	public RedisSessionRegistry(StringRedisTemplate redisTemplate) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null.");
	}

	@Override
	public void registerSession(UserSession session) {
		Objects.requireNonNull(session, "UserSession must not be null.");

		String sessionKey = KEY_PREFIX_SESSION + session.id().value();
		Map<String, String> hash = new HashMap<>();
		hash.put("id", session.id().value().toString());
		hash.put("userId", session.userId().value().toString());
		hash.put("tenantId", session.tenantId() != null ? session.tenantId().value().toString() : "");
		hash.put("tokenIdentifier", session.tokenIdentifier());
		hash.put("createdAt", String.valueOf(session.createdAt().toEpochMilli()));
		hash.put("expiresAt", String.valueOf(session.expiresAt().toEpochMilli()));
		hash.put("lastAccessAt", String.valueOf(session.lastAccessAt().toEpochMilli()));
		hash.put("ipAddress", session.ipAddress() != null ? session.ipAddress() : "");
		hash.put("userAgent", session.userAgent() != null ? session.userAgent() : "");
		hash.put("revoked", String.valueOf(session.revoked()));

		redisTemplate.opsForHash().putAll(sessionKey, hash);

		Duration ttl = Duration.between(Instant.now(), session.expiresAt());
		if (ttl.isPositive()) {
			redisTemplate.expire(sessionKey, ttl);
		} else {
			redisTemplate.expire(sessionKey, Duration.ofSeconds(1));
		}

		String userSessionsKey = KEY_PREFIX_USER_SESSIONS + session.userId().value();
		redisTemplate.opsForSet().add(userSessionsKey, session.id().value().toString());

		String tokenSessionKey = KEY_PREFIX_TOKEN_SESSION + session.tokenIdentifier();
		redisTemplate.opsForValue().set(tokenSessionKey, session.id().value().toString(), ttl.isPositive() ? ttl : Duration.ofSeconds(1));
	}

	@Override
	public Optional<UserSession> findSession(SessionId sessionId) {
		if (sessionId == null) {
			return Optional.empty();
		}
		String sessionKey = KEY_PREFIX_SESSION + sessionId.value();
		Map<Object, Object> raw = redisTemplate.opsForHash().entries(sessionKey);
		if (raw == null || raw.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(mapToUserSession(raw));
	}

	@Override
	public Optional<UserSession> findByTokenIdentifier(String tokenIdentifier) {
		if (tokenIdentifier == null || tokenIdentifier.isBlank()) {
			return Optional.empty();
		}
		String sessionIdStr = redisTemplate.opsForValue().get(KEY_PREFIX_TOKEN_SESSION + tokenIdentifier);
		if (sessionIdStr == null || sessionIdStr.isBlank()) {
			return Optional.empty();
		}
		try {
			return findSession(new SessionId(UUID.fromString(sessionIdStr)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	@Override
	public List<UserSession> findActiveSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");

		String userSessionsKey = KEY_PREFIX_USER_SESSIONS + userId.value();
		Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
		if (sessionIds == null || sessionIds.isEmpty()) {
			return List.of();
		}

		Instant now = Instant.now();
		List<UserSession> activeSessions = new ArrayList<>();

		for (String idStr : sessionIds) {
			try {
				UUID uuid = UUID.fromString(idStr);
				Optional<UserSession> sessionOpt = findSession(new SessionId(uuid));
				if (sessionOpt.isEmpty()) {
					// Clean up orphan set entry
					redisTemplate.opsForSet().remove(userSessionsKey, idStr);
					continue;
				}
				UserSession session = sessionOpt.get();
				if (!session.isActive(now)) {
					continue;
				}
				if (tenantId != null && session.tenantId() != null && !tenantId.equals(session.tenantId())) {
					continue;
				}
				activeSessions.add(session);
			} catch (IllegalArgumentException e) {
				redisTemplate.opsForSet().remove(userSessionsKey, idStr);
			}
		}

		activeSessions.sort(Comparator.comparing(UserSession::createdAt));
		return activeSessions;
	}

	@Override
	public void revokeSession(SessionId sessionId) {
		if (sessionId == null) {
			return;
		}
		String sessionKey = KEY_PREFIX_SESSION + sessionId.value();
		if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
			redisTemplate.opsForHash().put(sessionKey, "revoked", "true");
		}
	}

	@Override
	public void revokeAllSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		List<UserSession> activeSessions = findActiveSessions(userId, tenantId);
		for (UserSession s : activeSessions) {
			revokeSession(s.id());
		}
	}

	@Override
	public void touchSession(SessionId sessionId, Instant lastAccessAt) {
		if (sessionId == null || lastAccessAt == null) {
			return;
		}
		String sessionKey = KEY_PREFIX_SESSION + sessionId.value();
		if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
			redisTemplate.opsForHash().put(sessionKey, "lastAccessAt", String.valueOf(lastAccessAt.toEpochMilli()));
		}
	}

	@Override
	public int countActiveSessions(UserId userId, TenantId tenantId) {
		return findActiveSessions(userId, tenantId).size();
	}

	private UserSession mapToUserSession(Map<Object, Object> raw) {
		SessionId id = new SessionId(UUID.fromString((String) raw.get("id")));
		UserId userId = new UserId(UUID.fromString((String) raw.get("userId")));

		String tenantIdStr = (String) raw.get("tenantId");
		TenantId tenantId = tenantIdStr != null && !tenantIdStr.isBlank()
				? new TenantId(UUID.fromString(tenantIdStr))
				: null;

		String tokenIdentifier = (String) raw.get("tokenIdentifier");
		Instant createdAt = Instant.ofEpochMilli(Long.parseLong((String) raw.get("createdAt")));
		Instant expiresAt = Instant.ofEpochMilli(Long.parseLong((String) raw.get("expiresAt")));
		Instant lastAccessAt = Instant.ofEpochMilli(Long.parseLong((String) raw.get("lastAccessAt")));

		String ip = (String) raw.get("ipAddress");
		String ipAddress = ip != null && !ip.isBlank() ? ip : null;

		String ua = (String) raw.get("userAgent");
		String userAgent = ua != null && !ua.isBlank() ? ua : null;

		boolean revoked = Boolean.parseBoolean((String) raw.get("revoked"));

		return new UserSession(
				id,
				userId,
				tenantId,
				tokenIdentifier,
				createdAt,
				expiresAt,
				lastAccessAt,
				ipAddress,
				userAgent,
				revoked);
	}
}
