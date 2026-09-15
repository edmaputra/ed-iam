package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound command encapsulating credentials and target tenant for authentication.
 *
 * @param email    the user's email address
 * @param password the user's raw password
 * @param tenantId optional tenant ID context for scoping permissions
 * @author edmaputra
 * @since 0.0.1
 */
public record LoginCommand(
		String email,
		String password,
		TenantId tenantId) {

	public LoginCommand {
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(password, "Password must not be null.");
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email must not be blank.");
		}
		if (password.isBlank()) {
			throw new IllegalArgumentException("Password must not be blank.");
		}
	}

	/**
	 * Creates a login command without tenant scoping (e.g. for platform superadmin).
	 *
	 * @param email    the user's email address
	 * @param password the user's raw password
	 * @return new {@link LoginCommand}
	 */
	public static LoginCommand of(String email, String password) {
		return new LoginCommand(email, password, null);
	}

	/**
	 * Creates a login command scoped to a specific tenant.
	 *
	 * @param email    the user's email address
	 * @param password the user's raw password
	 * @param tenantId the target tenant ID
	 * @return new {@link LoginCommand}
	 */
	public static LoginCommand of(String email, String password, TenantId tenantId) {
		return new LoginCommand(email, password, tenantId);
	}
}
