package io.github.edmaputra.iam.application.port.out;

import java.time.Instant;

import io.github.edmaputra.iam.domain.model.LockoutStatus;

/**
 * Outbound SPI port for tracking authentication attempts, rate limiting, and brute-force lockout status.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public interface LoginAttemptTrackerPort {

	/**
	 * Records a failed authentication attempt for a target key (e.g. username/email or IP address).
	 *
	 * @param key       the identity or IP key
	 * @param timestamp timestamp when the failure occurred
	 */
	void recordFailedAttempt(String key, Instant timestamp);

	/**
	 * Records a successful authentication, resetting failed attempts and lockout timers for the key.
	 *
	 * @param key the identity or IP key
	 */
	void recordSuccessfulAttempt(String key);

	/**
	 * Checks the current lockout status for the given key.
	 *
	 * @param key the identity or IP key
	 * @return current {@link LockoutStatus}
	 */
	LockoutStatus getLockoutStatus(String key);

	/**
	 * Clears any lockouts and failed attempt counters for the given key.
	 *
	 * @param key the identity or IP key
	 */
	void unlock(String key);
}
