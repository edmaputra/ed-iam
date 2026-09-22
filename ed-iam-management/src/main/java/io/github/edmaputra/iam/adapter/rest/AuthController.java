package io.github.edmaputra.iam.adapter.rest;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.rest.dto.LoginRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RefreshTokenRequest;
import io.github.edmaputra.iam.adapter.rest.dto.SwitchTenantRequest;
import io.github.edmaputra.iam.adapter.rest.support.TenantResolutionHelper;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;

/**
 * REST controller exposing authentication and identity endpoints under {@code /api/v1/auth}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthenticateUserUseCase authenticateUserUseCase;
	private final CurrentActorProvider currentActorProvider;

	/**
	 * Authenticates user credentials and returns JWT access and refresh tokens.
	 * Tenant context can be supplied via the {@code X-Tenant-ID} header or the request body.
	 *
	 * @param headerTenantId optional tenant ID supplied via {@code X-Tenant-ID} header
	 * @param request        the login request payload
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody LoginRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TokenResponse response = authenticateUserUseCase.login(request.toCommand(tenantUuid));
		return ResponseEntity.ok(response);
	}

	/**
	 * Exchanges a valid refresh token for a newly issued access token.
	 *
	 * @param request the refresh token request payload
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		TokenResponse response = authenticateUserUseCase.refreshToken(request.toCommand());
		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves the current authenticated actor's profile, roles, permissions, and scopes.
	 *
	 * @return HTTP 200 with {@link UserProfileResponse}
	 */
	@GetMapping("/me")
	public ResponseEntity<UserProfileResponse> me() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		UserProfileResponse profile = authenticateUserUseCase.getMe(actor);
		return ResponseEntity.ok(profile);
	}

	/**
	 * Switches the active tenant context for the currently authenticated actor and issues a new token pair.
	 * Target tenant ID can be supplied via the {@code X-Tenant-ID} header or JSON request body.
	 *
	 * @param headerTenantId optional target tenant ID supplied via {@code X-Tenant-ID} header
	 * @param request        optional switch tenant request body
	 * @return HTTP 200 with {@link TokenResponse} scoped to the target tenant
	 */
	@PostMapping("/switch-tenant")
	public ResponseEntity<TokenResponse> switchTenant(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody(required = false) SwitchTenantRequest request) {

		UUID tenantUuid = TenantResolutionHelper.parseTenantHeader(headerTenantId);
		if (tenantUuid == null && request != null) {
			tenantUuid = request.tenantId();
		}
		if (tenantUuid == null) {
			throw new IllegalArgumentException("Target tenant ID must be provided via X-Tenant-ID header or request body.");
		}

		CurrentActor actor = currentActorProvider.requireCurrentActor();
		TokenResponse response = authenticateUserUseCase.switchTenant(
				new SwitchTenantCommand(actor, new TenantId(tenantUuid)));
		return ResponseEntity.ok(response);
	}
}
