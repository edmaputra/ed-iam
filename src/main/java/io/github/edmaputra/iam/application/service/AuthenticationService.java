package io.github.edmaputra.iam.application.service;

import java.util.Objects;
import java.util.Set;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.adapter.security.jwt.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.UserRepository;

/**
 * Application service implementing {@link AuthenticateUserUseCase}.
 * Orchestrates credential authentication, effective access computation, and signed JWT token issuance.
 *
 * @author edmaputra
 */
public class AuthenticationService implements AuthenticateUserUseCase {

	private final AuthenticationProviderRouter authRouter;
	private final UserRepository userRepository;
	private final EffectiveAccessResolver effectiveAccessResolver;
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * Constructs the authentication service with required dependencies.
	 *
	 * @param authRouter              the authentication provider router
	 * @param userRepository          the user domain repository
	 * @param effectiveAccessResolver the access and scope resolution engine
	 * @param jwtTokenProvider        the JWT token issuance provider
	 */
	public AuthenticationService(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			JwtTokenProvider jwtTokenProvider) {
		this.authRouter = Objects.requireNonNull(authRouter, "AuthenticationProviderRouter must not be null.");
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.effectiveAccessResolver = Objects.requireNonNull(effectiveAccessResolver, "EffectiveAccessResolver must not be null.");
		this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "JwtTokenProvider must not be null.");
	}

	@Override
	public TokenResponse login(LoginCommand command) {
		Objects.requireNonNull(command, "LoginCommand must not be null.");

		AuthenticatedIdentity identity = authRouter.authenticate(
				new PasswordAuthCredentials(command.email(), command.password()));

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, command.tenantId());

		String accessToken = jwtTokenProvider.createAccessToken(effectiveAccess);
		String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), command.tenantId());

		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				accessToken,
				refreshToken,
				jwtTokenProvider.getAccessTokenExpirationSeconds(),
				profile);
	}

	@Override
	public TokenResponse refreshToken(RefreshTokenCommand command) {
		Objects.requireNonNull(command, "RefreshTokenCommand must not be null.");

		RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(command.refreshToken());

		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new UserNotFoundException(claims.userId()));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, claims.tenantId());

		String newAccessToken = jwtTokenProvider.createAccessToken(effectiveAccess);
		String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId(), claims.tenantId());

		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				newAccessToken,
				newRefreshToken,
				jwtTokenProvider.getAccessTokenExpirationSeconds(),
				profile);
	}

	@Override
	public UserProfileResponse getMe(CurrentActor actor) {
		Objects.requireNonNull(actor, "CurrentActor must not be null.");

		User user = userRepository.findById(new UserId(actor.userId())).orElse(null);
		String fullName = user != null ? user.getFullName() : actor.email();

		Set<String> scopePaths = (actor instanceof SecurityContextCurrentActor scActor)
				? scActor.accessibleScopePaths()
				: Set.of();

		return new UserProfileResponse(
				actor.userId(),
				actor.email(),
				fullName,
				actor.tenantId(),
				actor.isPlatformSuperAdmin(),
				actor.isTenantWide(),
				actor.groups(),
				actor.roles(),
				actor.permissions(),
				actor.accessibleScopeNodeIds(),
				scopePaths);
	}

	private UserProfileResponse toUserProfileResponse(User user, EffectiveAccess access) {
		return new UserProfileResponse(
				user.getId().value(),
				user.getEmail(),
				user.getFullName(),
				access.tenantId() == null ? null : access.tenantId().value(),
				access.platformSuperAdmin(),
				access.tenantWide(),
				access.groups(),
				access.roles(),
				access.permissions(),
				access.accessibleScopeNodeIds(),
				access.accessibleScopePaths());
	}
}
