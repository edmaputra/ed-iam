package io.github.edmaputra.iam.adapter.rest;

import java.util.Objects;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * Dedicated REST controller for passwordless Magic Link dispatch and
 * verification endpoints
 * under {@code /api/v1/auth/magic-link}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@RestController
@RequestMapping("/api/v1/auth/magic-link")
public class MagicLinkController {

	private final ManageMagicLinkUseCase manageMagicLinkUseCase;

	public MagicLinkController(ManageMagicLinkUseCase manageMagicLinkUseCase) {
		this.manageMagicLinkUseCase = Objects.requireNonNull(manageMagicLinkUseCase,
				"ManageMagicLinkUseCase must not be null.");
	}

	/**
	 * Requests a passwordless magic link to be sent to the user's email address.
	 *
	 * @param headerTenantId optional target tenant ID supplied via
	 *                       {@code X-Tenant-ID} header
	 * @param request        the magic link request payload containing email and
	 *                       optional redirect URL
	 * @return HTTP 200 with {@link MagicLinkRequestResponse}
	 */
	@PostMapping("/request")
	public ResponseEntity<MagicLinkRequestResponse> requestMagicLink(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody MagicLinkSendRequest request) {
		UUID tenantUuid = TenantResolutionHelper.parseTenantHeader(headerTenantId);
		MagicLinkRequestResponse response = manageMagicLinkUseCase
				.requestMagicLink(request.toCommand(tenantUuid));
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
		String ip = HttpRequestUtils.extractClientIp(httpRequest);
		String userAgent = HttpRequestUtils.extractUserAgent(httpRequest);
		TokenResponse response = manageMagicLinkUseCase
				.verifyMagicLink(request.toCommand(ip, userAgent));
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
		String ip = HttpRequestUtils.extractClientIp(httpRequest);
		String userAgent = HttpRequestUtils.extractUserAgent(httpRequest);
		TokenResponse response = manageMagicLinkUseCase
				.verifyMagicLink(new MagicLinkVerifyCommand(token, ip, userAgent));
		return ResponseEntity.ok(response);
	}
}
