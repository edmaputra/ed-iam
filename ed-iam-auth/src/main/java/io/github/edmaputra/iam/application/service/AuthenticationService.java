package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Application service implementing {@link AuthenticateUserUseCase}.
 * Orchestrates credential authentication, brute-force defense, concurrent session limits,
 * effective access computation, signed JWT token issuance, and session revocation.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AuthenticationService implements AuthenticateUserUseCase {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

	private final AuthenticationProviderRouter authRouter;
	private final UserRepository userRepository;
	private final EffectiveAccessResolver effectiveAccessResolver;
	private final TokenProviderPort tokenProvider;
	private final LoginAttemptTrackerPort loginAttemptTracker;
	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final SessionProperties sessionProperties;
	private final UserMfaRepository userMfaRepository;

	/**
	 * Canonical constructor with session, lockout, and MFA dependencies.
	 */
	public AuthenticationService(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider,
			LoginAttemptTrackerPort loginAttemptTracker,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			SessionProperties sessionProperties,
			UserMfaRepository userMfaRepository) {
		this.authRouter = Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null.");
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.effectiveAccessResolver = Objects.requireNonNull(effectiveAccessResolver, "EffectiveAccessResolver must not be null.");
		this.tokenProvider = Objects.requireNonNull(tokenProvider, "TokenProviderPort must not be null.");
		this.loginAttemptTracker = loginAttemptTracker;
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.sessionProperties = sessionProperties != null ? sessionProperties : SessionProperties.defaultProperties();
		this.userMfaRepository = userMfaRepository;
	}

	/**
	 * Constructor with session and lockout dependencies.
	 */
	public AuthenticationService(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider,
			LoginAttemptTrackerPort loginAttemptTracker,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			SessionProperties sessionProperties) {
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, loginAttemptTracker, sessionRegistry, tokenRevocationPort, sessionProperties, null);
	}

	/**
	 * Backward-compatible constructor without session/lockout providers.
	 */
	public AuthenticationService(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider) {
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, null, null, null, SessionProperties.defaultProperties(), null);
	}

	@Override
	public TokenResponse login(LoginCommand command) {
		Objects.requireNonNull(command, "LoginCommand must not be null.");

		if (loginAttemptTracker != null) {
			LockoutStatus status = loginAttemptTracker.getLockoutStatus(command.email());
			if (status != null && status.locked()) {
				log.warn("Login rejected: account for '{}' is locked until {}", command.email(), status.lockedUntil());
				throw new AccountLockedException(
						"Account is temporarily locked due to too many failed attempts until: " + status.lockedUntil(),
						status.lockedUntil());
			}
		}

		AuthenticatedIdentity identity;
		try {
			identity = authRouter.authenticate(
					new PasswordAuthCredentials(command.email(), command.password()));
			if (loginAttemptTracker != null) {
				loginAttemptTracker.recordSuccessfulAttempt(command.email());
			}
		}
		catch (AuthenticationException ex) {
			log.warn("Authentication failed for principal '{}': {}", command.email(), ex.getMessage());
			if (loginAttemptTracker != null) {
				loginAttemptTracker.recordFailedAttempt(command.email(), Instant.now());
			}
			throw ex;
		}

		log.info("User '{}' successfully authenticated", identity.email());

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		if (userMfaRepository != null) {
			Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(user.getId());
			if (mfaOpt.isPresent() && mfaOpt.get().isEnabled()) {
				String mfaChallengeToken = tokenProvider.createMfaChallengeToken(user.getId(), command.tenantId());
				return TokenResponse.mfaChallenge(mfaChallengeToken);
			}
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, command.tenantId());

		String tokenId = UuidV7.generate().toString();
		String accessToken = tokenProvider.createAccessToken(effectiveAccess, tokenId);
		if (accessToken == null) {
			accessToken = tokenProvider.createAccessToken(effectiveAccess);
		}
		String refreshToken = tokenProvider.createRefreshToken(user.getId(), command.tenantId());

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

	@Override
	public TokenResponse refreshToken(RefreshTokenCommand command) {
		Objects.requireNonNull(command, "RefreshTokenCommand must not be null.");

		RefreshTokenClaims claims = tokenProvider.parseRefreshToken(command.refreshToken());

		if (tokenRevocationPort != null && claims.tokenId() != null && tokenRevocationPort.isTokenRevoked(claims.tokenId())) {
			log.warn("Detected refresh token reuse for user '{}', token ID '{}'. Invalidation initiated.", claims.userId(), claims.tokenId());
			logoutAll(claims.userId(), claims.tenantId());
			throw new AuthenticationException("Refresh token has been revoked.");
		}

		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new UserNotFoundException(claims.userId()));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, claims.tenantId());

		String tokenId = UuidV7.generate().toString();
		String newAccessToken = tokenProvider.createAccessToken(effectiveAccess, tokenId);
		if (newAccessToken == null) {
			newAccessToken = tokenProvider.createAccessToken(effectiveAccess);
		}
		String newRefreshToken = tokenProvider.createRefreshToken(user.getId(), claims.tenantId());

		if (tokenRevocationPort != null && claims.tokenId() != null) {
			tokenRevocationPort.revokeToken(claims.tokenId(), claims.expiresAt());
		}

		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				newAccessToken,
				newRefreshToken,
				tokenProvider.getAccessTokenExpirationSeconds(),
				profile);
	}

	@Override
	public void logout(String tokenIdentifier) {
		if (tokenIdentifier == null || tokenIdentifier.isBlank()) {
			return;
		}
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeToken(tokenIdentifier, Instant.now().plusSeconds(tokenProvider.getAccessTokenExpirationSeconds()));
		}
		if (sessionRegistry != null) {
			sessionRegistry.findByTokenIdentifier(tokenIdentifier)
					.ifPresent(s -> sessionRegistry.revokeSession(s.id()));
		}
	}

	@Override
	public void logoutAll(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Instant now = Instant.now();
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, now);
		}
		if (sessionRegistry != null) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, tenantId);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
	}

	@Override
	public List<UserSession> getActiveSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		if (sessionRegistry != null) {
			return sessionRegistry.findActiveSessions(userId, tenantId);
		}
		return List.of();
	}

	@Override
	public UserProfileResponse getMe(CurrentActor actor) {
		Objects.requireNonNull(actor, "CurrentActor must not be null.");

		User user = userRepository.findById(new UserId(actor.userId())).orElse(null);
		String fullName = user != null ? user.getFullName() : actor.email();

		Set<String> scopePaths = actor.accessibleScopePaths();

		Set<UUID> availableTenantIds = Set.of();
		if (user != null) {
			EffectiveAccess access = effectiveAccessResolver.resolve(
					user,
					actor.tenantId() == null ? null : new TenantId(actor.tenantId()));
			availableTenantIds = access.availableTenants().stream()
					.map(tenantId -> tenantId.value())
					.collect(Collectors.toSet());
		}

		return new UserProfileResponse(
				actor.userId(),
				actor.email(),
				fullName,
				actor.tenantId(),
				actor.isPlatformSuperAdmin(),
				actor.isTenantWide(),
				availableTenantIds,
				actor.groups(),
				actor.roles(),
				actor.permissions(),
				actor.accessibleScopeNodeIds(),
				scopePaths);
	}

	@Override
	public TokenResponse switchTenant(SwitchTenantCommand command) {
		Objects.requireNonNull(command, "SwitchTenantCommand must not be null.");

		User user = userRepository.findById(new UserId(command.currentActor().userId()))
				.orElseThrow(() -> new UserNotFoundException(new UserId(command.currentActor().userId())));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		EffectiveAccess fullAccess = effectiveAccessResolver.resolve(user, null);
		if (!user.isPlatformSuperAdmin() && !fullAccess.availableTenants().contains(command.targetTenantId())) {
			throw new AccessDeniedException("User does not have access to tenant: " + command.targetTenantId().value());
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, command.targetTenantId());

		String tokenId = UuidV7.generate().toString();
		String newAccessToken = tokenProvider.createAccessToken(effectiveAccess, tokenId);
		if (newAccessToken == null) {
			newAccessToken = tokenProvider.createAccessToken(effectiveAccess);
		}
		String newRefreshToken = tokenProvider.createRefreshToken(user.getId(), command.targetTenantId());


		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				newAccessToken,
				newRefreshToken,
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
