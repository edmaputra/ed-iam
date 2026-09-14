package io.github.edmaputra.iam.it.app;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.OidcAuthCredentials;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Test REST controller exposing an endpoint under {@code /api/test/oidc}
 * to verify OpenID Connect (OIDC) federated authentication flows over HTTP.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/test/oidc")
@RequiredArgsConstructor
public class TestOidcController {

	private final AuthenticationProviderRouter authRouter;

	@PostMapping("/authenticate")
	public ResponseEntity<OidcIdentityResponse> authenticate(@RequestBody OidcAuthenticateRequest request) {
		TenantId tenantId = request.tenantId() != null ? new TenantId(request.tenantId()) : null;
		OidcAuthCredentials credentials = new OidcAuthCredentials(
				request.token(),
				request.email(),
				request.subject(),
				request.fullName(),
				request.issuer(),
				request.groups() != null ? request.groups() : List.of(),
				tenantId);

		AuthenticatedIdentity identity = authRouter.authenticate(credentials);

		return ResponseEntity.ok(new OidcIdentityResponse(
				identity.userId().value(),
				identity.email(),
				identity.fullName(),
				identity.providerType().name(),
				identity.platformSuperAdmin()));
	}

	public record OidcAuthenticateRequest(
			String token,
			String email,
			String subject,
			String fullName,
			String issuer,
			List<String> groups,
			UUID tenantId) {}

	public record OidcIdentityResponse(
			UUID userId,
			String email,
			String fullName,
			String providerType,
			boolean platformSuperAdmin) {}
}
