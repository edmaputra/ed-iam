package io.github.edmaputra.iam.application.service;

import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.application.service.UserTokenService.TokenIssueResult;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Composite application service facade implementing {@link ManageMagicLinkUseCase}.
 * Coordinates magic link dispatching, token verification, MFA challenge checks, and session registration
 * by delegating to specialized collaborator services.
 *
 * @author edmaputra
 * @since 0.7.0
 */
public class MagicLinkService implements ManageMagicLinkUseCase {

	private final MagicLinkProperties magicLinkProperties;
	private final UserRepository userRepository;
	private final MagicLinkDispatchService dispatchService;
	private final MagicLinkTokenStorePort magicLinkTokenStore;
	private final AuthenticationProviderRouter authRouter;
	private final UserTokenService userTokenService;
	private final AuthSessionService authSessionService;

	/**
	 * Canonical constructor with specialized collaborator services.
	 */
	public MagicLinkService(
			MagicLinkProperties magicLinkProperties,
			UserRepository userRepository,
			MagicLinkDispatchService dispatchService,
			MagicLinkTokenStorePort magicLinkTokenStore,
			AuthenticationProviderRouter authRouter,
			UserTokenService userTokenService,
			AuthSessionService authSessionService) {
		this.magicLinkProperties = magicLinkProperties != null ? magicLinkProperties : MagicLinkProperties.defaultProperties();
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.dispatchService = Objects.requireNonNull(dispatchService, "MagicLinkDispatchService must not be null.");
		this.magicLinkTokenStore = magicLinkTokenStore;
		this.authRouter = Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null.");
		this.userTokenService = Objects.requireNonNull(userTokenService, "UserTokenService must not be null.");
		this.authSessionService = Objects.requireNonNull(authSessionService, "AuthSessionService must not be null.");
	}

	/**
	 * Backwards-compatible canonical constructor with all individual ports.
	 */
	public MagicLinkService(
			MagicLinkProperties magicLinkProperties,
			UserRepository userRepository,
			MagicLinkTokenStorePort magicLinkTokenStore,
			MagicLinkNotifierPort magicLinkNotifier,
			AuthenticationProviderRouter authRouter,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider,
			UserMfaRepository userMfaRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			SessionProperties sessionProperties) {
		this(
				magicLinkProperties,
				Objects.requireNonNull(userRepository, "UserRepository must not be null."),
				new MagicLinkDispatchService(magicLinkProperties, magicLinkTokenStore, magicLinkNotifier),
				magicLinkTokenStore,
				Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null."),
				new UserTokenService(effectiveAccessResolver, tokenProvider, userMfaRepository, null),
				new AuthSessionService(sessionRegistry, tokenRevocationPort, sessionProperties, null)
		);
	}

	/**
	 * Backwards-compatible constructor without session/revocation/MFA ports.
	 */
	public MagicLinkService(
			MagicLinkProperties magicLinkProperties,
			UserRepository userRepository,
			MagicLinkTokenStorePort magicLinkTokenStore,
			MagicLinkNotifierPort magicLinkNotifier,
			AuthenticationProviderRouter authRouter,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider) {
		this(magicLinkProperties, userRepository, magicLinkTokenStore, magicLinkNotifier, authRouter,
				effectiveAccessResolver, tokenProvider, null, null, null, SessionProperties.defaultProperties());
	}

	@Override
	public MagicLinkRequestResponse requestMagicLink(MagicLinkRequestCommand command) {
		return dispatchService.requestMagicLink(command, userRepository);
	}

	@Override
	public TokenResponse verifyMagicLink(MagicLinkVerifyCommand command) {
		Objects.requireNonNull(command, "MagicLinkVerifyCommand must not be null.");

		if (!magicLinkProperties.enabled()) {
			throw new AuthenticationException("Magic link passwordless authentication is currently disabled.");
		}

		TenantId tenantId = null;
		if (magicLinkTokenStore != null) {
			Optional<MagicLinkToken> existingToken = magicLinkTokenStore.findByToken(command.token());
			if (existingToken.isPresent()) {
				tenantId = existingToken.get().tenantId();
			}
		}

		AuthenticatedIdentity identity = authRouter.authenticate(new MagicLinkAuthCredentials(command.token()));

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		Optional<TokenResponse> mfaChallenge = userTokenService.checkMfaRequired(user, tenantId, "magic_link", 0);
		if (mfaChallenge.isPresent()) {
			return mfaChallenge.get();
		}

		TokenIssueResult result = userTokenService.issueTokens(user, tenantId);

		authSessionService.registerSession(
				user.getId(),
				result.effectiveAccess().tenantId(),
				result.tokenId(),
				userTokenService.getAccessTokenExpirationSeconds(),
				command.ipAddress(),
				command.userAgent());

		return TokenResponse.of(
				result.accessToken(),
				result.refreshToken(),
				userTokenService.getAccessTokenExpirationSeconds(),
				result.profile());
	}
}
