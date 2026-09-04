package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Thrown when a user account cannot be found by ID or email.
 *
 * @author edmaputra
 */
public class UserNotFoundException extends RuntimeException {

	/**
	 * Constructs the exception with a user ID.
	 *
	 * @param userId the missing user ID
	 */
	public UserNotFoundException(UserId userId) {
		super("User not found with id: " + userId);
	}

	/**
	 * Constructs the exception with an email.
	 *
	 * @param email the missing email address
	 */
	public UserNotFoundException(String email) {
		super("User not found with email: " + email);
	}
}
