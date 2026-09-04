package io.github.edmaputra.iam.adapter.rest;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.adapter.rest.dto.LoginRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RefreshTokenRequest;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;

/**
 * REST controller exposing authentication and identity endpoints under {@code /api/v1/auth}.
 *
 * @author edmaputra
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthenticateUserUseCase authenticateUserUseCase;
	private final CurrentActorProvider currentActorProvider;

	/**
	 * Constructs the authentication controller.
	 *
	 * @param authenticateUserUseCase the authenticate user use case inbound port
	 * @param currentActorProvider   the current actor security provider
	 */
	public AuthController(
			AuthenticateUserUseCase authenticateUserUseCase,
			CurrentActorProvider currentActorProvider) {
		this.authenticateUserUseCase = Objects.requireNonNull(authenticateUserUseCase, "AuthenticateUserUseCase must not be null.");
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
	}

	/**
	 * Authenticates user credentials and returns JWT access and refresh tokens.
	 *
	 * @param request the login request payload
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
		TokenResponse response = authenticateUserUseCase.login(request.toCommand());
		return ResponseEntity.ok(response);
	}

	/**
	 * Exchanges a valid refresh token for a newly issued access token.
	 *
	 * @param request the refresh token request payload
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
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
}
