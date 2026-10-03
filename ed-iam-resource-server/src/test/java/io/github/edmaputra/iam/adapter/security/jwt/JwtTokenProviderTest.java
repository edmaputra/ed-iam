package io.github.edmaputra.iam.adapter.security.jwt;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.MfaChallengeClaims;
import io.github.edmaputra.iam.application.model.RefreshTokenClaims;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying JWT token generation, parsing, validation, and claim extraction.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class JwtTokenProviderTest {

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(JwtProperties.defaultProperties());
	}

	@Test
	@DisplayName("Should create and parse valid access token")
	void shouldCreateAndParseAccessToken() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		UUID scopeId = UUID.randomUUID();

		EffectiveAccess access = new EffectiveAccess(
				userId,
				"doctor@hospital.org",
				tenantId,
				false,
				false,
				Set.of("CARDIOLOGY"),
				Set.of("PHYSICIAN"),
				Set.of("PATIENT_READ"),
				Set.of(scopeId),
				Set.of("/t1/scope1/"));

		String token = jwtTokenProvider.createAccessToken(access);

		assertThat(token).isNotBlank();
		assertThat(jwtTokenProvider.validateToken(token)).isTrue();

		CurrentActor actor = jwtTokenProvider.parseAccessToken(token);

		assertThat(actor.userId()).isEqualTo(userId.value());
		assertThat(actor.email()).isEqualTo("doctor@hospital.org");
		assertThat(actor.tenantId()).isEqualTo(tenantId.value());
		assertThat(actor.isPlatformSuperAdmin()).isFalse();
		assertThat(actor.isTenantWide()).isFalse();
		assertThat(actor.groups()).containsExactly("CARDIOLOGY");
		assertThat(actor.roles()).containsExactly("PHYSICIAN");
		assertThat(actor.permissions()).containsExactly("PATIENT_READ");
		assertThat(actor.accessibleScopeNodeIds()).containsExactly(scopeId);
		assertThat(actor.hasPermission("PATIENT_READ")).isTrue();
		assertThat(actor.canAccessScope(scopeId)).isTrue();
	}

	@Test
	@DisplayName("Should create and parse refresh token")
	void shouldCreateAndParseRefreshToken() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();

		String refreshToken = jwtTokenProvider.createRefreshToken(userId, tenantId);

		assertThat(refreshToken).isNotBlank();
		assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();

		RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);

		assertThat(claims.userId()).isEqualTo(userId);
		assertThat(claims.tenantId()).isEqualTo(tenantId);
		assertThat(claims.optionalTokenId()).isPresent();
		assertThat(claims.tokenId()).isNotBlank();
	}

	@Test
	@DisplayName("Should reject access token when parsing as refresh token")
	void shouldRejectAccessTokenAsRefreshToken() {
		UserId userId = UserId.generate();
		EffectiveAccess access = new EffectiveAccess(
				userId,
				"doc@hospital.org",
				null,
				true,
				true,
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of(),
				Set.of());

		String accessToken = jwtTokenProvider.createAccessToken(access);

		assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken(accessToken))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("not a REFRESH token");
	}

	@Test
	@DisplayName("Should reject malformed or tampered token")
	void shouldRejectMalformedToken() {
		assertThat(jwtTokenProvider.validateToken("not-a-valid-jwt")).isFalse();
		assertThat(jwtTokenProvider.validateToken(null)).isFalse();
		assertThat(jwtTokenProvider.validateToken("   ")).isFalse();

		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken("invalid-token"))
				.isInstanceOf(AuthenticationException.class);
	}

	@Test
	@DisplayName("Should reject token with mismatched issuer")
	void shouldRejectTokenWithMismatchedIssuer() {
		JwtProperties customIssuerProps = new JwtProperties(
				JwtProperties.TEST_SECRET,
				3600,
				604800,
				"untrusted-foreign-issuer",
				JwtProperties.DEFAULT_AUDIENCE);
		JwtTokenProvider otherIssuerProvider = new JwtTokenProvider(customIssuerProps);

		UserId userId = UserId.generate();
		EffectiveAccess access = new EffectiveAccess(
				userId, "user@test.org", null, true, true,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		String token = otherIssuerProvider.createAccessToken(access);

		assertThat(jwtTokenProvider.validateToken(token)).isFalse();
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid or expired JWT token");
	}

	@Test
	@DisplayName("Should reject token with mismatched audience")
	void shouldRejectTokenWithMismatchedAudience() {
		JwtProperties customAudProps = new JwtProperties(
				JwtProperties.TEST_SECRET,
				3600,
				604800,
				JwtProperties.DEFAULT_ISSUER,
				"foreign-api-audience");
		JwtTokenProvider otherAudProvider = new JwtTokenProvider(customAudProps);

		UserId userId = UserId.generate();
		EffectiveAccess access = new EffectiveAccess(
				userId, "user@test.org", null, true, true,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		String token = otherAudProvider.createAccessToken(access);

		assertThat(jwtTokenProvider.validateToken(token)).isFalse();
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(token))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Invalid or expired JWT token");
	}

	@Test
	@DisplayName("Should create and parse access and refresh token with null tenant context")
	void shouldCreateAndParseTokensWithNullTenant() {
		UserId userId = UserId.generate();

		// Refresh token with null tenant
		String refreshToken = jwtTokenProvider.createRefreshToken(userId, null);
		RefreshTokenClaims refreshClaims = jwtTokenProvider.parseRefreshToken(refreshToken);
		assertThat(refreshClaims.userId()).isEqualTo(userId);
		assertThat(refreshClaims.tenantId()).isNull();
		assertThat(refreshClaims.tokenId()).isNotBlank();

		// Access token for superadmin with null tenant
		EffectiveAccess superAdminAccess = new EffectiveAccess(
				userId, "super@system.org", null, true, true, Set.of(), Set.of("PLATFORM_SUPERADMIN"), Set.of("*"), Set.of(), Set.of("/"));
		String accessToken = jwtTokenProvider.createAccessToken(superAdminAccess);
		CurrentActor actor = jwtTokenProvider.parseAccessToken(accessToken);
		assertThat(actor.tenantId()).isNull();
		assertThat(actor.isPlatformSuperAdmin()).isTrue();

		// Access token parser rejecting refresh token
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(refreshToken))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("not an ACCESS token");

		assertThat(jwtTokenProvider.getAccessTokenExpirationSeconds()).isGreaterThan(0);
		assertThat(jwtTokenProvider.getIssuer()).isEqualTo(JwtProperties.DEFAULT_ISSUER);
		assertThat(jwtTokenProvider.getAudience()).isEqualTo(JwtProperties.DEFAULT_AUDIENCE);
	}

	@Test
	@DisplayName("Should fail when secret is null, blank, or shorter than 256 bits")
	void shouldEnforceSecretValidation() {
		assertThatThrownBy(() -> JwtProperties.of(null, 3600, 604800))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("iam.jwt.secret must not be null or blank");

		assertThatThrownBy(() -> JwtProperties.of("   ", 3600, 604800))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("iam.jwt.secret must not be null or blank");

		assertThatThrownBy(() -> JwtProperties.of("short-secret-less-than-32-bytes", 3600, 604800))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 256 bits (32 bytes)");
	}

	@Test
	@DisplayName("Should handle JwtProperties defaults for blank issuer, audience, or negative expiration")
	void shouldHandleJwtPropertiesEdgeCases() {
		JwtProperties p1 = new JwtProperties(JwtProperties.TEST_SECRET, -10, -20, null, null);
		assertThat(p1.secret()).isEqualTo(JwtProperties.TEST_SECRET);
		assertThat(p1.accessTokenExpirationSeconds()).isEqualTo(JwtProperties.DEFAULT_ACCESS_TOKEN_EXPIRATION);
		assertThat(p1.refreshTokenExpirationSeconds()).isEqualTo(JwtProperties.DEFAULT_REFRESH_TOKEN_EXPIRATION);
		assertThat(p1.issuer()).isEqualTo(JwtProperties.DEFAULT_ISSUER);
		assertThat(p1.audience()).isEqualTo(JwtProperties.DEFAULT_AUDIENCE);

		JwtProperties p2 = new JwtProperties(JwtProperties.TEST_SECRET, 0, 0, "   ", "   ");
		assertThat(p2.issuer()).isEqualTo(JwtProperties.DEFAULT_ISSUER);
		assertThat(p2.audience()).isEqualTo(JwtProperties.DEFAULT_AUDIENCE);
	}

	@Test
	@DisplayName("Should create and parse MFA challenge token successfully and reject invalid types")
	void shouldCreateAndParseMfaChallengeToken() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();

		String mfaToken = jwtTokenProvider.createMfaChallengeToken(userId, tenantId);
		assertThat(mfaToken).isNotBlank();
		assertThat(jwtTokenProvider.validateToken(mfaToken)).isTrue();

		MfaChallengeClaims claims =
				jwtTokenProvider.parseMfaChallengeToken(mfaToken);
		assertThat(claims.userId()).isEqualTo(userId);
		assertThat(claims.optionalTenantId()).contains(tenantId);
		assertThat(claims.issuedAt()).isNotNull();
		assertThat(claims.expiresAt()).isAfter(claims.issuedAt());

		// Reject parsing access token or refresh token as MFA challenge
		String refreshToken = jwtTokenProvider.createRefreshToken(userId, tenantId);
		assertThatThrownBy(() -> jwtTokenProvider.parseMfaChallengeToken(refreshToken))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("not an MFA challenge token");
	}
}
