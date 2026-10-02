package io.github.edmaputra.iam.application.port.out;

import java.time.Instant;
import java.util.Optional;

import io.github.edmaputra.iam.domain.model.MagicLinkToken;

/**
 * Outbound SPI port for persisting, querying, and atomically consuming magic link tokens.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public interface MagicLinkTokenStorePort {

	/**
	 * Persists a newly issued magic link token.
	 *
	 * @param token the magic link token domain model
	 */
	void save(MagicLinkToken token);

	/**
	 * Finds a magic link token by its secret token string.
	 *
	 * @param token the secret token string
	 * @return optional containing the matching {@link MagicLinkToken} if found
	 */
	Optional<MagicLinkToken> findByToken(String token);

	/**
	 * Atomically marks the token matching the given string as consumed at the specified timestamp.
	 *
	 * @param token      the secret token string
	 * @param consumedAt the timestamp of consumption
	 * @return optional containing the updated consumed {@link MagicLinkToken}, or empty if not found or already consumed
	 */
	Optional<MagicLinkToken> consume(String token, Instant consumedAt);

	/**
	 * Purges expired or already consumed magic link tokens older than the specified timestamp.
	 *
	 * @param before cutoff timestamp
	 * @return number of purged records
	 */
	int deleteExpired(Instant before);
}
