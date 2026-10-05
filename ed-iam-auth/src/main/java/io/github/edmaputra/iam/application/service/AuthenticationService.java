package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.application.service.UserTokenService.TokenIssueResult;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Composite application service facade implementing {@link AuthenticateUserUseCase}.
 * Coordinates credential verification, token issuance, session tracking, and tenant switching
 * by delegating to specialized collaborator services.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AuthenticationService implements AuthenticateUserUseCase {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

	private final UserRepository userRepository;
	private final CredentialAuthService credentialAuthService;
	private final UserTokenService userTokenService;
	private final AuthSessionService authSessionService;
	private final SecurityAuditRecorder auditRecorder;

	/**
	 * Canonical constructor with specialized collaborator services.
	 */
	public AuthenticationService(
			UserRepository userRepository,
			CredentialAuthService credentialAuthService,
			UserTokenService userTokenService,
			AuthSessionService authSessionService,
			SecurityAuditRecorder auditRecorder) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.credentialAuthService = Objects.requireNonNull(credentialAuthService, "CredentialAuthService must not be null.");
		this.userTokenService = Objects.requireNonNull(userTokenService, "UserTokenService must not be null.");
		this.authSessionService = Objects.requireNonNull(authSessionService, "AuthSessionService must not be null.");
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	/**
	 * Backwards-compatible canonical constructor with unified security audit recorder.
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
			UserMfaRepository userMfaRepository,
			SecurityAuditRecorder auditRecorder) {
		this(
				Objects.requireNonNull(userRepository, "UserRepository must not be null."),
				new CredentialAuthService(authRouter, loginAttemptTracker, auditRecorder),
				new UserTokenService(effectiveAccessResolver, tokenProvider, userMfaRepository, auditRecorder),
				new AuthSessionService(sessionRegistry, tokenRevocationPort, sessionProperties, auditRecorder),
				auditRecorder
		);
	}

	/**
	 * Full constructor with telemetry and audit event dependencies.
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
			UserMfaRepository userMfaRepository,
			IamTelemetry telemetry,
			EventPublisherPort eventPublisher) {
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, loginAttemptTracker, sessionRegistry,
				tokenRevocationPort, sessionProperties, userMfaRepository, new SecurityAuditRecorder(telemetry, eventPublisher));
	}

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
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, loginAttemptTracker, sessionRegistry, tokenRevocationPort, sessionProperties, userMfaRepository, null, null);
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
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, loginAttemptTracker, sessionRegistry, tokenRevocationPort, sessionProperties, null, null, null);
	}

	/**
	 * Backward-compatible constructor without session/lockout providers.
	 */
	public AuthenticationService(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider) {
		this(authRouter, userRepository, effectiveAccessResolver, tokenProvider, null, null, null, SessionProperties.defaultProperties(), null, null, null);
	}

	@Override
	public TokenResponse login(LoginCommand command) {
		Objects.requireNonNull(command, "LoginCommand must not be null.");

		long startTime = System.nanoTime();

		AuthenticatedIdentity identity = credentialAuthService.authenticatePassword(
				command.email(),
				command.password(),
				command.tenantId(),
				command.ipAddress(),
				startTime);

		log.info("User '{}' successfully authenticated", identity.email());

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		Optional<TokenResponse> mfaChallenge = userTokenService.checkMfaRequired(
				user, command.tenantId(), "password", startTime);
		if (mfaChallenge.isPresent()) {
			return mfaChallenge.get();
		}

		TokenIssueResult result = userTokenService.issueTokens(user, command.tenantId());

		authSessionService.registerSession(
				user.getId(),
				result.effectiveAccess().tenantId(),
				result.tokenId(),
				userTokenService.getAccessTokenExpirationSeconds(),
				command.ipAddress(),
				command.userAgent());

		UUID resolvedTenant = result.effectiveAccess().tenantId() != null ? result.effectiveAccess().tenantId().value() : null;
		auditRecorder.recordLoginSuccess("password", user.getEmail(), user.getId().value(), resolvedTenant, command.ipAddress(), startTime);
		auditRecorder.publish(IamEvent.of(
				IamEventTypes.SESSION_CREATED,
				resolvedTenant,
				user.getId().value(),
				"SESSION",
				Map.of("tokenId", result.tokenId(), "ip", command.ipAddress() != null ? command.ipAddress() : "unknown"),
				user.getEmail()));

		return TokenResponse.of(
				result.accessToken(),
				result.refreshToken(),
				userTokenService.getAccessTokenExpirationSeconds(),
				result.profile());
	}

	@Override
	public TokenResponse refreshToken(RefreshTokenCommand command) {
		Objects.requireNonNull(command, "RefreshTokenCommand must not be null.");

		long startTime = System.nanoTime();
		RefreshTokenClaims claims = userTokenService.parseRefreshToken(command.refreshToken());

		if (claims.tokenId() != null && authSessionService.isTokenRevoked(claims.tokenId())) {
			log.warn("Detected refresh token reuse for user '{}', token ID '{}'. Invalidation initiated.", claims.userId(), claims.tokenId());
			logoutAll(claims.userId(), claims.tenantId());
			auditRecorder.recordLoginFailure("refresh_token", claims.userId().toString(), claims.tenantId() != null ? claims.tenantId().value() : null, "refresh_token_revoked", "Refresh token has been revoked.", null, startTime);
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

		TokenIssueResult result = userTokenService.issueTokens(user, claims.tenantId());

		if (claims.tokenId() != null) {
			authSessionService.revokeToken(claims.tokenId(), claims.expiresAt());
		}

		UUID refreshTenant = claims.tenantId() != null ? claims.tenantId().value() : null;
		auditRecorder.recordLoginSuccess("refresh_token", user.getEmail(), user.getId().value(), refreshTenant, null, startTime);

		return TokenResponse.of(
				result.accessToken(),
				result.refreshToken(),
				userTokenService.getAccessTokenExpirationSeconds(),
				result.profile());
	}

	@Override
	public void logout(String tokenIdentifier) {
		authSessionService.logout(tokenIdentifier, userTokenService.getAccessTokenExpirationSeconds());
	}

	@Override
	public void logoutAll(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		authSessionService.logoutAll(userId, tenantId);
	}

	@Override
	public List<UserSession> getActiveSessions(UserId userId, TenantId tenantId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return authSessionService.getActiveSessions(userId, tenantId);
	}

	@Override
	public UserProfileResponse getMe(CurrentActor actor) {
		Objects.requireNonNull(actor, "CurrentActor must not be null.");
		User user = userRepository.findById(new UserId(actor.userId())).orElse(null);
		return userTokenService.toUserProfileResponse(actor, user);
	}

	@Override
	public TokenResponse switchTenant(SwitchTenantCommand command) {
		Objects.requireNonNull(command, "SwitchTenantCommand must not be null.");

		long startTime = System.nanoTime();
		User user = userRepository.findById(new UserId(command.currentActor().userId()))
				.orElseThrow(() -> new UserNotFoundException(new UserId(command.currentActor().userId())));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		EffectiveAccess fullAccess = userTokenService.resolveEffectiveAccess(user, null);
		if (!user.isPlatformSuperAdmin() && !fullAccess.availableTenants().contains(command.targetTenantId())) {
			auditRecorder.recordAccessDenied("tenant_switch_forbidden", null, command.targetTenantId().value(), user.getEmail(), "/api/v1/auth/switch-tenant", null);
			throw new AccessDeniedException("User does not have access to tenant: " + command.targetTenantId().value());
		}

		TokenIssueResult result = userTokenService.issueTokens(user, command.targetTenantId());

		String targetTenantStr = command.targetTenantId().value().toString();
		auditRecorder.recordLoginSuccess("switch_tenant", user.getEmail(), user.getId().value(), command.targetTenantId().value(), null, startTime);
		auditRecorder.publish(IamEvent.of(
				IamEventTypes.LOGIN_SUCCESS,
				command.targetTenantId().value(),
				user.getId().value(),
				"AUTH",
				Map.of("action", "switch_tenant", "targetTenantId", targetTenantStr, "email", user.getEmail()),
				user.getEmail()));

		return TokenResponse.of(
				result.accessToken(),
				result.refreshToken(),
				userTokenService.getAccessTokenExpirationSeconds(),
				result.profile());
	}
}
