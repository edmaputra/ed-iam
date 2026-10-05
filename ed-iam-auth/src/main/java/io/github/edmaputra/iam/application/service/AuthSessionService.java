package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for user session tracking, concurrency limit enforcement,
 * and token revocation.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class AuthSessionService {

	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final SessionProperties sessionProperties;
	private final SecurityAuditRecorder auditRecorder;

	public AuthSessionService(
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			SessionProperties sessionProperties,
			SecurityAuditRecorder auditRecorder) {
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.sessionProperties = sessionProperties != null ? sessionProperties : SessionProperties.defaultProperties();
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	public AuthSessionService(
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort) {
		this(sessionRegistry, tokenRevocationPort, SessionProperties.defaultProperties(), null);
	}

	public void registerSession(
			UserId userId,
			TenantId tenantId,
			String tokenId,
			long ttl,
			String ipAddress,
			String userAgent) {
		if (sessionRegistry == null) {
			return;
		}

		int maxConcurrent = sessionProperties.maxConcurrentSessions();
		if (maxConcurrent > 0) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, tenantId);
			if (activeSessions.size() >= maxConcurrent) {
				if (sessionProperties.sessionLimitStrategy() == SessionProperties.SessionLimitStrategy.REJECT_NEW) {
					throw new AuthenticationException("Maximum concurrent active sessions (" + maxConcurrent + ") exceeded.");
				}
				else {
					int excess = activeSessions.size() - maxConcurrent + 1;
					activeSessions.stream()
							.sorted(Comparator.comparing(UserSession::createdAt))
							.limit(excess)
							.forEach(oldSession -> {
								sessionRegistry.revokeSession(oldSession.id());
								if (tokenRevocationPort != null && oldSession.tokenIdentifier() != null) {
									tokenRevocationPort.revokeToken(oldSession.tokenIdentifier(), oldSession.expiresAt());
								}
							});
				}
			}
		}

		UserSession session = UserSession.create(
				userId,
				tenantId,
				tokenId,
				ttl,
				ipAddress,
				userAgent);
		sessionRegistry.registerSession(session);
	}

	public void logout(String tokenIdentifier, long expirationSeconds) {
		if (tokenIdentifier == null || tokenIdentifier.isBlank()) {
			return;
		}
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeToken(tokenIdentifier, Instant.now().plusSeconds(expirationSeconds));
		}
		if (sessionRegistry != null) {
			sessionRegistry.findByTokenIdentifier(tokenIdentifier)
					.ifPresent(s -> sessionRegistry.revokeSession(s.id()));
		}
		auditRecorder.publish(IamEvent.of(
				IamEventTypes.SESSION_REVOKED,
				null,
				new UUID(0L, 0L),
				"SESSION",
				Map.of("tokenIdentifier", tokenIdentifier),
				"anonymous"));
	}

	public void logoutAll(UserId userId, TenantId tenantId) {
		Instant now = Instant.now();
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, now);
		}
		if (sessionRegistry != null) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, tenantId);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
		auditRecorder.publish(IamEvent.of(
				IamEventTypes.SESSIONS_REVOKED_ALL,
				tenantId != null ? tenantId.value() : null,
				userId.value(),
				"SESSION",
				Map.of("userId", userId.value().toString()),
				userId.toString()));
	}

	public List<UserSession> getActiveSessions(UserId userId, TenantId tenantId) {
		if (sessionRegistry != null) {
			return sessionRegistry.findActiveSessions(userId, tenantId);
		}
		return List.of();
	}

	public boolean isTokenRevoked(String tokenId) {
		return tokenRevocationPort != null && tokenId != null && tokenRevocationPort.isTokenRevoked(tokenId);
	}

	public void revokeToken(String tokenId, Instant expiresAt) {
		if (tokenRevocationPort != null && tokenId != null) {
			tokenRevocationPort.revokeToken(tokenId, expiresAt);
		}
	}
}
