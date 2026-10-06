package io.github.edmaputra.iam.domain.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultPasswordValidator}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class DefaultPasswordValidatorTest {

	private DefaultPasswordValidator validator;

	@BeforeEach
	void setUp() {
		validator = new DefaultPasswordValidator();
	}

	@Test
	@DisplayName("Should accept compliant password meeting OWASP requirements")
	void shouldAcceptCompliantPassword() {
		assertThatCode(() -> validator.validatePassword("Str0ng!Pass#2026", "doctor@hospital.org"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Should reject password shorter than 8 characters or blank")
	void shouldRejectTooShort() {
		assertThatThrownBy(() -> validator.validatePassword(null, "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validatePassword("", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validatePassword("   ", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validatePassword("short", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validatePassword("1234567", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should reject password longer than 128 characters")
	void shouldRejectTooLong() {
		String longPassword = "A1!" + "a".repeat(130);
		assertThatThrownBy(() -> validator.validatePassword(longPassword, "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at most 128 characters");
	}

	@Test
	@DisplayName("Should reject password lacking required character categories")
	void shouldRejectInsufficientDiversity() {
		// Only lowercase (missing uppercase)
		assertThatThrownBy(() -> validator.validatePassword("alllowercasewords", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("uppercase letter(s)");

		// Only digits (missing uppercase)
		assertThatThrownBy(() -> validator.validatePassword("123456789012", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("uppercase letter(s)");

		// Only lowercase and digits (missing uppercase and special)
		assertThatThrownBy(() -> validator.validatePassword("password12345", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("uppercase letter(s)");
	}

	@Test
	@DisplayName("Should reject password containing username or email handle")
	void shouldRejectUsernameInPassword() {
		assertThatThrownBy(() -> validator.validatePassword("Doctor123!Special", "doctor@clinic.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not contain the username or email");
	}

	@Test
	@DisplayName("Should enforce custom policy min characters per category")
	void shouldEnforceCustomPolicyCategories() {
		PasswordPolicy customPolicy = new PasswordPolicy(10, 64, 2, 2, 2, 2, null, null, false);
		DefaultPasswordValidator customValidator = new DefaultPasswordValidator(customPolicy);

		// Fails: only 1 uppercase
		assertThatThrownBy(() -> customValidator.validatePassword("Abcde12!!#", "test"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 2 uppercase");

		// Fails: only 1 special char
		assertThatThrownBy(() -> customValidator.validatePassword("ABcde12!aa", "test"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 2 special");

		// Passes: 2 upper, 3 lower, 2 digits, 2 special
		assertThatCode(() -> customValidator.validatePassword("ABcde12!@#", "test"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Should enforce custom regex pattern when specified")
	void shouldEnforceCustomRegex() {
		PasswordPolicy regexPolicy = new PasswordPolicy(
				8, 64, 1, 1, 1, 0, "^[A-Z].*\\$$", "Password must start with uppercase and end with dollar sign", true);
		DefaultPasswordValidator regexValidator = new DefaultPasswordValidator(regexPolicy);

		// Fails custom regex
		assertThatThrownBy(() -> regexValidator.validatePassword("Pass1234!", "user"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Password must start with uppercase and end with dollar sign");

		// Passes custom regex
		assertThatCode(() -> regexValidator.validatePassword("Pass1234$", "user"))
				.doesNotThrowAnyException();
	}
}
