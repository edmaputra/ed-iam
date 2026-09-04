package io.github.edmaputra.iam.adapter.security.jwt;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;
import io.github.edmaputra.iam.adapter.security.SecurityContextAccessor;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.domain.model.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

	private JwtTokenProvider jwtTokenProvider;
	private SecurityContextAccessor securityContextAccessor;
	private ObjectProvider<TenantContextBridge> tenantContextProvider;
	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(JwtProperties.defaultProperties());
		securityContextAccessor = new SecurityContextAccessor();
		tenantContextProvider = Mockito.mock(ObjectProvider.class);

		when(tenantContextProvider.getIfAvailable()).thenReturn(null);

		filter = new JwtAuthenticationFilter(jwtTokenProvider, securityContextAccessor, tenantContextProvider);
	}

	@Test
	@DisplayName("Should proceed without auth context when Authorization header is missing")
	void shouldProceedWithoutAuthHeader() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = Mockito.mock(FilterChain.class);

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		assertThat(securityContextAccessor.currentActor()).isEmpty();
	}

	@Test
	@DisplayName("Should extract and bind CurrentActor in context when valid Bearer token is provided")
	void shouldBindCurrentActorOnValidBearerToken() throws ServletException, IOException {
		UserId userId = UserId.generate();
		EffectiveAccess access = new EffectiveAccess(
				userId,
				"doctor@hospital.org",
				null,
				true,
				true,
				Set.of(),
				Set.of("SUPERADMIN"),
				Set.of("*"),
				Set.of(),
				Set.of("/"));

		String token = jwtTokenProvider.createAccessToken(access);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		AtomicBoolean actorVerified = new AtomicBoolean(false);
		FilterChain filterChain = (req, res) -> {
			if (securityContextAccessor.currentActor().isPresent()) {
				if ("doctor@hospital.org".equals(securityContextAccessor.requireCurrentActor().email())) {
					actorVerified.set(true);
				}
			}
		};

		filter.doFilter(request, response, filterChain);

		assertThat(actorVerified.get()).isTrue();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Should return 401 Unauthorized when invalid Bearer token is provided")
	void shouldReturn401OnInvalidBearerToken() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid-tampered-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain filterChain = Mockito.mock(FilterChain.class);

		filter.doFilter(request, response, filterChain);

		verify(filterChain, never()).doFilter(any(), any());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("Unauthorized");
	}
}
