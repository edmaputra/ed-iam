package io.github.edmaputra.iam.adapter.security.provider;

import java.util.Objects;

import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

/**
 * Authentication provider for local email and password credentials.
 * Checks BCrypt password hashes and verifies user lifecycle states (active, suspended, deactivated).
 *
 * @author edmaputra
 */
public class LocalPasswordAuthProvider implements AuthenticationProvider {

	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;

	/**
	 * Constructs the local password auth provider.
	 *
	 * @param userRepository  the user repository
	 * @param passwordEncoder the password encoder port
	 */
	public LocalPasswordAuthProvider(UserRepository userRepository, PasswordEncoderPort passwordEncoder) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoderPort must not be null.");
	}

	@Override
	public boolean supports(AuthCredentialType credentialType) {
		return credentialType == AuthCredentialType.PASSWORD;
	}

	@Override
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		if (!(credentials instanceof PasswordAuthCredentials passwordCredentials)) {
			throw new IllegalArgumentException("Expected PasswordAuthCredentials but got: " + credentials.getClass().getName());
		}

		User user = userRepository.findByEmail(passwordCredentials.email())
				.orElseThrow(() -> new AuthenticationException("Invalid credentials."));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		String passwordHash = user.getPasswordHash();
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new AuthenticationException("Local password authentication is not configured for this account.");
		}

		if (!passwordEncoder.matches(passwordCredentials.rawPassword(), passwordHash)) {
			throw new AuthenticationException("Invalid credentials.");
		}

		return new AuthenticatedIdentity(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.isPlatformSuperAdmin(),
				ProviderType.LOCAL);
	}
}
