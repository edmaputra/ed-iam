package io.github.edmaputra.iam.domain.exception;

import java.time.Instant;

/**
 * Thrown when an authentication attempt is rejected because the user account or IP is temporarily locked
 * due to exceeding maximum consecutive failed login attempts.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class AccountLockedException extends AuthenticationException {

	private final Instant lockedUntil;

	/**
	 * Constructs the exception with a message and unlock instant.
	 *
	 * @param message     the detail message
	 * @param lockedUntil the timestamp until which the account is locked
	 */
	public AccountLockedException(String message, Instant lockedUntil) {
		super(message);
		this.lockedUntil = lockedUntil;
	}

	/**
	 * Returns the timestamp until which the account is locked.
	 *
	 * @return unlock timestamp, or null if indefinite
	 */
	public Instant getLockedUntil() {
		return lockedUntil;
	}
}
