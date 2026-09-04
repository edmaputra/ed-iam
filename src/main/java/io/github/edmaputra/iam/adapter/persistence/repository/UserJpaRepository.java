package io.github.edmaputra.iam.adapter.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.edmaputra.iam.adapter.persistence.entity.UserJpaEntity;

/**
 * Spring Data JPA repository for {@link UserJpaEntity}.
 *
 * @author edmaputra
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

	/**
	 * Finds a user entity by case-insensitive email.
	 *
	 * @param email the email address
	 * @return optional entity
	 */
	Optional<UserJpaEntity> findByEmailIgnoreCase(String email);

	/**
	 * Checks existence by case-insensitive email.
	 *
	 * @param email the email address
	 * @return true if exists
	 */
	boolean existsByEmailIgnoreCase(String email);
}
