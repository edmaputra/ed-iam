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
	@DisplayName("Should reject password lacking character diversity (less than 3 categories)")
	void shouldRejectInsufficientDiversity() {
		// Only lowercase
		assertThatThrownBy(() -> validator.validatePassword("alllowercasewords", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 3 of the following categories");

		// Only digits
		assertThatThrownBy(() -> validator.validatePassword("123456789012", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 3 of the following categories");

		// Only lowercase and digits (2 categories)
		assertThatThrownBy(() -> validator.validatePassword("password12345", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 3 of the following categories");
	}

	@Test
	@DisplayName("Should reject password containing username or email handle")
	void shouldRejectUsernameInPassword() {
		assertThatThrownBy(() -> validator.validatePassword("Doctor123!Special", "doctor@clinic.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not contain the username or email");
	}
}
