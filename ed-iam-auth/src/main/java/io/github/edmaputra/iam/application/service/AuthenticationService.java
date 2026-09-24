package io.github.edmaputra.iam.application.service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link AuthenticateUserUseCase}.
 * Orchestrates credential authentication, effective access computation, and signed JWT token issuance.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class AuthenticationService implements AuthenticateUserUseCase {

	private final AuthenticationProviderRouter authRouter;
	private final UserRepository userRepository;
	private final EffectiveAccessResolver effectiveAccessResolver;
	private final TokenProviderPort tokenProvider;

	@Override
	public TokenResponse login(LoginCommand command) {
		Objects.requireNonNull(command, "LoginCommand must not be null.");

		AuthenticatedIdentity identity = authRouter.authenticate(
				new PasswordAuthCredentials(command.email(), command.password()));

		User user = userRepository.findById(identity.userId())
				.orElseThrow(() -> new UserNotFoundException(identity.userId()));

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, command.tenantId());

		String accessToken = tokenProvider.createAccessToken(effectiveAccess);
		String refreshToken = tokenProvider.createRefreshToken(user.getId(), command.tenantId());

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

		User user = userRepository.findById(claims.userId())
				.orElseThrow(() -> new UserNotFoundException(claims.userId()));

		if (user.isSuspended()) {
			throw new AuthenticationException("User account is suspended.");
		}
		if (user.isDeactivated()) {
			throw new AuthenticationException("User account is deactivated.");
		}

		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, claims.tenantId());

		String newAccessToken = tokenProvider.createAccessToken(effectiveAccess);
		String newRefreshToken = tokenProvider.createRefreshToken(user.getId(), claims.tenantId());

		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return TokenResponse.of(
				newAccessToken,
				newRefreshToken,
				tokenProvider.getAccessTokenExpirationSeconds(),
				profile);
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

		String newAccessToken = tokenProvider.createAccessToken(effectiveAccess);
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
