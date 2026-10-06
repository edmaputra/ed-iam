package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;

import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;

/**
 * Focused application service responsible for terminating active sessions and revoking tokens for user accounts.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserSessionRevocationService {

	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;

	public UserSessionRevocationService(SessionRegistryPort sessionRegistry, TokenRevocationPort tokenRevocationPort) {
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
	}

	/**
	 * Revokes all active sessions and blacklists active tokens for the given user.
	 *
	 * @param userId the user ID whose sessions should be revoked
	 */
	public void revokeAllUserSessions(UserId userId) {
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, Instant.now());
		}
		if (sessionRegistry != null) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, null);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
	}
}
