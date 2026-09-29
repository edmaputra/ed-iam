package io.github.edmaputra.iam.domain.repository;

import java.util.Optional;

import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;

/**
 * Outbound SPI repository contract for managing {@link UserMfa} persistence.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public interface UserMfaRepository {

	/**
	 * Finds the MFA configuration for the specified user ID.
	 *
	 * @param userId the user ID
	 * @return optional containing the user's MFA settings if present
	 */
	Optional<UserMfa> findByUserId(UserId userId);

	/**
	 * Persists or updates the specified user MFA configuration.
	 *
	 * @param userMfa the user MFA domain model
	 * @return persisted user MFA instance
	 */
	UserMfa save(UserMfa userMfa);

	/**
	 * Deletes the MFA configuration associated with the specified user ID.
	 *
	 * @param userId the user ID
	 */
	void deleteByUserId(UserId userId);
}
