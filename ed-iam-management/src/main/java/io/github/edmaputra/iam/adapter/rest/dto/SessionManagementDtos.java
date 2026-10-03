package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.UserSession;

/**
 * Request and response DTOs for administrative session and lockout management.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public final class SessionManagementDtos {

	private SessionManagementDtos() {}

	/**
	 * Response DTO describing active or historical user session details.
	 *
	 * @param sessionId       the unique session UUID
	 * @param userId          the user UUID
	 * @param tenantId        the tenant UUID (null for global)
	 * @param tokenIdentifier the JWT ID (jti)
	 * @param createdAt       creation timestamp
	 * @param expiresAt       expiration timestamp
	 * @param lastAccessAt    last access timestamp
	 * @param ipAddress       client IP address
	 * @param userAgent       client user agent string
	 * @param revoked         flag indicating whether the session has been terminated
	 */
	public record UserSessionDetailResponse(
			UUID sessionId,
			UUID userId,
			UUID tenantId,
			String tokenIdentifier,
			Instant createdAt,
			Instant expiresAt,
			Instant lastAccessAt,
			String ipAddress,
			String userAgent,
			boolean revoked) {

		public static UserSessionDetailResponse from(UserSession session) {
			return new UserSessionDetailResponse(
					session.id().value(),
					session.userId().value(),
					session.tenantId() == null ? null : session.tenantId().value(),
					session.tokenIdentifier(),
					session.createdAt(),
					session.expiresAt(),
					session.lastAccessAt(),
					session.ipAddress(),
					session.userAgent(),
					session.revoked());
		}
	}

	/**
	 * Response DTO describing user brute-force lockout status.
	 *
	 * @param locked         true if user account is locked
	 * @param failedAttempts count of consecutive failed attempts
	 * @param lockedUntil    expiration instant of lockout, or null if not locked
	 */
	public record UserLockoutResponse(
			boolean locked,
			int failedAttempts,
			Instant lockedUntil) {

		public static UserLockoutResponse from(LockoutStatus status) {
			if (status == null) {
				return new UserLockoutResponse(false, 0, null);
			}
			return new UserLockoutResponse(status.locked(), status.failedAttempts(), status.lockedUntil());
		}
	}
}
