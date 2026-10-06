package io.github.edmaputra.iam.domain.security;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Domain value object defining configurable constraints for password complexity and validation.
 *
 * @param minLength            minimum length in characters
 * @param maxLength            maximum length in characters
 * @param minUppercase         minimum number of uppercase characters required
 * @param minLowercase         minimum number of lowercase characters required
 * @param minNumbers           minimum number of numeric digits required
 * @param minSpecialCharacters minimum number of symbol/special characters required
 * @param customRegex          optional custom regular expression pattern to match
 * @param regexDescription     optional human-readable explanation when custom regex fails
 * @param disallowUsername     whether candidate password must not contain username/email handle
 * @author edmaputra
 * @since 0.9.0
 */
public record PasswordPolicy(
		int minLength,
		int maxLength,
		int minUppercase,
		int minLowercase,
		int minNumbers,
		int minSpecialCharacters,
		String customRegex,
		String regexDescription,
		boolean disallowUsername) {

	public static final int DEFAULT_MIN_LENGTH = 8;
	public static final int DEFAULT_MAX_LENGTH = 128;
	public static final int DEFAULT_MIN_UPPERCASE = 1;
	public static final int DEFAULT_MIN_LOWERCASE = 1;
	public static final int DEFAULT_MIN_NUMBERS = 1;
	public static final int DEFAULT_MIN_SPECIAL = 1;

	public PasswordPolicy {
		if (minLength < 1) {
			minLength = DEFAULT_MIN_LENGTH;
		}
		if (maxLength < minLength) {
			maxLength = Math.max(minLength, DEFAULT_MAX_LENGTH);
		}
		if (minUppercase < 0) {
			minUppercase = 0;
		}
		if (minLowercase < 0) {
			minLowercase = 0;
		}
		if (minNumbers < 0) {
			minNumbers = 0;
		}
		if (minSpecialCharacters < 0) {
			minSpecialCharacters = 0;
		}

		if (customRegex != null && !customRegex.isBlank()) {
			try {
				Pattern.compile(customRegex);
			}
			catch (PatternSyntaxException ex) {
				throw new IllegalArgumentException("Invalid custom regex pattern: " + customRegex, ex);
			}
		}
		else {
			customRegex = null;
		}
	}

	/**
	 * Returns the default standard OWASP-compliant password policy.
	 *
	 * @return default {@link PasswordPolicy}
	 */
	public static PasswordPolicy defaultPolicy() {
		return new PasswordPolicy(
				DEFAULT_MIN_LENGTH,
				DEFAULT_MAX_LENGTH,
				DEFAULT_MIN_UPPERCASE,
				DEFAULT_MIN_LOWERCASE,
				DEFAULT_MIN_NUMBERS,
				DEFAULT_MIN_SPECIAL,
				null,
				null,
				true);
	}
}
