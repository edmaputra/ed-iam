package io.github.edmaputra.iam.adapter.rest;

import java.util.Objects;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import io.github.edmaputra.iam.adapter.rest.dto.MfaActivateRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaDisableRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MfaLoginVerifyRequest;
import io.github.edmaputra.iam.adapter.security.properties.MfaProperties;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.in.MfaLoginVerifyCommand;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Reusable operations delegate for Multi-Factor Authentication (MFA / TOTP) web interactions.
 * <p>
 * Encapsulates common HTTP request metadata extraction, actor resolution, and orchestration
 * with {@link ManageMfaUseCase}, enabling consumers to build custom REST controllers, GraphQL
 * resolvers, or BFF endpoints with minimal boilerplate.
 *
 * @author edmaputra
 * @since 0.11.0
 */
public class MfaOperationsDelegate {

	private final ManageMfaUseCase manageMfaUseCase;
	private final CurrentActorProvider currentActorProvider;
	private final MfaProperties mfaProperties;

	public MfaOperationsDelegate(
			ManageMfaUseCase manageMfaUseCase,
			CurrentActorProvider currentActorProvider,
			MfaProperties mfaProperties) {
		this.manageMfaUseCase = Objects.requireNonNull(manageMfaUseCase, "ManageMfaUseCase must not be null.");
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
		this.mfaProperties = Objects.requireNonNullElseGet(mfaProperties, MfaProperties::defaultProperties);
	}

	public MfaOperationsDelegate(
			ManageMfaUseCase manageMfaUseCase,
			CurrentActorProvider currentActorProvider) {
		this(manageMfaUseCase, currentActorProvider, MfaProperties.defaultProperties());
	}

	/**
	 * Verifies an MFA challenge token and TOTP code from an HTTP request.
	 * Extracts client IP and User-Agent automatically from {@link HttpServletRequest}.
	 *
	 * @param request     the MFA verification request containing the challenge token and code
	 * @param httpRequest the underlying HTTP servlet request for client metadata
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyLogin(MfaLoginVerifyRequest request, HttpServletRequest httpRequest) {
		Objects.requireNonNull(request, "MfaLoginVerifyRequest must not be null.");
		String ip = HttpRequestUtils.extractClientIp(httpRequest);
		String userAgent = HttpRequestUtils.extractUserAgent(httpRequest);
		return verifyLogin(request, ip, userAgent);
	}

	/**
	 * Verifies an MFA challenge token and TOTP code with explicit client IP and User-Agent metadata.
	 *
	 * @param request  the MFA verification request containing the challenge token and code
	 * @param clientIp the client IP address
	 * @param userAgent the client User-Agent header string
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyLogin(MfaLoginVerifyRequest request, String clientIp, String userAgent) {
		Objects.requireNonNull(request, "MfaLoginVerifyRequest must not be null.");
		return manageMfaUseCase.verifyLogin(request.toCommand(clientIp, userAgent));
	}

	/**
	 * Verifies an MFA challenge token using raw parameters.
	 *
	 * @param mfaToken  the MFA challenge JWT token
	 * @param code      the 6-digit TOTP code or backup recovery code
	 * @param clientIp  the client IP address
	 * @param userAgent the client User-Agent header string
	 * @return access and refresh {@link TokenResponse}
	 */
	public TokenResponse verifyLogin(String mfaToken, String code, String clientIp, String userAgent) {
		return manageMfaUseCase.verifyLogin(new MfaLoginVerifyCommand(mfaToken, code, clientIp, userAgent));
	}

