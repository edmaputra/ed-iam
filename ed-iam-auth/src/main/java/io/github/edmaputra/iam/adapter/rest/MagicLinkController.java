package io.github.edmaputra.iam.adapter.rest;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkSendRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkVerifyRequest;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;

/**
 * Dedicated REST controller for passwordless Magic Link dispatch and
 * verification endpoints under {@code /api/v1/auth/magic-link}.
 * <p>
 * Delegates web operations to {@link MagicLinkOperationsDelegate}. Can be toggled on/off
 * via {@code iam.auth.magic-link.endpoints.enabled=true}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@RestController
@RequestMapping("/api/v1/auth/magic-link")
public class MagicLinkController {

	private final MagicLinkOperationsDelegate magicLinkOperationsDelegate;

	public MagicLinkController(MagicLinkOperationsDelegate magicLinkOperationsDelegate) {
		this.magicLinkOperationsDelegate = Objects.requireNonNull(magicLinkOperationsDelegate,
				"MagicLinkOperationsDelegate must not be null.");
	}

	public MagicLinkController(ManageMagicLinkUseCase manageMagicLinkUseCase) {
		this(new MagicLinkOperationsDelegate(
				Objects.requireNonNull(manageMagicLinkUseCase, "ManageMagicLinkUseCase must not be null.")));
	}

	/**
	 * Requests a passwordless magic link to be sent to the user's email address.
	 *
	 * @param headerTenantId optional target tenant ID supplied via {@code X-Tenant-ID} header
	 * @param request        the magic link request payload containing email and optional redirect URL
	 * @return HTTP 200 with {@link MagicLinkRequestResponse}
	 */
	@PostMapping("/request")
	public ResponseEntity<MagicLinkRequestResponse> requestMagicLink(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody MagicLinkSendRequest request) {
		MagicLinkRequestResponse response = magicLinkOperationsDelegate.requestMagicLink(request, headerTenantId);
		return ResponseEntity.ok(response);
	}

	/**
	 * Verifies and atomically consumes a magic link token via POST request.
	 *
	 * @param request     the verification request payload containing the token
	 * @param httpRequest the HTTP servlet request for client metadata
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@PostMapping("/verify")
	public ResponseEntity<TokenResponse> verifyMagicLinkPost(
			@Valid @RequestBody MagicLinkVerifyRequest request,
			HttpServletRequest httpRequest) {
		TokenResponse response = magicLinkOperationsDelegate.verifyMagicLink(request, httpRequest);
		return ResponseEntity.ok(response);
	}

	/**
	 * Verifies and atomically consumes a magic link token via GET request (e.g.
	 * direct email link click).
	 *
	 * @param token       the magic link token from query parameters
	 * @param httpRequest the HTTP servlet request for client metadata
	 * @return HTTP 200 with {@link TokenResponse}
	 */
	@GetMapping("/verify")
	public ResponseEntity<TokenResponse> verifyMagicLinkGet(
			@RequestParam("token") String token,
			HttpServletRequest httpRequest) {
		TokenResponse response = magicLinkOperationsDelegate.verifyMagicLink(token, httpRequest);
		return ResponseEntity.ok(response);
	}
}
