package io.github.edmaputra.iam.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.security.PasswordValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialServiceTest {

	@Mock
	private PasswordEncoderPort passwordEncoder;

	@Mock
	private PasswordValidator passwordValidator;

	private UserCredentialService credentialService;

	@BeforeEach
	void setUp() {
		credentialService = new UserCredentialService(passwordEncoder, passwordValidator);
	}

	@Test
	@DisplayName("Should return null hash when password is null or blank")
	void shouldReturnNullWhenBlank() {
		assertThat(credentialService.preparePasswordHash(null, "user@test.org")).isNull();
		assertThat(credentialService.preparePasswordHash("   ", "user@test.org")).isNull();
	}

	@Test
	@DisplayName("Should validate and encode password when valid")
	void shouldValidateAndEncode() {
		when(passwordEncoder.encode("ValidPass123!")).thenReturn("hashed-pass");

		String hash = credentialService.preparePasswordHash("ValidPass123!", "user@test.org");

		assertThat(hash).isEqualTo("hashed-pass");
		verify(passwordValidator).validatePassword("ValidPass123!", "user@test.org");
		verify(passwordEncoder).encode("ValidPass123!");
	}

	@Test
	@DisplayName("Should throw exception when password validator fails")
	void shouldThrowWhenValidatorFails() {
		org.mockito.Mockito.doThrow(new IllegalArgumentException("Password too weak"))
				.when(passwordValidator).validatePassword("weak", "user@test.org");

		assertThatThrownBy(() -> credentialService.preparePasswordHash("weak", "user@test.org"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Password too weak");
	}
}
