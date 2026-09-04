package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.port.in.LoginCommand;

/**
 * REST request body for user authentication.
 *
 * @param email    user email address
 * @param password raw user password
 * @param tenantId optional tenant context UUID
 * @author edmaputra
 */
public record LoginRequest(String email, String password, UUID tenantId) {

	public LoginRequest {
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(password, "Password must not be null.");
	}

	/**
	 * Maps this REST request to the inbound {@link LoginCommand}.
	 *
	 * @return new {@link LoginCommand}
	 */
	public LoginCommand toCommand() {
		return new LoginCommand(email, password, tenantId == null ? null : new TenantId(tenantId));
	}
}
