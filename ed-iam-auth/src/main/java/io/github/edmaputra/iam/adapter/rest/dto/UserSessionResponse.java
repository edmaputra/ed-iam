package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.edmaputra.iam.domain.model.UserSession;

/**
 * REST response DTO representing an authenticated session.
 *
 * @param sessionId       the unique session UUID
 * @param userId          the user UUID
 * @param tenantId        the tenant UUID (null for global)
 * @param tokenIdentifier the JWT ID (jti)
 * @param createdAt       timestamp when session was established
 * @param expiresAt       timestamp when session expires
 * @param lastAccessAt    timestamp of most recent access
 * @param ipAddress       client IP address
 * @param userAgent       client user agent string
 * @param revoked         flag indicating whether session is revoked
 * @param current         flag indicating if this session matches the calling client's token
 * @author edmaputra
 * @since 0.3.0
 */
public record UserSessionResponse(
		UUID sessionId,
		UUID userId,
		UUID tenantId,
		String tokenIdentifier,
		Instant createdAt,
		Instant expiresAt,
		Instant lastAccessAt,
		String ipAddress,
		String userAgent,
		boolean revoked,
		boolean current) {

	/**
	 * Maps domain {@link UserSession} to response DTO without current token matching.
	 *
	 * @param session the user session
	 * @return new {@link UserSessionResponse}
	 */
	public static UserSessionResponse from(UserSession session) {
		return from(session, null);
	}

	/**
	 * Maps domain {@link UserSession} to response DTO, marking whether it matches the caller's token.
	 *
	 * @param session        the user session
	 * @param currentTokenId the caller's active token identifier
	 * @return new {@link UserSessionResponse}
	 */
	public static UserSessionResponse from(UserSession session, String currentTokenId) {
		boolean isCurrent = currentTokenId != null && currentTokenId.equals(session.tokenIdentifier());
		return new UserSessionResponse(
				session.id().value(),
				session.userId().value(),
				session.tenantId() == null ? null : session.tenantId().value(),
				session.tokenIdentifier(),
				session.createdAt(),
				session.expiresAt(),
				session.lastAccessAt(),
				session.ipAddress(),
				session.userAgent(),
				session.revoked(),
				isCurrent);
	}
}
