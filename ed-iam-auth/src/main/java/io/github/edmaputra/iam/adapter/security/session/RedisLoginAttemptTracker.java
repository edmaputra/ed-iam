package io.github.edmaputra.iam.adapter.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.domain.model.LockoutStatus;

/**
 * Distributed, Redis-backed implementation of {@link LoginAttemptTrackerPort}.
 * Tracks failed authentication attempts atomically across multi-instance clusters
 * and enforces brute-force lockout windows.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class RedisLoginAttemptTracker implements LoginAttemptTrackerPort {

	private static final String KEY_PREFIX_ATTEMPTS = "iam:lockout:attempts:";
	private static final String KEY_PREFIX_LOCKED = "iam:lockout:locked_until:";

	private final StringRedisTemplate redisTemplate;
	private final SessionProperties sessionProperties;

	public RedisLoginAttemptTracker(StringRedisTemplate redisTemplate, SessionProperties sessionProperties) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null.");
		this.sessionProperties = sessionProperties != null ? sessionProperties : SessionProperties.defaultProperties();
	}

	public RedisLoginAttemptTracker(StringRedisTemplate redisTemplate) {
		this(redisTemplate, SessionProperties.defaultProperties());
	}

	@Override
	public void recordFailedAttempt(String key, Instant timestamp) {
		if (key == null || key.isBlank()) {
			return;
		}
		Instant ts = timestamp != null ? timestamp : Instant.now();
		String attemptsKey = KEY_PREFIX_ATTEMPTS + key;
		Long count = redisTemplate.opsForValue().increment(attemptsKey);

		long lockoutDuration = sessionProperties.lockoutDurationSeconds();
		if (count != null && count == 1) {
			redisTemplate.expire(attemptsKey, Duration.ofSeconds(lockoutDuration));
		}

		if (count != null && count >= sessionProperties.maxFailedLoginAttempts()) {
			Instant lockedUntil = ts.plusSeconds(lockoutDuration);
			redisTemplate.opsForValue().set(
					KEY_PREFIX_LOCKED + key,
					String.valueOf(lockedUntil.toEpochMilli()),
					Duration.ofSeconds(lockoutDuration));
		}
	}

	@Override
	public void recordSuccessfulAttempt(String key) {
		if (key != null) {
			redisTemplate.delete(List.of(KEY_PREFIX_ATTEMPTS + key, KEY_PREFIX_LOCKED + key));
		}
	}

	@Override
	public LockoutStatus getLockoutStatus(String key) {
		if (key == null || key.isBlank()) {
			return LockoutStatus.unlocked(0);
		}

		String lockedUntilVal = redisTemplate.opsForValue().get(KEY_PREFIX_LOCKED + key);
		int attempts = 0;
		String attemptsVal = redisTemplate.opsForValue().get(KEY_PREFIX_ATTEMPTS + key);
		if (attemptsVal != null) {
			try {
				attempts = Integer.parseInt(attemptsVal);
			} catch (NumberFormatException ignored) {
				// fallback 0
			}
		}

		if (lockedUntilVal != null) {
			try {
				long lockedUntilMillis = Long.parseLong(lockedUntilVal);
				Instant lockedUntil = Instant.ofEpochMilli(lockedUntilMillis);
				if (Instant.now().isBefore(lockedUntil)) {
					return LockoutStatus.locked(attempts, lockedUntil);
				}
			} catch (NumberFormatException ignored) {
				// fallback
			}
		}

		return LockoutStatus.unlocked(attempts);
	}

	@Override
	public void unlock(String key) {
		if (key != null) {
			redisTemplate.delete(List.of(KEY_PREFIX_ATTEMPTS + key, KEY_PREFIX_LOCKED + key));
		}
	}
}
