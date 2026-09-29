package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * REST request body for initiating passwordless magic link dispatch.
 *
 * @param email       destination user email address
 * @param tenantId    optional tenant UUID
 * @param redirectUrl optional client redirect URL where the user should land after verification
 * @author edmaputra
 * @since 0.5.0
 */
public record MagicLinkSendRequest(
		@NotBlank(message = "Email must not be blank.")
		@Email(message = "Email must be a valid email address.")
		String email,

		UUID tenantId,

		String redirectUrl) {

	/**
	 * Secondary constructor creating a magic link request without tenant or redirect URL.
	 *
	 * @param email user email address
	 */
	public MagicLinkSendRequest(String email) {
		this(email, null, null);
	}

	/**
	 * Maps this REST request to the inbound {@link MagicLinkRequestCommand}.
	 *
	 * @return new {@link MagicLinkRequestCommand}
	 */
	public MagicLinkRequestCommand toCommand() {
		return toCommand(null);
	}

	/**
	 * Maps this REST request to the inbound {@link MagicLinkRequestCommand}, preferring the HTTP header tenant ID if present.
	 *
	 * @param headerTenantId optional tenant ID from request header
	 * @return new {@link MagicLinkRequestCommand}
	 */
	public MagicLinkRequestCommand toCommand(UUID headerTenantId) {
		UUID effectiveTenantId = headerTenantId != null ? headerTenantId : tenantId;
		return new MagicLinkRequestCommand(
				email,
				effectiveTenantId == null ? null : new TenantId(effectiveTenantId),
				redirectUrl);
	}
}
