package io.github.edmaputra.iam.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MfaChallengeClaims;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Focused application service responsible for resolving user effective permissions,
 * issuing signed JWT access/refresh tokens, and building user profile responses.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserTokenService {

	private final EffectiveAccessResolver effectiveAccessResolver;
	private final TokenProviderPort tokenProvider;
	private final UserMfaRepository userMfaRepository;
	private final SecurityAuditRecorder auditRecorder;

	public record TokenIssueResult(
			String accessToken,
			String refreshToken,
			String tokenId,
			UserProfileResponse profile,
			EffectiveAccess effectiveAccess) {
	}

	public UserTokenService(
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider,
			UserMfaRepository userMfaRepository,
			SecurityAuditRecorder auditRecorder) {
		this.effectiveAccessResolver = Objects.requireNonNull(effectiveAccessResolver, "EffectiveAccessResolver must not be null.");
		this.tokenProvider = Objects.requireNonNull(tokenProvider, "TokenProviderPort must not be null.");
		this.userMfaRepository = userMfaRepository;
		this.auditRecorder = auditRecorder != null ? auditRecorder : SecurityAuditRecorder.noop();
	}

	public UserTokenService(
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider) {
		this(effectiveAccessResolver, tokenProvider, null, null);
	}

	public Optional<TokenResponse> checkMfaRequired(User user, TenantId tenantId, String authMethod, long startTime) {
		if (userMfaRepository != null) {
			Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(user.getId());
			if (mfaOpt.isPresent() && mfaOpt.get().isEnabled()) {
				auditRecorder.recordMfaRequired(authMethod, tenantId != null ? tenantId.value() : null, startTime);
				String mfaChallengeToken = tokenProvider.createMfaChallengeToken(user.getId(), tenantId);
				return Optional.of(TokenResponse.mfaChallenge(mfaChallengeToken));
			}
		}
		return Optional.empty();
	}

	public TokenIssueResult issueTokens(User user, TenantId tenantId) {
		EffectiveAccess effectiveAccess = effectiveAccessResolver.resolve(user, tenantId);

		String tokenId = UuidV7.generate().toString();
		String accessToken = tokenProvider.createAccessToken(effectiveAccess, tokenId);
		if (accessToken == null) {
			accessToken = tokenProvider.createAccessToken(effectiveAccess);
		}
		String refreshToken = tokenProvider.createRefreshToken(user.getId(), tenantId);
		UserProfileResponse profile = toUserProfileResponse(user, effectiveAccess);

		return new TokenIssueResult(accessToken, refreshToken, tokenId, profile, effectiveAccess);
	}

	public RefreshTokenClaims parseRefreshToken(String refreshToken) {
		return tokenProvider.parseRefreshToken(refreshToken);
	}

	public MfaChallengeClaims parseMfaChallengeToken(String mfaChallengeToken) {
		return tokenProvider.parseMfaChallengeToken(mfaChallengeToken);
	}

	public long getAccessTokenExpirationSeconds() {
		return tokenProvider.getAccessTokenExpirationSeconds();
	}

	public EffectiveAccess resolveEffectiveAccess(User user, TenantId tenantId) {
		return effectiveAccessResolver.resolve(user, tenantId);
	}

	public UserProfileResponse toUserProfileResponse(User user, EffectiveAccess access) {
		Set<UUID> availableTenantIds = access.availableTenants().stream()
				.map(TenantId::value)
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

	public UserProfileResponse toUserProfileResponse(CurrentActor actor, User user) {
		String fullName = user != null ? user.getFullName() : actor.email();
		Set<String> scopePaths = actor.accessibleScopePaths();

		Set<UUID> availableTenantIds = Set.of();
		if (user != null) {
			EffectiveAccess access = effectiveAccessResolver.resolve(
					user,
					actor.tenantId() == null ? null : new TenantId(actor.tenantId()));
			availableTenantIds = access.availableTenants().stream()
					.map(TenantId::value)
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
}
