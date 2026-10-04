package io.github.edmaputra.iam.adapter.security.provider;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Employs constant-time dummy verification and deferred status checks to prevent account enumeration
 * and timing side-channel attacks.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class LocalPasswordAuthProvider implements AuthenticationProvider {

	private static final Logger log = LoggerFactory.getLogger(LocalPasswordAuthProvider.class);

	/**
	 * Pre-computed valid BCrypt hash used for constant-time comparisons when a user
	 * cannot be found or lacks a local password, defeating timing side-channel probes.
	 */
	private static final String DUMMY_BCRYPT_HASH = "$2a$10$e8k8dGkJbVqXg1.rM4s80edq6tEsm6R8CkWZ64x/v6r5pGvOaJ6u6";

	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;

	@Override
	public boolean supports(AuthCredentialType credentialType) {
		return credentialType == AuthCredentialType.PASSWORD;
	}

	@Override
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		if (!(credentials instanceof PasswordAuthCredentials passwordCredentials)) {
			throw new IllegalArgumentException("Expected PasswordAuthCredentials but got: " + credentials.getClass().getName());
		}

		Optional<User> optionalUser = userRepository.findByEmail(passwordCredentials.email());

		if (optionalUser.isEmpty()) {
			// Defend against timing attacks: execute dummy BCrypt hash check
			passwordEncoder.matches(passwordCredentials.rawPassword(), DUMMY_BCRYPT_HASH);
			log.debug("Authentication failed: user not found for email '{}'", passwordCredentials.email());
			throw new AuthenticationException("Invalid credentials.");
		}

		User user = optionalUser.get();
		String passwordHash = user.getPasswordHash();

		if (passwordHash == null || passwordHash.isBlank()) {
			// Defend against timing attacks and enumeration of SSO/external accounts
			passwordEncoder.matches(passwordCredentials.rawPassword(), DUMMY_BCRYPT_HASH);
			log.debug("Authentication failed: local password not configured for user '{}'", user.getId());
			throw new AuthenticationException("Invalid credentials.");
		}

		if (!passwordEncoder.matches(passwordCredentials.rawPassword(), passwordHash)) {
			log.debug("Authentication failed: invalid password for user '{}'", user.getId());
			throw new AuthenticationException("Invalid credentials.");
		}

		// Verify account lifecycle status only AFTER valid password verification
		if (user.isSuspended()) {
			log.warn("Authentication rejected: user account '{}' is suspended", user.getId());
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			log.warn("Authentication rejected: user account '{}' is deactivated", user.getId());
			throw new AuthenticationException("User account is deactivated.");
		}

		return new AuthenticatedIdentity(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.isPlatformSuperAdmin(),
				ProviderType.LOCAL);
	}
}
