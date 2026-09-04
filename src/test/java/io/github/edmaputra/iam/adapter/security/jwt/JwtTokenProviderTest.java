package io.github.edmaputra.iam.adapter.security.jwt;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken("invalid-token"))
				.isInstanceOf(AuthenticationException.class);
	}
}
