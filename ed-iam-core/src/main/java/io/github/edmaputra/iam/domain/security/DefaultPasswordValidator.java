package io.github.edmaputra.iam.domain.security;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link PasswordValidator} enforcing configurable password policies
 * according to OWASP authentication and complexity guidelines.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class DefaultPasswordValidator implements PasswordValidator {

	public static final int MIN_LENGTH = PasswordPolicy.DEFAULT_MIN_LENGTH;
	public static final int MAX_LENGTH = PasswordPolicy.DEFAULT_MAX_LENGTH;

	private final PasswordPolicy policy;

	public DefaultPasswordValidator(PasswordPolicy policy) {
		this.policy = policy != null ? policy : PasswordPolicy.defaultPolicy();
	}

	public DefaultPasswordValidator() {
		this(PasswordPolicy.defaultPolicy());
	}

	public PasswordPolicy getPolicy() {
		return policy;
	}

	@Override
	public void validatePassword(String rawPassword, String usernameOrEmail) {
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new IllegalArgumentException("Password must not be null or blank.");
		}

		if (rawPassword.length() < policy.minLength()) {
			throw new IllegalArgumentException("Password must be at least " + policy.minLength() + " characters long.");
		}

		if (rawPassword.length() > policy.maxLength()) {
			throw new IllegalArgumentException("Password must be at most " + policy.maxLength() + " characters long.");
		}

		long upperCount = rawPassword.chars().filter(Character::isUpperCase).count();
		if (upperCount < policy.minUppercase()) {
			throw new IllegalArgumentException("Password must contain at least " + policy.minUppercase() + " uppercase letter(s).");
		}

		long lowerCount = rawPassword.chars().filter(Character::isLowerCase).count();
		if (lowerCount < policy.minLowercase()) {
			throw new IllegalArgumentException("Password must contain at least " + policy.minLowercase() + " lowercase letter(s).");
		}

		long numberCount = rawPassword.chars().filter(Character::isDigit).count();
		if (numberCount < policy.minNumbers()) {
			throw new IllegalArgumentException("Password must contain at least " + policy.minNumbers() + " numeric digit(s).");
		}

		long specialCount = rawPassword.chars().filter(ch -> !Character.isLetterOrDigit(ch)).count();
		if (specialCount < policy.minSpecialCharacters()) {
			throw new IllegalArgumentException("Password must contain at least " + policy.minSpecialCharacters() + " special character(s).");
		}

		if (policy.customRegex() != null) {
			Pattern pattern = Pattern.compile(policy.customRegex());
			if (!pattern.matcher(rawPassword).find()) {
				String desc = policy.regexDescription() != null && !policy.regexDescription().isBlank()
						? policy.regexDescription()
						: "Password does not satisfy the custom security pattern.";
				throw new IllegalArgumentException(desc);
			}
		}

		if (policy.disallowUsername() && usernameOrEmail != null && !usernameOrEmail.isBlank()) {
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
