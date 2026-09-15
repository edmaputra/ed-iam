package io.github.edmaputra.iam.domain.auth;

import java.util.Objects;

/**
 * Encapsulates standard email and raw password credentials for local authentication.
 *
 * @param email       the user's email address
 * @param rawPassword the user's raw unhashed password
 * @author edmaputra
 * @since 0.0.1
 */
public record PasswordAuthCredentials(String email, String rawPassword) implements AuthCredentials {

	public PasswordAuthCredentials {
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(rawPassword, "Password must not be null.");
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email must not be blank.");
		}
		if (rawPassword.isBlank()) {
			throw new IllegalArgumentException("Password must not be blank.");
		}
	}

	@Override
	public AuthCredentialType credentialType() {
		return AuthCredentialType.PASSWORD;
	}
}
