package io.github.edmaputra.iam.adapter.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.MagicLinkTokenJpaEntity;

/**
 * Spring Data JPA repository for {@link MagicLinkTokenJpaEntity}.
 *
 * @author edmaputra
 * @since 0.6.0
 */
@Repository
public interface MagicLinkTokenJpaRepository extends JpaRepository<MagicLinkTokenJpaEntity, UUID> {

	/**
	 * Finds a magic link token entity by its secret token string.
	 *
	 * @param token the secret token string
	 * @return optional entity if found
	 */
	Optional<MagicLinkTokenJpaEntity> findByToken(String token);

	/**
	 * Finds the latest magic link token entity issued to a given email address.
	 *
	 * @param email the destination email address
	 * @return optional entity if found
	 */
	Optional<MagicLinkTokenJpaEntity> findTopByEmailOrderByCreatedAtDesc(String email);

	/**
	 * Atomically marks an unconsumed, non-expired magic link token as consumed.
	 *
	 * @param token      the secret token string
	 * @param consumedAt the consumption timestamp
	 * @return 1 if successfully marked consumed, 0 otherwise
	 */
	@Modifying
	@Query("UPDATE MagicLinkTokenJpaEntity e SET e.consumedAt = :consumedAt WHERE e.token = :token AND e.consumedAt IS NULL AND e.expiresAt > :consumedAt")
	int markConsumed(@Param("token") String token, @Param("consumedAt") Instant consumedAt);

	/**
	 * Deletes tokens that are expired or already consumed older than the given cutoff timestamp.
	 *
	 * @param before cutoff timestamp
	 * @return number of deleted records
	 */
	@Modifying
	@Query("DELETE FROM MagicLinkTokenJpaEntity e WHERE e.expiresAt < :before OR e.consumedAt IS NOT NULL")
	int deleteExpired(@Param("before") Instant before);
}
