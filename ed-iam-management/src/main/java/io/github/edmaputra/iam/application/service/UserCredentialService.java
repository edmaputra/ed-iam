package io.github.edmaputra.iam.application.service;

import java.util.Objects;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.security.PasswordValidator;

/**
 * Focused application service responsible for validating password policies and generating password hashes.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserCredentialService {

	private final PasswordEncoderPort passwordEncoder;
	private final PasswordValidator passwordValidator;

	public UserCredentialService(PasswordEncoderPort passwordEncoder, PasswordValidator passwordValidator) {
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoderPort must not be null.");
		this.passwordValidator = passwordValidator;
	}

	public UserCredentialService(PasswordEncoderPort passwordEncoder) {
		this(passwordEncoder, null);
	}

	/**
	 * Validates raw password against configured security policies and returns the encoded password hash.
	 *
	 * @param rawPassword     candidate raw password
	 * @param usernameOrEmail username or email associated with the user account
	 * @return encoded password hash, or {@code null} if no password was supplied
	 */
	public String preparePasswordHash(String rawPassword, String usernameOrEmail) {
		if (rawPassword == null || rawPassword.isBlank()) {
			return null;
		}
		if (passwordValidator != null) {
			passwordValidator.validatePassword(rawPassword, usernameOrEmail);
		}
		return passwordEncoder.encode(rawPassword);
	}
}
