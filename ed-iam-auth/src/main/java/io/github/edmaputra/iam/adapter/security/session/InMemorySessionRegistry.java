package io.github.edmaputra.iam.adapter.security.session;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Thread-safe, in-memory implementation of {@link SessionRegistryPort}.
 * Suitable for local development, standalone single-instance setups, and unit/integration testing.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class InMemorySessionRegistry implements SessionRegistryPort {

	private final Map<SessionId, UserSession> sessions = new ConcurrentHashMap<>();

	@Override
	public void registerSession(UserSession session) {
		Objects.requireNonNull(session, "UserSession must not be null.");
		sessions.put(session.id(), session);
	}

	@Override
	public Optional<UserSession> findSession(SessionId sessionId) {
		if (sessionId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(sessions.get(sessionId));
	}

	@Override
	public Optional<UserSession> findByTokenIdentifier(String tokenIdentifier) {
		if (tokenIdentifier == null || tokenIdentifier.isBlank()) {
			return Optional.empty();
		}
		return sessions.values().stream()
				.filter(s -> tokenIdentifier.equals(s.tokenIdentifier()))
				.findFirst();
	}

	@Override
	public List<UserSession> findActiveSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Instant now = Instant.now();
		return sessions.values().stream()
				.filter(s -> s.userId().equals(userId))
				.filter(s -> tenantId == null || s.tenantId() == null || tenantId.equals(s.tenantId()))
				.filter(s -> s.isActive(now))
				.sorted(Comparator.comparing(UserSession::createdAt))
				.toList();
	}

	@Override
	public void revokeSession(SessionId sessionId) {
		if (sessionId == null) {
			return;
		}
		sessions.computeIfPresent(sessionId, (id, s) -> s.withRevoked());
	}

	@Override
	public void revokeAllSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		sessions.replaceAll((id, s) -> {
			if (s.userId().equals(userId) && (tenantId == null || tenantId.equals(s.tenantId()))) {
				return s.withRevoked();
			}
			return s;
		});
	}

	@Override
	public void touchSession(SessionId sessionId, Instant lastAccessAt) {
		if (sessionId == null || lastAccessAt == null) {
			return;
		}
		sessions.computeIfPresent(sessionId, (id, s) -> s.touch(lastAccessAt));
	}

	@Override
	public int countActiveSessions(UserId userId, TenantId tenantId) {
		return findActiveSessions(userId, tenantId).size();
	}

	/**
	 * Removes all expired and revoked sessions from memory.
	 */
	public void purgeInactive() {
		Instant now = Instant.now();
		sessions.entrySet().removeIf(e -> !e.getValue().isActive(now));
	}

	/**
	 * Clears all registered sessions.
	 */
	public void clear() {
		sessions.clear();
	}
}
