package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Command requesting generation and dispatch of a magic link to the user's email address.
 *
 * @param email       the destination email address
 * @param tenantId    optional tenant ID context
 * @param redirectUrl optional client redirect URL where the user should land after verification
 * @author edmaputra
 * @since 0.7.0
 */
public record MagicLinkRequestCommand(
		String email,
		TenantId tenantId,
		String redirectUrl) {

	public MagicLinkRequestCommand {
		Objects.requireNonNull(email, "Email must not be null.");
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email must not be blank.");
		}
	}

	/**
	 * Creates a command without tenant context or redirect URL.
	 *
	 * @param email destination email
	 * @return new {@link MagicLinkRequestCommand}
	 */
	public static MagicLinkRequestCommand of(String email) {
		return new MagicLinkRequestCommand(email, null, null);
	}

	/**
	 * Creates a command with tenant context.
	 *
	 * @param email    destination email
	 * @param tenantId target tenant context
	 * @return new {@link MagicLinkRequestCommand}
	 */
	public static MagicLinkRequestCommand of(String email, TenantId tenantId) {
		return new MagicLinkRequestCommand(email, tenantId, null);
	}

	/**
	 * Creates a command with tenant context and redirect URL.
	 *
	 * @param email       destination email
	 * @param tenantId    target tenant context
	 * @param redirectUrl client redirect landing URL
	 * @return new {@link MagicLinkRequestCommand}
	 */
	public static MagicLinkRequestCommand of(String email, TenantId tenantId, String redirectUrl) {
		return new MagicLinkRequestCommand(email, tenantId, redirectUrl);
	}
}
