package io.github.edmaputra.iam.adapter.rest;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.LoginRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaActivateRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaDisableRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaLoginVerifyRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RefreshTokenRequest;
import io.github.edmaputra.iam.adapter.rest.dto.SwitchTenantRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserSessionResponse;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller exposing authentication, session lifecycle, and identity endpoints under {@code /api/v1/auth}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthenticateUserUseCase authenticateUserUseCase;
	private final CurrentActorProvider currentActorProvider;
	private final ManageMfaUseCase manageMfaUseCase;

	@Autowired
	public AuthController(
			AuthenticateUserUseCase authenticateUserUseCase,
			CurrentActorProvider currentActorProvider,
			ObjectProvider<ManageMfaUseCase> manageMfaUseCaseProvider) {
		this.authenticateUserUseCase = authenticateUserUseCase;
		this.currentActorProvider = currentActorProvider;
		this.manageMfaUseCase = manageMfaUseCaseProvider != null ? manageMfaUseCaseProvider.getIfAvailable() : null;
	}

	public AuthController(
			AuthenticateUserUseCase authenticateUserUseCase,
			CurrentActorProvider currentActorProvider,
			ManageMfaUseCase manageMfaUseCase) {
		this.authenticateUserUseCase = authenticateUserUseCase;
		this.currentActorProvider = currentActorProvider;
		this.manageMfaUseCase = manageMfaUseCase;
	}

	public AuthController(
			AuthenticateUserUseCase authenticateUserUseCase,
			CurrentActorProvider currentActorProvider) {
		this(authenticateUserUseCase, currentActorProvider, (ManageMfaUseCase) null);
	}

	/**
	 * Authenticates user credentials and returns JWT access and refresh tokens.
	 * Tenant context can be supplied via the {@code X-Tenant-ID} header or the request body.
	 *
	 * @param headerTenantId optional tenant ID supplied via {@code X-Tenant-ID} header
	 * @param request        the login request payload
	 * @param httpRequest    the underlying HTTP servlet request for client metadata
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		String ip = extractClientIp(httpRequest);
		String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : null;

		TokenResponse response = authenticateUserUseCase.login(request.toCommand(tenantUuid, ip, userAgent));
		return ResponseEntity.ok(response);
	}

	/**
	 * Programmatic login overload without HTTP servlet request context.
	 *
	 * @param headerTenantId optional tenant ID
	 * @param request        the login request payload
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	public ResponseEntity<TokenResponse> login(String headerTenantId, LoginRequest request) {
		return login(headerTenantId, request, null);
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
	 * Completes second-factor authentication for an MFA challenge and returns access and refresh tokens.
	 *
	 * @param request     the MFA verification request containing the challenge token and code
	 * @param httpRequest the underlying HTTP servlet request for client metadata
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/mfa/verify")
	public ResponseEntity<TokenResponse> verifyMfa(
			@Valid @RequestBody MfaLoginVerifyRequest request,
			HttpServletRequest httpRequest) {
		String ip = extractClientIp(httpRequest);
		String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : null;
		TokenResponse response = manageMfaUseCase.verifyLogin(request.toCommand(ip, userAgent));
		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves the current user's Multi-Factor Authentication status.
	 *
	 * @return HTTP 200 with {@link MfaStatusResponse}
	 */
	@GetMapping("/mfa/status")
	public ResponseEntity<MfaStatusResponse> getMfaStatus() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		MfaStatusResponse response = manageMfaUseCase.getStatus(new UserId(actor.userId()));
		return ResponseEntity.ok(response);
	}

	/**
	 * Initiates MFA setup by generating a new Base32 secret, OTP Auth QR URI, and backup recovery codes.
	 *
	 * @return HTTP 200 with {@link MfaSetupResponse}
	 */
	@PostMapping("/mfa/setup")
	public ResponseEntity<MfaSetupResponse> setupMfa() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		MfaSetupResponse response = manageMfaUseCase.initiateSetup(new UserId(actor.userId()), "ed-iam");
		return ResponseEntity.ok(response);
	}

	/**
	 * Confirms and activates MFA using the first 6-digit TOTP code generated by an authenticator application.
	 *
	 * @param request the activation request containing the verification code
	 * @return HTTP 204 No Content
	 */
	@PostMapping("/mfa/activate")
	public ResponseEntity<Void> activateMfa(@Valid @RequestBody MfaActivateRequest request) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		manageMfaUseCase.activate(new UserId(actor.userId()), request.code());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Disables Multi-Factor Authentication after verifying the user's TOTP code or password.
	 *
	 * @param request the disable request containing the verification code or password
	 * @return HTTP 204 No Content
	 */
	@PostMapping("/mfa/disable")
	public ResponseEntity<Void> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		manageMfaUseCase.disable(new UserId(actor.userId()), request.codeOrPassword());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Terminates the calling actor's current session and revokes their active JWT token.
	 *
	 * @return HTTP 204 No Content
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		if (actor.tokenId() != null) {
			authenticateUserUseCase.logout(actor.tokenId());
		}
		return ResponseEntity.noContent().build();
	}

	/**
	 * Terminates all active sessions and revokes all tokens for the calling actor.
	 *
	 * @return HTTP 204 No Content
	 */
	@PostMapping("/logout-all")
	public ResponseEntity<Void> logoutAll() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		TenantId tenantId = actor.tenantId() != null ? new TenantId(actor.tenantId()) : null;
		authenticateUserUseCase.logoutAll(new UserId(actor.userId()), tenantId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Retrieves all active sessions for the calling actor.
	 *
	 * @return HTTP 200 with list of {@link UserSessionResponse}
	 */
	@GetMapping("/sessions")
	public ResponseEntity<List<UserSessionResponse>> getSessions() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		TenantId tenantId = actor.tenantId() != null ? new TenantId(actor.tenantId()) : null;
		List<UserSession> sessions = authenticateUserUseCase.getActiveSessions(new UserId(actor.userId()), tenantId);
		List<UserSessionResponse> response = sessions.stream()
				.map(s -> UserSessionResponse.from(s, actor.tokenId()))
				.toList();
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

	private String extractClientIp(HttpServletRequest request) {
		if (request == null) {
			return null;
		}
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			return xForwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
