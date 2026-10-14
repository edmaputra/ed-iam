package io.github.edmaputra.iam.adapter.rest;

import java.util.Objects;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkSendRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkVerifyRequest;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * Reusable operations delegate for passwordless Magic Link dispatch and verification web interactions.
 * <p>
 * Encapsulates common HTTP header parsing, request metadata extraction (client IP and User-Agent),
 * and orchestration with {@link ManageMagicLinkUseCase}, enabling consumers to build custom
 * REST controllers, GraphQL resolvers, or BFF endpoints with minimal boilerplate.
 *
 * @author edmaputra
 * @since 0.11.0
 */
public class MagicLinkOperationsDelegate {

	private final ManageMagicLinkUseCase manageMagicLinkUseCase;

	public MagicLinkOperationsDelegate(ManageMagicLinkUseCase manageMagicLinkUseCase) {
		this.manageMagicLinkUseCase = Objects.requireNonNull(manageMagicLinkUseCase,
				"ManageMagicLinkUseCase must not be null.");
	}

	/**
	 * Requests a passwordless magic link to be sent to the user's email address,
	 * parsing an optional tenant header string.
	 *
	 * @param request        the magic link request payload containing email and optional redirect URL
	 * @param headerTenantId optional target tenant ID supplied via {@code X-Tenant-ID} header string
	 * @return {@link MagicLinkRequestResponse}
	 */
	public MagicLinkRequestResponse requestMagicLink(MagicLinkSendRequest request, String headerTenantId) {
		Objects.requireNonNull(request, "MagicLinkSendRequest must not be null.");
		UUID tenantUuid = TenantResolutionHelper.parseTenantHeader(headerTenantId);
		return requestMagicLink(request, tenantUuid);
	}

	/**
	 * Requests a passwordless magic link to be sent to the user's email address with an explicit tenant ID.
	 *
	 * @param request  the magic link request payload containing email and optional redirect URL
	 * @param tenantId optional target tenant UUID
	 * @return {@link MagicLinkRequestResponse}
	 */
	public MagicLinkRequestResponse requestMagicLink(MagicLinkSendRequest request, UUID tenantId) {
		Objects.requireNonNull(request, "MagicLinkSendRequest must not be null.");
		return manageMagicLinkUseCase.requestMagicLink(request.toCommand(tenantId));
	}

	/**
	 * Requests a passwordless magic link using a pre-constructed command.
	 *
	 * @param command the magic link request command
	 * @return {@link MagicLinkRequestResponse}
	 */
	public MagicLinkRequestResponse requestMagicLink(MagicLinkRequestCommand command) {
		Objects.requireNonNull(command, "MagicLinkRequestCommand must not be null.");
		return manageMagicLinkUseCase.requestMagicLink(command);
	}

	/**
	 * Verifies and atomically consumes a magic link token from a request DTO,
	 * automatically extracting client IP and User-Agent from {@link HttpServletRequest}.
	 *
	 * @param request     the verification request payload containing the token
	 * @param httpRequest the HTTP servlet request for client metadata
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyMagicLink(MagicLinkVerifyRequest request, HttpServletRequest httpRequest) {
		Objects.requireNonNull(request, "MagicLinkVerifyRequest must not be null.");
		String ip = HttpRequestUtils.extractClientIp(httpRequest);
		String userAgent = HttpRequestUtils.extractUserAgent(httpRequest);
		return verifyMagicLink(request, ip, userAgent);
	}

	/**
	 * Verifies and atomically consumes a magic link token from a request DTO with explicit client metadata.
	 *
	 * @param request   the verification request payload containing the token
	 * @param clientIp  the client IP address
	 * @param userAgent the client User-Agent string
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyMagicLink(MagicLinkVerifyRequest request, String clientIp, String userAgent) {
		Objects.requireNonNull(request, "MagicLinkVerifyRequest must not be null.");
		return manageMagicLinkUseCase.verifyMagicLink(request.toCommand(clientIp, userAgent));
	}

	/**
	 * Verifies and atomically consumes a magic link token from a raw token string,
	 * automatically extracting client IP and User-Agent from {@link HttpServletRequest}.
	 *
	 * @param token       the magic link token string
	 * @param httpRequest the HTTP servlet request for client metadata
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyMagicLink(String token, HttpServletRequest httpRequest) {
		String ip = HttpRequestUtils.extractClientIp(httpRequest);
		String userAgent = HttpRequestUtils.extractUserAgent(httpRequest);
		return verifyMagicLink(token, ip, userAgent);
	}

	/**
	 * Verifies and atomically consumes a magic link token from a raw token string with explicit client metadata.
	 *
	 * @param token     the magic link token string
	 * @param clientIp  the client IP address
	 * @param userAgent the client User-Agent string
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyMagicLink(String token, String clientIp, String userAgent) {
		Objects.requireNonNull(token, "Token must not be null.");
		return manageMagicLinkUseCase.verifyMagicLink(new MagicLinkVerifyCommand(token, clientIp, userAgent));
	}

	/**
	 * Verifies and atomically consumes a magic link token using a pre-constructed command.
	 *
	 * @param command the magic link verification command
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyMagicLink(MagicLinkVerifyCommand command) {
		Objects.requireNonNull(command, "MagicLinkVerifyCommand must not be null.");
		return manageMagicLinkUseCase.verifyMagicLink(command);
	}
}
