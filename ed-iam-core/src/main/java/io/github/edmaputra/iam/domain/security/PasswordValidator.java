package io.github.edmaputra.iam.domain.security;

/**
 * Domain SPI for validating password strength and compliance with security policies
 * according to OWASP authentication standards.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface PasswordValidator {

	/**
	 * Validates whether a candidate raw password complies with security and complexity policies.
	 *
	 * @param rawPassword     the candidate raw password
	 * @param usernameOrEmail the associated username or email for context checks
	 * @throws IllegalArgumentException if the password violates security policy constraints
	 */
	void validatePassword(String rawPassword, String usernameOrEmail);
}
