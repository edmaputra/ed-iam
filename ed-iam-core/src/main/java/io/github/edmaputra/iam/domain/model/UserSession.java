package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Domain record representing an active or revoked user authentication session.
 *
 * @param id              unique session identifier
 * @param userId          the user ID to whom this session belongs
 * @param tenantId        optional tenant ID context
 * @param tokenIdentifier the JWT ID (jti) or token thumbprint bound to this session
 * @param createdAt       timestamp when session was created
 * @param expiresAt       timestamp when session expires
 * @param lastAccessAt    timestamp of most recent access
 * @param ipAddress       client IP address if available
 * @param userAgent       client user-agent header if available
 * @param revoked         flag indicating whether the session has been invalidated
 * @author edmaputra
 * @since 0.3.0
 */
public record UserSession(
		SessionId id,
		UserId userId,
		TenantId tenantId,
		String tokenIdentifier,
		Instant createdAt,
		Instant expiresAt,
		Instant lastAccessAt,
		String ipAddress,
		String userAgent,
		boolean revoked) {

	public UserSession {
		Objects.requireNonNull(id, "Session ID must not be null.");
		Objects.requireNonNull(userId, "User ID must not be null.");
		Objects.requireNonNull(tokenIdentifier, "Token identifier must not be null.");
		Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		Objects.requireNonNull(expiresAt, "ExpiresAt must not be null.");
		Objects.requireNonNull(lastAccessAt, "LastAccessAt must not be null.");
	}

	/**
	 * Creates a new active session.
	 *
	 * @param userId          the user ID
	 * @param tenantId        optional tenant ID
	 * @param tokenIdentifier the JWT jti
	 * @param ttlSeconds      session time to live in seconds
	 * @param ipAddress       client IP
	 * @param userAgent       client user agent
	 * @return new {@link UserSession}
	 */
	public static UserSession create(
			UserId userId,
			TenantId tenantId,
			String tokenIdentifier,
			long ttlSeconds,
			String ipAddress,
			String userAgent) {
		Instant now = Instant.now();
		Instant expires = now.plusSeconds(ttlSeconds);
		return new UserSession(
				SessionId.generate(),
				userId,
				tenantId,
				tokenIdentifier,
				now,
				expires,
				now,
				ipAddress,
				userAgent,
				false);
	}

	/**
	 * Checks whether the session is expired relative to the given instant.
	 *
	 * @param now the current time
	 * @return true if expired
	 */
	public boolean isExpired(Instant now) {
		return expiresAt.isBefore(now);
	}

	/**
	 * Checks whether the session is active (not revoked and not expired).
	 *
	 * @param now the current time
	 * @return true if active
	 */
	public boolean isActive(Instant now) {
		return !revoked && !isExpired(now);
	}

	/**
	 * Returns a copy of this session marked as revoked.
	 *
	 * @return revoked {@link UserSession}
	 */
	public UserSession withRevoked() {
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
				true);
	}

	/**
	 * Returns a copy of this session with updated last access timestamp.
	 *
	 * @param now the updated last access instant
	 * @return touched {@link UserSession}
	 */
	public UserSession touch(Instant now) {
		return new UserSession(
				id,
				userId,
				tenantId,
				tokenIdentifier,
				createdAt,
				expiresAt,
				Objects.requireNonNull(now, "Touch instant must not be null."),
				ipAddress,
				userAgent,
				revoked);
	}
}
