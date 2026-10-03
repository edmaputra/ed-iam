package io.github.edmaputra.iam.adapter.security.provider;

import java.time.Instant;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.AuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.InvalidMagicLinkException;
import io.github.edmaputra.iam.domain.exception.MagicLinkConsumedException;
import io.github.edmaputra.iam.domain.exception.MagicLinkExpiredException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;

/**
 * Authentication provider for passwordless Magic Link tokens.
 * Validates token state, atomically consumes it via {@link MagicLinkTokenStorePort}, and verifies user state.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@RequiredArgsConstructor
public class MagicLinkAuthProvider implements AuthenticationProvider {

	private final MagicLinkTokenStorePort magicLinkTokenStore;
	private final UserRepository userRepository;

	@Override
	public boolean supports(AuthCredentialType credentialType) {
		return credentialType == AuthCredentialType.MAGIC_LINK;
	}

	@Override
	public AuthenticatedIdentity authenticate(AuthCredentials credentials) {
		if (!(credentials instanceof MagicLinkAuthCredentials magicLinkCreds)) {
			throw new IllegalArgumentException("Expected MagicLinkAuthCredentials but got: " + credentials.getClass().getName());
		}

		Instant now = Instant.now();
		MagicLinkToken token = magicLinkTokenStore.findByToken(magicLinkCreds.token())
				.orElseThrow(() -> new InvalidMagicLinkException("Magic link token not found or invalid."));

		if (token.isConsumed()) {
			throw new MagicLinkConsumedException("Magic link token has already been consumed.");
		}
		if (token.isExpired(now)) {
			throw new MagicLinkExpiredException("Magic link token has expired.");
		}

		MagicLinkToken consumed = magicLinkTokenStore.consume(magicLinkCreds.token(), now)
				.orElseThrow(() -> new MagicLinkConsumedException("Magic link token could not be consumed (already used)."));

		User user = userRepository.findById(consumed.userId())
				.orElseThrow(() -> new AuthenticationException("User account not found for magic link."));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		return new AuthenticatedIdentity(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.isPlatformSuperAdmin(),
				ProviderType.MAGIC_LINK);
	}
}
