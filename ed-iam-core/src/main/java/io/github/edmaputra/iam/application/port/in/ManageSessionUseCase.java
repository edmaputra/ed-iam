package io.github.edmaputra.iam.application.port.in;

import java.util.List;

import io.github.edmaputra.iam.domain.model.SessionId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound port for managing, listing, and revoking user sessions.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public interface ManageSessionUseCase {

	/**
	 * Lists all active sessions for a user within an optional tenant context.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID
	 * @return list of active sessions
	 */
	List<UserSession> listUserSessions(UserId userId, TenantId tenantId);

	/**
	 * Terminates and invalidates a specific user session.
	 *
	 * @param userId    the user ID owning the session
	 * @param sessionId the session to terminate
	 */
	void terminateSession(UserId userId, SessionId sessionId);

	/**
	 * Terminates and invalidates all active sessions for a user.
	 *
	 * @param userId   the user ID
	 * @param tenantId optional tenant ID (if null, revokes across all tenants)
	 */
	void terminateAllUserSessions(UserId userId, TenantId tenantId);
}
