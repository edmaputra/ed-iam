package io.github.edmaputra.iam.domain.security;

import java.util.Objects;

/**
 * Default implementation of {@link PasswordValidator} enforcing OWASP authentication
 * and password strength guidelines.
 * <ul>
 *   <li>Length between 8 and 128 characters.</li>
 *   <li>Must contain characters from at least 3 of 4 classes: lowercase, uppercase, digits, symbols.</li>
 *   <li>Must not contain the user's username or email local-part.</li>
 * </ul>
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class DefaultPasswordValidator implements PasswordValidator {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 128;

	@Override
	public void validatePassword(String rawPassword, String usernameOrEmail) {
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new IllegalArgumentException("Password must not be null or blank.");
		}

		if (rawPassword.length() < MIN_LENGTH) {
			throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long.");
		}

		if (rawPassword.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Password must be at most " + MAX_LENGTH + " characters long.");
		}

		int classesMet = 0;
		if (rawPassword.chars().anyMatch(Character::isLowerCase)) {
			classesMet++;
		}
		if (rawPassword.chars().anyMatch(Character::isUpperCase)) {
			classesMet++;
		}
		if (rawPassword.chars().anyMatch(Character::isDigit)) {
			classesMet++;
		}
		if (rawPassword.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
			classesMet++;
		}

		if (classesMet < 3) {
			throw new IllegalArgumentException(
					"Password must contain characters from at least 3 of the following categories: "
							+ "uppercase letters, lowercase letters, digits, and special characters.");
		}

		if (usernameOrEmail != null && !usernameOrEmail.isBlank()) {
			String localPart = extractLocalPart(usernameOrEmail).toLowerCase();
			if (localPart.length() >= 3 && rawPassword.toLowerCase().contains(localPart)) {
				throw new IllegalArgumentException("Password must not contain the username or email address.");
			}
		}
	}

	private static String extractLocalPart(String usernameOrEmail) {
		String trimmed = usernameOrEmail.trim();
		int atIndex = trimmed.indexOf('@');
		return (atIndex > 0) ? trimmed.substring(0, atIndex) : trimmed;
	}
}
