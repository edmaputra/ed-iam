package io.github.edmaputra.iam.application.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Application service implementing {@link ManageMagicLinkUseCase}.
 * Manages the passwordless magic link lifecycle including token issuance, email notification,
 * and single-use link consumption.
 *
 * @author edmaputra
 * @since 0.7.0
 */
public class MagicLinkService implements ManageMagicLinkUseCase {

	private final MagicLinkProperties magicLinkProperties;
	private final UserRepository userRepository;
	private final MagicLinkTokenStorePort magicLinkTokenStore;
	private final MagicLinkNotifierPort magicLinkNotifier;
	private final AuthenticationProviderRouter authRouter;
	private final EffectiveAccessResolver effectiveAccessResolver;
	private final TokenProviderPort tokenProvider;
	private final UserMfaRepository userMfaRepository;
	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final SessionProperties sessionProperties;
	private final SecureRandom secureRandom = new SecureRandom();

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
		this.magicLinkProperties = magicLinkProperties != null ? magicLinkProperties : MagicLinkProperties.defaultProperties();
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.magicLinkTokenStore = Objects.requireNonNull(magicLinkTokenStore, "MagicLinkTokenStorePort must not be null.");
		this.magicLinkNotifier = Objects.requireNonNull(magicLinkNotifier, "MagicLinkNotifierPort must not be null.");
		this.authRouter = Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null.");
		this.effectiveAccessResolver = Objects.requireNonNull(effectiveAccessResolver, "EffectiveAccessResolver must not be null.");
		this.tokenProvider = Objects.requireNonNull(tokenProvider, "TokenProviderPort must not be null.");
		this.userMfaRepository = userMfaRepository;
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.sessionProperties = sessionProperties != null ? sessionProperties : SessionProperties.defaultProperties();
	}

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

	private static final String GENERIC_DISPATCH_MESSAGE =
			"If an account matching that email exists, a sign-in link has been sent to your inbox.";

	@Override
	public MagicLinkRequestResponse requestMagicLink(MagicLinkRequestCommand command) {
		Objects.requireNonNull(command, "MagicLinkRequestCommand must not be null.");

		if (!magicLinkProperties.enabled()) {
			throw new AuthenticationException("Magic link passwordless authentication is currently disabled.");
		}

		Optional<User> userOpt = userRepository.findByEmail(command.email().trim().toLowerCase());
		if (userOpt.isEmpty()) {
			// Anti-enumeration: return generic success without generating or dispatching a token
			return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
		}

		User user = userOpt.get();
		if (user.isSuspended() || user.isDeactivated()) {
			// Anti-enumeration: return generic success without dispatching
			return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
		}

		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(magicLinkProperties.expirationSeconds());

		MagicLinkToken magicLinkToken = MagicLinkToken.issue(
				token,
				user.getId(),
				command.tenantId(),
				user.getEmail(),
				expiresAt,
				now);

		magicLinkTokenStore.save(magicLinkToken);

		String base = magicLinkProperties.baseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String verificationUrl = base + "/api/v1/auth/magic-link/verify?token=" + token;
		if (command.redirectUrl() != null && !command.redirectUrl().isBlank()) {
			verificationUrl += "&redirect=" + URLEncoder.encode(command.redirectUrl(), StandardCharsets.UTF_8);
		}

		magicLinkNotifier.sendMagicLink(magicLinkToken, verificationUrl);

		return MagicLinkRequestResponse.of(GENERIC_DISPATCH_MESSAGE);
	}

	@Override
	public TokenResponse verifyMagicLink(MagicLinkVerifyCommand command) {
		Objects.requireNonNull(command, "MagicLinkVerifyCommand must not be null.");

		if (!magicLinkProperties.enabled()) {
			throw new AuthenticationException("Magic link passwordless authentication is currently disabled.");
		}

		MagicLinkToken existingToken = magicLinkTokenStore.findByToken(command.token()).orElse(null);
		TenantId tenantId = existingToken != null ? existingToken.tenantId() : null;

		AuthenticatedIdentity identity = authRouter.authenticate(new MagicLinkAuthCredentials(command.token()));

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		if (userMfaRepository != null) {
			Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(user.getId());
			if (mfaOpt.isPresent() && mfaOpt.get().isEnabled()) {
				String mfaChallengeToken = tokenProvider.createMfaChallengeToken(user.getId(), tenantId);
				return TokenResponse.mfaChallenge(mfaChallengeToken);
			}
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, tenantId);

		String tokenId = UuidV7.generate().toString();
		String accessToken = tokenProvider.createAccessToken(effectiveAccess, tokenId);
		if (accessToken == null) {
			accessToken = tokenProvider.createAccessToken(effectiveAccess);
		}
		String refreshToken = tokenProvider.createRefreshToken(user.getId(), tenantId);

		if (sessionRegistry != null) {
			int maxConcurrent = sessionProperties.maxConcurrentSessions();
			if (maxConcurrent > 0) {
				List<UserSession> activeSessions = sessionRegistry.findActiveSessions(user.getId(), effectiveAccess.tenantId());
				if (activeSessions.size() >= maxConcurrent) {
					if (sessionProperties.sessionLimitStrategy() == SessionProperties.SessionLimitStrategy.REJECT_NEW) {
						throw new AuthenticationException("Maximum concurrent active sessions (" + maxConcurrent + ") exceeded.");
					}
					else {
						int excess = activeSessions.size() - maxConcurrent + 1;
						activeSessions.stream()
								.sorted(Comparator.comparing(UserSession::createdAt))
								.limit(excess)
								.forEach(oldSession -> {
									sessionRegistry.revokeSession(oldSession.id());
									if (tokenRevocationPort != null && oldSession.tokenIdentifier() != null) {
										tokenRevocationPort.revokeToken(oldSession.tokenIdentifier(), oldSession.expiresAt());
									}
								});
					}
				}
			}

			long ttl = tokenProvider.getAccessTokenExpirationSeconds();
			UserSession session = UserSession.create(
					user.getId(),
					effectiveAccess.tenantId(),
					tokenId,
					ttl,
					command.ipAddress(),
					command.userAgent());
			sessionRegistry.registerSession(session);
		}

		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				accessToken,
				refreshToken,
				tokenProvider.getAccessTokenExpirationSeconds(),
				profile);
	}

	private UserProfileResponse toUserProfileResponse(User user, EffectiveAccess access) {
		Set<UUID> availableTenantIds = access.availableTenants().stream()
				.map(tenantId -> tenantId.value())
				.collect(Collectors.toSet());

		return new UserProfileResponse(
				user.getId().value(),
				user.getEmail(),
				user.getFullName(),
				access.tenantId() == null ? null : access.tenantId().value(),
				access.platformSuperAdmin(),
				access.tenantWide(),
				availableTenantIds,
				access.groups(),
				access.roles(),
				access.permissions(),
				access.accessibleScopeNodeIds(),
				access.accessibleScopePaths());
	}
}
