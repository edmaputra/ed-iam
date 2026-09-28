package io.github.edmaputra.iam.application.port.out;

import java.time.Instant;

import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Outbound SPI port for token revocation and denylist checks.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public interface TokenRevocationPort {

	/**
	 * Revokes a specific token identifier until its expiration timestamp.
	 *
	 * @param tokenIdentifier the token jti or unique identifier
	 * @param expiresAt       the expiration timestamp after which the entry can be pruned
	 */
	void revokeToken(String tokenIdentifier, Instant expiresAt);

	/**
	 * Checks whether the specified token identifier is recorded in the revocation list.
	 *
	 * @param tokenIdentifier the token jti or unique identifier
	 * @return true if revoked
	 */
	boolean isTokenRevoked(String tokenIdentifier);

	/**
	 * Revokes all tokens issued to the given user at or before the specified instant.
	 *
	 * @param userId    the user ID
	 * @param revokedAt timestamp marking the revocation cutoff
	 */
	void revokeAllForUser(UserId userId, Instant revokedAt);

	/**
	 * Checks whether tokens for the specified user issued at or before the given timestamp are revoked.
	 *
	 * @param userId   the user ID
	 * @param issuedAt the token issuance timestamp
	 * @return true if the user's tokens issued at or before that timestamp are revoked
	 */
	boolean isUserRevoked(UserId userId, Instant issuedAt);
}
