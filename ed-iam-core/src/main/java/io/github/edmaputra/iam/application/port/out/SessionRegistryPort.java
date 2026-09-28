package io.github.edmaputra.iam.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound SPI port for session tracking, concurrency control, and lifecycle persistence.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public interface SessionRegistryPort {

	/**
	 * Registers a newly created user session.
	 *
	 * @param session the user session to store
	 */
	void registerSession(UserSession session);

	/**
	 * Finds a session by its unique session ID.
	 *
	 * @param sessionId the session ID
	 * @return optional containing the session if found
	 */
	Optional<UserSession> findSession(SessionId sessionId);

	/**
	 * Finds a session by its bound token identifier (jti).
	 *
	 * @param tokenIdentifier the token jti
	 * @return optional containing the session if found
	 */
	Optional<UserSession> findByTokenIdentifier(String tokenIdentifier);

	/**
	 * Retrieves all active (non-revoked and non-expired) sessions for a user within an optional tenant context.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID
	 * @return list of active user sessions
	 */
	List<UserSession> findActiveSessions(UserId userId, TenantId tenantId);

	/**
	 * Marks a specific session as revoked.
	 *
	 * @param sessionId the session ID
	 */
	void revokeSession(SessionId sessionId);

	/**
	 * Marks all active sessions for a user as revoked.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID (if null, revokes across all tenants)
	 */
	void revokeAllSessions(UserId userId, TenantId tenantId);

	/**
	 * Updates the last access timestamp for the specified session.
	 *
	 * @param sessionId    the session ID
	 * @param lastAccessAt the last access instant
	 */
	void touchSession(SessionId sessionId, Instant lastAccessAt);

	/**
	 * Counts active sessions for a user within an optional tenant context.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID
	 * @return count of active sessions
	 */
	int countActiveSessions(UserId userId, TenantId tenantId);
}
