package io.github.edmaputra.iam.adapter.security.jwt;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.edmaputra.iam.adapter.security.IamResourceServerAutoConfiguration;
import io.github.edmaputra.iam.adapter.security.SecurityContextAccessor;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}, {@link SecurityContextAccessor},
 * and {@link IamResourceServerAutoConfiguration}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class JwtAuthenticationFilterTest {

	private JwtTokenProvider jwtTokenProvider;
	private SecurityContextAccessor securityContextAccessor;
	private ObjectProvider<TenantContextBridge> tenantBridgeProvider;
	private TenantContextBridge tenantBridge;
	private JwtAuthenticationFilter filter;

	private static final String SECRET = "c2VjdXJlLWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LTEyMzQ1Njc4OTA=";

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(JwtProperties.defaultProperties());
		securityContextAccessor = new SecurityContextAccessor();
		tenantBridgeProvider = mock(ObjectProvider.class);
		tenantBridge = mock(TenantContextBridge.class);

		when(tenantBridgeProvider.getIfAvailable()).thenReturn(tenantBridge);
		filter = new JwtAuthenticationFilter(jwtTokenProvider, securityContextAccessor, tenantBridgeProvider);
	}

	@Test
	@DisplayName("Should skip filter when Authorization header is missing")
	void shouldSkipWhenNoAuthHeader() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(securityContextAccessor.currentActor()).isEmpty();
	}

	@Test
	@DisplayName("Should skip filter when Authorization header does not start with Bearer")
	void shouldSkipWhenNotBearer() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(securityContextAccessor.currentActor()).isEmpty();
	}

	@Test
	@DisplayName("Should return 401 when Bearer token is blank")
	void shouldReturn401WhenBearerBlank() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer    ");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("Authorization header contains empty bearer token");
	}

	@Test
	@DisplayName("Should return 401 when token is invalid or malformed")
	void shouldReturn401WhenInvalidToken() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("Unauthorized");
	}

	@Test
	@DisplayName("Should return 401 with generic message when token parsing throws unanticipated exception")
	void shouldReturn401WhenGenericExceptionOccurs() throws ServletException, IOException {
		JwtTokenProvider mockProvider = mock(JwtTokenProvider.class);
		when(mockProvider.parseAccessToken(any())).thenThrow(new NullPointerException("Unexpected NPE"));
		JwtAuthenticationFilter testFilter = new JwtAuthenticationFilter(mockProvider, securityContextAccessor, tenantBridgeProvider);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		testFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("Invalid or expired authorization token");
	}

	@Test
	@DisplayName("Should authenticate valid token and bind actor and tenant bridge")
	void shouldAuthenticateAndBindContext() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		io.github.edmaputra.iam.application.model.EffectiveAccess access = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				new io.github.edmaputra.iam.domain.model.UserId(userId), "user@tenant.org", new TenantId(tenantId), false, false,
				Set.of("SURGERY"), Set.of("DOCTOR"), Set.of("PATIENT_READ"), Set.of(), Set.of());

		String token = jwtTokenProvider.createAccessToken(access);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			assertThat(securityContextAccessor.currentActor()).isPresent();
			assertThat(securityContextAccessor.currentActor().get().userId()).isEqualTo(userId);
			assertThat(securityContextAccessor.currentActor().get().email()).isEqualTo("user@tenant.org");
		};

		// execute when tenantBridge.runWithTenant is invoked
		org.mockito.Mockito.doAnswer(invocation -> {
			io.github.edmaputra.iam.domain.tenancy.TenantContextBridge.ThrowingRunnable<?> op = invocation.getArgument(1);
			op.run();
			return null;
		}).when(tenantBridge).runWithTenant(any(UUID.class), any());

		filter.doFilter(request, response, chain);

		verify(tenantBridge).runWithTenant(any(UUID.class), any());
		assertThat(securityContextAccessor.currentActor()).isEmpty(); // unbound after completion
	}

	@Test
	@DisplayName("Should authenticate valid token when tenant bridge is null")
	void shouldAuthenticateWhenTenantBridgeNull() throws ServletException, IOException {
		when(tenantBridgeProvider.getIfAvailable()).thenReturn(null);

		UUID userId = UUID.randomUUID();
		io.github.edmaputra.iam.application.model.EffectiveAccess access = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				new io.github.edmaputra.iam.domain.model.UserId(userId), "user@global.org", null, true, false,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		String token = jwtTokenProvider.createAccessToken(access);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			assertThat(securityContextAccessor.currentActor()).isPresent();
			assertThat(securityContextAccessor.currentActor().get().isPlatformSuperAdmin()).isTrue();
		};

		filter.doFilter(request, response, chain);

		assertThat(securityContextAccessor.currentActor()).isEmpty();
	}

	@Test
	@DisplayName("Should authenticate valid token when tenantId is null but tenant bridge is available")
	void shouldAuthenticateWhenTenantIdNullAndTenantBridgePresent() throws ServletException, IOException {
		UUID userId = UUID.randomUUID();
		io.github.edmaputra.iam.application.model.EffectiveAccess access = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				new io.github.edmaputra.iam.domain.model.UserId(userId), "user@global.org", null, true, false,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		String token = jwtTokenProvider.createAccessToken(access);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			assertThat(securityContextAccessor.currentActor()).isPresent();
			assertThat(securityContextAccessor.currentActor().get().isPlatformSuperAdmin()).isTrue();
		};

		filter.doFilter(request, response, chain);

		// tenantBridge should NOT be called because tenantId is null
		verify(tenantBridge, never()).runWithTenant(any(UUID.class), any());
		assertThat(securityContextAccessor.currentActor()).isEmpty();
	}

	@Test
	@DisplayName("Should handle null detail in writeUnauthorized")
	void shouldHandleNullDetailInUnauthorized() throws ServletException, IOException {
		JwtTokenProvider mockProvider = mock(JwtTokenProvider.class);
		when(mockProvider.parseAccessToken(any())).thenThrow(new AuthenticationException(null));
		JwtAuthenticationFilter testFilter = new JwtAuthenticationFilter(mockProvider, securityContextAccessor, tenantBridgeProvider);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		testFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("Should propagate ServletException and IOException from downstream chain")
	void shouldPropagateExceptionsFromDownstreamChain() {
		UUID userId = UUID.randomUUID();
		io.github.edmaputra.iam.application.model.EffectiveAccess access = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				new io.github.edmaputra.iam.domain.model.UserId(userId), "user@global.org", null, true, false,
				Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

		String token = jwtTokenProvider.createAccessToken(access);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain servletExChain = (req, res) -> {
			throw new ServletException("Downstream servlet failure");
		};

		assertThatThrownBy(() -> filter.doFilter(request, response, servletExChain))
				.isInstanceOf(ServletException.class)
				.hasMessage("Downstream servlet failure");

		FilterChain runtimeExChain = (req, res) -> {
			throw new IllegalStateException("Downstream runtime failure");
		};

		assertThatThrownBy(() -> filter.doFilter(request, response, runtimeExChain))
				.isInstanceOf(ServletException.class)
				.hasMessageContaining("Security-scoped request execution failed");
	}

	@Test
	@DisplayName("JwtProperties should properly store and expose configuration values")
	void shouldVerifyJwtProperties() {
		JwtProperties props = new JwtProperties("secret", 3600, 86400);
		assertThat(props.secret()).isEqualTo("secret");
		assertThat(props.accessTokenExpirationSeconds()).isEqualTo(3600);
		assertThat(props.refreshTokenExpirationSeconds()).isEqualTo(86400);

		JwtProperties defaults = JwtProperties.defaultProperties();
		assertThat(defaults.secret()).isNotBlank();
	}

	@Test
	@DisplayName("SecurityContextAccessor should enforce non-null arguments")
	void shouldVerifySecurityContextAccessorInvariants() {
		assertThatThrownBy(() -> securityContextAccessor.callWithActor(null, () -> "test"))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> securityContextAccessor.callWithActor(mock(CurrentActor.class), null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Auto-configuration should load default beans")
	void shouldLoadAutoConfigurationBeans() {
		new ApplicationContextRunner()
				.withUserConfiguration(IamResourceServerAutoConfiguration.class)
				.withPropertyValues(
						"iam.jwt.secret=" + SECRET,
						"iam.jwt.issuer=ed-iam-test")
				.run(context -> {
					assertThat(context).hasSingleBean(JwtTokenProvider.class);
					assertThat(context).hasSingleBean(SecurityContextAccessor.class);
					assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
				});
	}
}
