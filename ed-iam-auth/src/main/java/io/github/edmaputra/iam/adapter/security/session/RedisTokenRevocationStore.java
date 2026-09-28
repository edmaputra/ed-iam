package io.github.edmaputra.iam.adapter.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Distributed, Redis-backed implementation of {@link TokenRevocationPort}.
 * Stores revoked JWT IDs with TTL matching their expiration, enabling instantaneous, cluster-wide revocation.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class RedisTokenRevocationStore implements TokenRevocationPort {

	private static final String KEY_PREFIX_TOKEN = "iam:revoked:token:";
	private static final String KEY_PREFIX_USER = "iam:revoked:user:";

	private final StringRedisTemplate redisTemplate;

	public RedisTokenRevocationStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null.");
	}

	@Override
	public void revokeToken(String tokenId, Instant expiresAt) {
		Objects.requireNonNull(tokenId, "TokenId must not be null.");
		Duration ttl;
		if (expiresAt != null) {
			long seconds = Duration.between(Instant.now(), expiresAt).toSeconds();
			ttl = seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(1);
		} else {
			ttl = Duration.ofHours(1);
		}
		redisTemplate.opsForValue().set(KEY_PREFIX_TOKEN + tokenId, "1", ttl);
	}

	@Override
	public boolean isTokenRevoked(String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return false;
		}
		Boolean exists = redisTemplate.hasKey(KEY_PREFIX_TOKEN + tokenId);
		return Boolean.TRUE.equals(exists);
	}

	@Override
	public void revokeAllForUser(UserId userId, Instant issuedBefore) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Instant cutoff = issuedBefore != null ? issuedBefore : Instant.now();
		// Retain user revocation cutoff with standard 24h retention window
		redisTemplate.opsForValue().set(
				KEY_PREFIX_USER + userId.value(),
				String.valueOf(cutoff.toEpochMilli()),
				Duration.ofDays(1));
	}

	@Override
	public boolean isUserRevoked(UserId userId, Instant issuedAt) {
		if (userId == null || issuedAt == null) {
			return false;
		}
		String cutoffVal = redisTemplate.opsForValue().get(KEY_PREFIX_USER + userId.value());
		if (cutoffVal == null) {
			return false;
		}
		try {
			long cutoffMillis = Long.parseLong(cutoffVal);
			Instant cutoff = Instant.ofEpochMilli(cutoffMillis);
			return !issuedAt.isAfter(cutoff);
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
