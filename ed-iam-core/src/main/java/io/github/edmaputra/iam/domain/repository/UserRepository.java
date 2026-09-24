package io.github.edmaputra.iam.domain.repository;

import java.util.Optional;

import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Domain repository port for managing {@link User} entities.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface UserRepository {

	/**
	 * Finds a user by unique identifier.
	 *
	 * @param id the unique user ID
	 * @return optional containing the user if found
	 */
	Optional<User> findById(UserId id);

	/**
	 * Finds a user by unique email address (case-insensitive).
	 *
	 * @param email the user email address
	 * @return optional containing the user if found
	 */
	Optional<User> findByEmail(String email);

	/**
	 * Checks if a user exists with the given email address (case-insensitive).
	 *
	 * @param email the email address to check
	 * @return true if an account exists with this email
	 */
	boolean existsByEmail(String email);

	/**
	 * Saves or updates a user entity.
	 *
	 * @param user the user to persist
	 * @return the persisted user
	 */
	User save(User user);

	/**
	 * Deletes a user by unique identifier.
	 *
	 * @param id the unique user ID
	 */
	void delete(UserId id);

	/**
	 * Retrieves a paginated list of users matching the specified filter criteria.
	 *
	 * @param filter    filter criteria
	 * @param pageQuery pagination parameters
	 * @return paginated user records
	 */
	PagedResult<User> findAll(UserFilter filter, PageQuery pageQuery);
}