	/**
	 * Retrieves MFA status for the currently authenticated actor.
	 *
	 * @return {@link MfaStatusResponse} indicating whether MFA is enabled
	 */
	public MfaStatusResponse getStatus() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		return getStatus(new UserId(actor.userId()));
	}

	/**
	 * Retrieves MFA status for a specific user ID.
	 *
	 * @param userId the target user identifier
	 * @return {@link MfaStatusResponse} indicating whether MFA is enabled
	 */
	public MfaStatusResponse getStatus(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return manageMfaUseCase.getStatus(userId);
	}

	/**
	 * Retrieves MFA status for a specific user UUID.
	 *
	 * @param userId the target user UUID
	 * @return {@link MfaStatusResponse} indicating whether MFA is enabled
	 */
	public MfaStatusResponse getStatus(UUID userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return getStatus(new UserId(userId));
	}

	/**
	 * Initiates MFA setup for the currently authenticated actor using the configured issuer name.
	 *
	 * @return {@link MfaSetupResponse} containing secret, QR code URI, and backup codes
	 */
	public MfaSetupResponse initiateSetup() {
		return initiateSetup(mfaProperties.issuer());
	}

	/**
	 * Initiates MFA setup for the currently authenticated actor with a custom issuer name.
	 *
	 * @param issuer the application name shown in authenticator apps
	 * @return {@link MfaSetupResponse} containing secret, QR code URI, and backup codes
	 */
	public MfaSetupResponse initiateSetup(String issuer) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		return initiateSetup(new UserId(actor.userId()), issuer);
	}

	/**
	 * Initiates MFA setup for a specific user with a custom issuer name.
	 *
	 * @param userId the target user identifier
	 * @param issuer the application name shown in authenticator apps
	 * @return {@link MfaSetupResponse} containing secret, QR code URI, and backup codes
	 */
	public MfaSetupResponse initiateSetup(UserId userId, String issuer) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		String effectiveIssuer = (issuer != null && !issuer.isBlank()) ? issuer : mfaProperties.issuer();
		return manageMfaUseCase.initiateSetup(userId, effectiveIssuer);
	}

	/**
	 * Activates MFA for the currently authenticated actor using the supplied activation request.
	 *
	 * @param request the activation request containing the 6-digit TOTP verification code
	 */
	public void activate(MfaActivateRequest request) {
		Objects.requireNonNull(request, "MfaActivateRequest must not be null.");
		activate(request.code());
	}

	/**
	 * Activates MFA for the currently authenticated actor using the supplied TOTP code.
	 *
	 * @param code the 6-digit TOTP code
	 */
	public void activate(String code) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		activate(new UserId(actor.userId()), code);
	}

	/**
	 * Activates MFA for a specific user ID using the supplied TOTP code.
	 *
	 * @param userId the target user identifier
	 * @param code   the 6-digit TOTP code
	 */
	public void activate(UserId userId, String code) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(code, "TOTP code must not be null.");
		manageMfaUseCase.activate(userId, code);
	}

	/**
	 * Disables MFA for the currently authenticated actor using the supplied disable request.
	 *
	 * @param request the disable request containing the verification code or password
	 */
	public void disable(MfaDisableRequest request) {
		Objects.requireNonNull(request, "MfaDisableRequest must not be null.");
		disable(request.codeOrPassword());
	}

	/**
	 * Disables MFA for the currently authenticated actor using a TOTP code or password.
	 *
	 * @param codeOrPassword the 6-digit TOTP code or user password
	 */
	public void disable(String codeOrPassword) {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		disable(new UserId(actor.userId()), codeOrPassword);
	}

	/**
	 * Disables MFA for a specific user ID using a TOTP code or password.
	 *
	 * @param userId         the target user identifier
	 * @param codeOrPassword the 6-digit TOTP code or user password
	 */
	public void disable(UserId userId, String codeOrPassword) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(codeOrPassword, "codeOrPassword must not be null.");
		manageMfaUseCase.disable(userId, codeOrPassword);
	}

	/**
	 * Administratively resets MFA for a specific user ID, removing enrolled secrets and backup codes.
	 *
	 * @param userId the target user identifier
	 */
	public void resetMfa(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		manageMfaUseCase.resetMfa(userId);
	}

	/**
	 * Administratively resets MFA for a specific user UUID, removing enrolled secrets and backup codes.
	 *
	 * @param userId the target user UUID
	 */
	public void resetMfa(UUID userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		resetMfa(new UserId(userId));
	}
}
