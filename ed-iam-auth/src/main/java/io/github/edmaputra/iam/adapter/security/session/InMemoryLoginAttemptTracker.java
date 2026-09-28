package io.github.edmaputra.iam.adapter.security.session;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.domain.model.LockoutStatus;

/**
 * Thread-safe, in-memory implementation of {@link LoginAttemptTrackerPort}.
 * Tracks failed authentication attempts per key (email, username, or IP) and applies
 * configurable brute-force lockout windows.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class InMemoryLoginAttemptTracker implements LoginAttemptTrackerPort {

	private final SessionProperties sessionProperties;
	private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

	public InMemoryLoginAttemptTracker(SessionProperties sessionProperties) {
		this.sessionProperties = sessionProperties != null ? sessionProperties : SessionProperties.defaultProperties();
	}

	public InMemoryLoginAttemptTracker() {
		this(SessionProperties.defaultProperties());
	}

	@Override
	public void recordFailedAttempt(String key, Instant timestamp) {
		if (key == null || key.isBlank()) {
			return;
		}
		Instant ts = timestamp != null ? timestamp : Instant.now();
		attempts.compute(key, (k, current) -> {
			if (current == null) {
				int count = 1;
				Instant lockedUntil = count >= sessionProperties.maxFailedLoginAttempts()
						? ts.plusSeconds(sessionProperties.lockoutDurationSeconds())
						: null;
				return new AttemptRecord(count, lockedUntil, ts);
			}

			// If previously locked and lockout has expired, reset counter to 1
			if (current.lockedUntil() != null && ts.isAfter(current.lockedUntil())) {
				int count = 1;
				Instant lockedUntil = count >= sessionProperties.maxFailedLoginAttempts()
						? ts.plusSeconds(sessionProperties.lockoutDurationSeconds())
						: null;
				return new AttemptRecord(count, lockedUntil, ts);
			}

			int newCount = current.count() + 1;
			Instant lockedUntil = current.lockedUntil();
			if (newCount >= sessionProperties.maxFailedLoginAttempts() && lockedUntil == null) {
				lockedUntil = ts.plusSeconds(sessionProperties.lockoutDurationSeconds());
			}
			return new AttemptRecord(newCount, lockedUntil, ts);
		});
	}

	@Override
	public void recordSuccessfulAttempt(String key) {
		if (key != null) {
			attempts.remove(key);
		}
	}

	@Override
	public LockoutStatus getLockoutStatus(String key) {
		if (key == null || key.isBlank()) {
			return LockoutStatus.unlocked(0);
		}
		AttemptRecord record = attempts.get(key);
		if (record == null) {
			return LockoutStatus.unlocked(0);
		}

		Instant now = Instant.now();
		if (record.lockedUntil() != null) {
			if (now.isBefore(record.lockedUntil())) {
				return LockoutStatus.locked(record.count(), record.lockedUntil());
			}
			// Lockout expired
			attempts.remove(key);
			return LockoutStatus.unlocked(0);
		}

		return LockoutStatus.unlocked(record.count());
	}

	@Override
	public void unlock(String key) {
		if (key != null) {
			attempts.remove(key);
		}
	}

	/**
	 * Clears all recorded attempt history.
	 */
	public void clear() {
		attempts.clear();
	}

	private record AttemptRecord(int count, Instant lockedUntil, Instant lastAttemptAt) {
	}
}
