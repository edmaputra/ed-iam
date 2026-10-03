package io.github.edmaputra.iam.adapter.security.jwt;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.edmaputra.iam.adapter.security.SecurityContextAccessor;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;

/**
 * HTTP filter that extracts the Bearer token from the {@code Authorization} header,
 * verifies it via {@link JwtTokenProvider}, checks token revocation status if configured,
 * and establishes the request-scoped {@link CurrentActor} and {@link TenantContextBridge}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/** Standard Bearer authorization header prefix. */
	public static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final SecurityContextAccessor securityContextAccessor;
	private final ObjectProvider<TenantContextBridge> tenantContextBridgeProvider;
	private final ObjectProvider<TokenRevocationPort> tokenRevocationPortProvider;

	/**
	 * Backward-compatible constructor without token revocation provider.
	 */
	public JwtAuthenticationFilter(
			JwtTokenProvider jwtTokenProvider,
			SecurityContextAccessor securityContextAccessor,
			ObjectProvider<TenantContextBridge> tenantContextBridgeProvider) {
		this(jwtTokenProvider, securityContextAccessor, tenantContextBridgeProvider, null);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			writeUnauthorized(response, "Authorization header contains empty bearer token.");
			return;
		}

		CurrentActor actor;
		try {
			actor = jwtTokenProvider.parseAccessToken(token);
		}
		catch (AuthenticationException ex) {
			writeUnauthorized(response, ex.getMessage());
			return;
		}
		catch (Exception ex) {
			writeUnauthorized(response, "Invalid or expired authorization token.");
			return;
		}

		if (tokenRevocationPortProvider != null) {
			TokenRevocationPort revocationPort = tokenRevocationPortProvider.getIfAvailable();
			if (revocationPort != null && actor.tokenId() != null && revocationPort.isTokenRevoked(actor.tokenId())) {
				writeUnauthorized(response, "Token has been revoked.");
				return;
			}
		}

		try {
			if (actor.tenantId() != null) {
				MDC.put("tenantId", actor.tenantId().toString());
			}
			if (actor.userId() != null) {
				MDC.put("actorId", actor.userId().toString());
			}
			securityContextAccessor.callWithActor(actor, () -> {
				TenantContextBridge tenantBridge = tenantContextBridgeProvider.getIfAvailable();
				if (actor.tenantId() != null && tenantBridge != null) {
					tenantBridge.runWithTenant(actor.tenantId(), () -> {
						filterChain.doFilter(request, response);
					});
				}
				else {
					filterChain.doFilter(request, response);
				}
				return null;
			});
		}
		catch (IOException | ServletException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ServletException("Security-scoped request execution failed.", ex);
		}
		finally {
			MDC.remove("tenantId");
			MDC.remove("actorId");
		}
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String safeMessage = message != null ? escapeJson(message) : "";
		response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + safeMessage + "\"}");
	}

	private static String escapeJson(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(input.length() + 16);
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < ' ') {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
}
