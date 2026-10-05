package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserTokenService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

	@Mock
	private EffectiveAccessResolver effectiveAccessResolver;

	@Mock
	private TokenProviderPort tokenProvider;

	@Mock
	private UserMfaRepository userMfaRepository;

	@Mock
	private SecurityAuditRecorder auditRecorder;

	private UserTokenService userTokenService;

	@BeforeEach
	void setUp() {
		userTokenService = new UserTokenService(
				effectiveAccessResolver,
				tokenProvider,
				userMfaRepository,
				auditRecorder);
	}

	@Test
	@DisplayName("Should require non-null mandatory dependencies in constructor")
	void shouldValidateConstructorParameters() {
		assertThatThrownBy(() -> new UserTokenService(null, tokenProvider, userMfaRepository, auditRecorder))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("EffectiveAccessResolver");

		assertThatThrownBy(() -> new UserTokenService(effectiveAccessResolver, null, userMfaRepository, auditRecorder))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("TokenProviderPort");
	}

	@Test
	@DisplayName("Should return MFA challenge token when MFA is active and record audit event")
	void shouldReturnMfaChallengeWhenActive() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		User user = new User(userId, "mfa@clinic.org", "hash", "MFA User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		UserMfa mfa = new UserMfa(userId, "BASE32SECRET", true, List.of(), Instant.now(), Instant.now());

		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
		when(tokenProvider.createMfaChallengeToken(userId, tenantId)).thenReturn("mfa-challenge-jwt");

		long startTime = System.currentTimeMillis();
		Optional<TokenResponse> challenge = userTokenService.checkMfaRequired(user, tenantId, "password", startTime);

		assertThat(challenge).isPresent();
		assertThat(challenge.get().mfaRequired()).isTrue();
		assertThat(challenge.get().mfaToken()).isEqualTo("mfa-challenge-jwt");
		verify(auditRecorder).recordMfaRequired(eq("password"), eq(tenantId.value()), eq(startTime));
	}

	@Test
	@DisplayName("Should return empty optional when MFA is not active or disabled")
	void shouldReturnEmptyWhenMfaInactive() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		User user = new User(userId, "no-mfa@clinic.org", "hash", "No MFA", UserStatus.ACTIVE, false, Instant.now(), Instant.now());

		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

		Optional<TokenResponse> challenge = userTokenService.checkMfaRequired(user, tenantId, "password", System.currentTimeMillis());
		assertThat(challenge).isEmpty();

		UserMfa disabledMfa = new UserMfa(userId, "BASE32SECRET", false, List.of(), Instant.now(), Instant.now());
		when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(disabledMfa));

		challenge = userTokenService.checkMfaRequired(user, tenantId, "password", System.currentTimeMillis());
		assertThat(challenge).isEmpty();
	}

	@Test
	@DisplayName("Should issue tokens and build UserProfileResponse correctly")
	void shouldIssueTokens() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		User user = new User(userId, "doctor@clinic.org", "hash", "Dr. Jane", UserStatus.ACTIVE, false, Instant.now(), Instant.now());

		EffectiveAccess access = new EffectiveAccess(
				userId,
				"doctor@clinic.org",
				tenantId,
				false,
				false,
				Set.of(tenantId),
				Set.of("DOC_GROUP"),
				Set.of("ROLE_DOCTOR"),
				Set.of("patients:read"),
				Set.of(UUID.randomUUID()),
				Set.of("/clinics/1"));

		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(access);
		when(tokenProvider.createAccessToken(eq(access), any(String.class))).thenReturn("access-token-123");
		when(tokenProvider.createRefreshToken(userId, tenantId)).thenReturn("refresh-token-123");

		UserTokenService.TokenIssueResult result = userTokenService.issueTokens(user, tenantId);

		assertThat(result.accessToken()).isEqualTo("access-token-123");
		assertThat(result.refreshToken()).isEqualTo("refresh-token-123");
		assertThat(result.tokenId()).isNotBlank();
		assertThat(result.effectiveAccess()).isEqualTo(access);

		UserProfileResponse profile = result.profile();
		assertThat(profile.id()).isEqualTo(userId.value());
		assertThat(profile.email()).isEqualTo("doctor@clinic.org");
		assertThat(profile.fullName()).isEqualTo("Dr. Jane");
		assertThat(profile.tenantId()).isEqualTo(tenantId.value());
		assertThat(profile.roles()).containsExactly("ROLE_DOCTOR");
		assertThat(profile.groups()).containsExactly("DOC_GROUP");
		assertThat(profile.permissions()).containsExactly("patients:read");
	}

	@Test
	@DisplayName("Should issue tokens with fallback if tokenId-based creation returns null")
	void shouldFallbackAccessTokenCreation() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		User user = new User(userId, "doctor@clinic.org", "hash", "Dr. Jane", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		EffectiveAccess access = new EffectiveAccess(userId, "doctor@clinic.org", tenantId, false, false, Set.of(tenantId), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		when(effectiveAccessResolver.resolve(user, tenantId)).thenReturn(access);
		when(tokenProvider.createAccessToken(eq(access), any(String.class))).thenReturn(null);
		when(tokenProvider.createAccessToken(access)).thenReturn("fallback-token");
		when(tokenProvider.createRefreshToken(userId, tenantId)).thenReturn("refresh-token");

		UserTokenService.TokenIssueResult result = userTokenService.issueTokens(user, tenantId);
		assertThat(result.accessToken()).isEqualTo("fallback-token");
	}

	@Test
	@DisplayName("Should delegate parseRefreshToken, getAccessTokenExpirationSeconds, and resolveEffectiveAccess")
	void shouldDelegateTokenProviderAndAccessResolverMethods() {
		RefreshTokenClaims claims = new RefreshTokenClaims(UserId.generate(), null, Instant.now(), Instant.now().plusSeconds(300));
		when(tokenProvider.parseRefreshToken("test-token")).thenReturn(claims);
		when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

		assertThat(userTokenService.parseRefreshToken("test-token")).isEqualTo(claims);
		assertThat(userTokenService.getAccessTokenExpirationSeconds()).isEqualTo(3600L);

		UserId userId = UserId.generate();
		User user = new User(userId, "u@clinic.org", "hash", "User", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		EffectiveAccess access = new EffectiveAccess(userId, "u@clinic.org", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(effectiveAccessResolver.resolve(user, null)).thenReturn(access);

		assertThat(userTokenService.resolveEffectiveAccess(user, null)).isEqualTo(access);
	}

	@Test
	@DisplayName("Should build UserProfileResponse from CurrentActor with and without User entity")
	void shouldBuildUserProfileFromCurrentActor() {
		UUID actorId = UUID.randomUUID();
		UUID tenantUuid = UUID.randomUUID();
		CurrentActor actor = org.mockito.Mockito.mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorId);
		when(actor.email()).thenReturn("actor@clinic.org");
		when(actor.tenantId()).thenReturn(tenantUuid);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.isTenantWide()).thenReturn(true);
		when(actor.groups()).thenReturn(Set.of("group1"));
		when(actor.roles()).thenReturn(Set.of("role1"));
		when(actor.permissions()).thenReturn(Set.of("perm1"));
		when(actor.accessibleScopeNodeIds()).thenReturn(Set.of());
		when(actor.accessibleScopePaths()).thenReturn(Set.of("/root"));

		// 1. Without User entity
		UserProfileResponse profileWithoutUser = userTokenService.toUserProfileResponse(actor, null);
		assertThat(profileWithoutUser.id()).isEqualTo(actorId);
		assertThat(profileWithoutUser.fullName()).isEqualTo("actor@clinic.org");
		assertThat(profileWithoutUser.availableTenantIds()).isEmpty();

		// 2. With User entity
		User user = new User(new UserId(actorId), "actor@clinic.org", "hash", "Actor Name", UserStatus.ACTIVE, false, Instant.now(), Instant.now());
		EffectiveAccess access = new EffectiveAccess(
				new UserId(actorId), "actor@clinic.org", new TenantId(tenantUuid), false, true,
				Set.of(new TenantId(tenantUuid)), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(effectiveAccessResolver.resolve(user, new TenantId(tenantUuid))).thenReturn(access);

		UserProfileResponse profileWithUser = userTokenService.toUserProfileResponse(actor, user);
		assertThat(profileWithUser.fullName()).isEqualTo("Actor Name");
		assertThat(profileWithUser.availableTenantIds()).containsExactly(tenantUuid);
	}
}
