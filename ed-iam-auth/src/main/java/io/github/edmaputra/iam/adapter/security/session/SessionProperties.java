package io.github.edmaputra.iam.adapter.security.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for user session limits and brute-force lockout policies.
 *
 * @param maxConcurrentSessions   maximum active concurrent sessions per user account (0 for unlimited, default 5)
 * @param sessionLimitStrategy    strategy when limit is reached: TERMINATE_OLDEST or REJECT_NEW (default TERMINATE_OLDEST)
 * @param maxFailedLoginAttempts  maximum consecutive failed login attempts before account lockout (default 5)
 * @param lockoutDurationSeconds  duration in seconds an account remains locked (default 900s = 15 mins)
 * @author edmaputra
 * @since 0.3.0
 */
@ConfigurationProperties(prefix = "iam.security.session")
public record SessionProperties(
		int maxConcurrentSessions,
		SessionLimitStrategy sessionLimitStrategy,
		int maxFailedLoginAttempts,
		long lockoutDurationSeconds) {

	/** Default maximum concurrent active sessions per user account. */
	public static final int DEFAULT_MAX_CONCURRENT_SESSIONS = 5;
	/** Default concurrency limit strategy. */
	public static final SessionLimitStrategy DEFAULT_SESSION_LIMIT_STRATEGY = SessionLimitStrategy.TERMINATE_OLDEST;
	/** Default maximum failed login attempts before lockout. */
	public static final int DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS = 5;
	/** Default lockout duration in seconds (15 minutes). */
	public static final long DEFAULT_LOCKOUT_DURATION_SECONDS = 900L;

	public SessionProperties {
		if (maxConcurrentSessions < 0) {
			maxConcurrentSessions = DEFAULT_MAX_CONCURRENT_SESSIONS;
		}
		if (sessionLimitStrategy == null) {
			sessionLimitStrategy = DEFAULT_SESSION_LIMIT_STRATEGY;
		}
		if (maxFailedLoginAttempts <= 0) {
			maxFailedLoginAttempts = DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS;
		}
		if (lockoutDurationSeconds <= 0) {
			lockoutDurationSeconds = DEFAULT_LOCKOUT_DURATION_SECONDS;
		}
	}

	/**
	 * Creates default session configuration properties.
	 *
	 * @return default {@link SessionProperties}
	 */
	public static SessionProperties defaultProperties() {
		return new SessionProperties(
				DEFAULT_MAX_CONCURRENT_SESSIONS,
				DEFAULT_SESSION_LIMIT_STRATEGY,
				DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS,
				DEFAULT_LOCKOUT_DURATION_SECONDS);
	}

	/**
	 * Concurrency enforcement strategy when maximum session limit is reached.
	 */
	public enum SessionLimitStrategy {
		TERMINATE_OLDEST,
		REJECT_NEW
	}
}
