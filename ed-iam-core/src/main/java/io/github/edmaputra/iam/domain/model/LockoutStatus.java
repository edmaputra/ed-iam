package io.github.edmaputra.iam.domain.model;

import java.time.Instant;

/**
 * Domain record describing the brute-force lockout status for a user or client identity.
 *
 * @param locked         true if the account or client is currently locked
 * @param failedAttempts the consecutive failed attempt count
 * @param lockedUntil    the expiration timestamp of the lockout (null if not locked)
 * @author edmaputra
 * @since 0.3.0
 */
public record LockoutStatus(
		boolean locked,
		int failedAttempts,
		Instant lockedUntil) {

	/**
	 * Creates an unlocked status with the given failed attempt count.
	 *
	 * @param failedAttempts current count of consecutive failed attempts
	 * @return unlocked {@link LockoutStatus}
	 */
	public static LockoutStatus unlocked(int failedAttempts) {
		return new LockoutStatus(false, failedAttempts, null);
	}

	/**
	 * Creates a locked status with the given failed attempts and unlock timestamp.
	 *
	 * @param failedAttempts total failed attempts triggering the lockout
	 * @param lockedUntil    instant until which access is denied
	 * @return locked {@link LockoutStatus}
	 */
	public static LockoutStatus locked(int failedAttempts, Instant lockedUntil) {
		return new LockoutStatus(true, failedAttempts, lockedUntil);
	}
}
