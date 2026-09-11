package io.github.edmaputra.iam.it.app;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.auth.ApiKeyAuthCredentials;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;

/**
 * Test REST controller exposing an API key authentication endpoint under {@code /api/test/api-key}
 * to verify machine-to-machine (M2M) API key authentication flows over HTTP.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/test/api-key")
@RequiredArgsConstructor
public class TestApiKeyController {

	private final AuthenticationProviderRouter authRouter;

	/**
	 * Authenticates an API key supplied via the {@code X-API-Key} header or JSON request body.
	 *
	 * @param headerKey the optional API key header
	 * @param body      the optional API key request body
	 * @return HTTP 200 with {@link ApiKeyIdentityResponse} if valid
	 */
	@PostMapping("/authenticate")
	public ResponseEntity<ApiKeyIdentityResponse> authenticate(
			@RequestHeader(value = "X-API-Key", required = false) String headerKey,
			@RequestBody(required = false) ApiKeyAuthenticateRequest body) {

		String apiKey = (headerKey != null && !headerKey.isBlank())
				? headerKey
				: (body != null ? body.apiKey() : null);

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("API key must be provided in X-API-Key header or request body.");
		}

		AuthenticatedIdentity identity = authRouter.authenticate(new ApiKeyAuthCredentials(apiKey));

		return ResponseEntity.ok(new ApiKeyIdentityResponse(
				identity.userId().value(),
				identity.email(),
				identity.fullName(),
				identity.providerType().name(),
				identity.platformSuperAdmin()));
	}

	/**
	 * Request payload for API key authentication.
	 *
	 * @param apiKey the raw API key token string
	 * @author edmaputra
	 * @since 1.0.0
	 */
	public record ApiKeyAuthenticateRequest(String apiKey) {
	}

	/**
	 * Response payload containing authenticated identity attributes.
	 *
	 * @param userId             the authenticated user/client ID
	 * @param email              the associated email address
	 * @param fullName           the associated full name or client label
	 * @param providerType       the authentication provider type name
	 * @param platformSuperAdmin whether the identity has platform super admin privileges
	 * @author edmaputra
	 * @since 1.0.0
	 */
	public record ApiKeyIdentityResponse(
			UUID userId,
			String email,
			String fullName,
			String providerType,
			boolean platformSuperAdmin) {
	}
}
