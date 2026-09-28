package io.github.edmaputra.iam.adapter.security.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Thread-safe, in-memory implementation of {@link TokenRevocationPort}.
 * Suitable for single-instance deployments, local development, and integration testing.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public class InMemoryTokenRevocationStore implements TokenRevocationPort {

	private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();
	private final Map<UserId, Instant> userRevocationCutoffs = new ConcurrentHashMap<>();

	@Override
	public void revokeToken(String tokenId, Instant expiresAt) {
		Objects.requireNonNull(tokenId, "TokenId must not be null.");
		Instant expiry = expiresAt != null ? expiresAt : Instant.now().plusSeconds(3600);
		revokedTokens.put(tokenId, expiry);
	}

	@Override
	public boolean isTokenRevoked(String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return false;
		}
		Instant expiry = revokedTokens.get(tokenId);
		if (expiry == null) {
			return false;
		}
		if (Instant.now().isAfter(expiry)) {
			revokedTokens.remove(tokenId);
			return false;
		}
		return true;
	}

	@Override
	public void revokeAllForUser(UserId userId, Instant issuedBefore) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Instant cutoff = issuedBefore != null ? issuedBefore : Instant.now();
		userRevocationCutoffs.put(userId, cutoff);
	}

	@Override
	public boolean isUserRevoked(UserId userId, Instant issuedAt) {
		if (userId == null || issuedAt == null) {
			return false;
		}
		Instant cutoff = userRevocationCutoffs.get(userId);
		if (cutoff == null) {
			return false;
		}
		return !issuedAt.isAfter(cutoff);
	}

	/**
	 * Returns the token revocation cutoff timestamp for a user, if any.
	 *
	 * @param userId the user identifier
	 * @return optional cutoff timestamp or null
	 */
	public Instant getUserRevocationCutoff(UserId userId) {
		return userRevocationCutoffs.get(userId);
	}

	/**
	 * Cleans up expired tokens from the in-memory map.
	 */
	public void purgeExpired() {
		Instant now = Instant.now();
		revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
	}

	/**
	 * Clears all in-memory revocation records.
	 */
	public void clear() {
		revokedTokens.clear();
		userRevocationCutoffs.clear();
	}
}
