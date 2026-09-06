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
 * @since 1.0.0
 */
public record LoginRequest(String email, String password, UUID tenantId) {

	public LoginRequest {
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(password, "Password must not be null.");
	}

	/**
	 * Secondary constructor creating a login request without an explicit tenant ID.
	 *
	 * @param email    user email address
	 * @param password raw user password
	 */
	public LoginRequest(String email, String password) {
		this(email, password, null);
	}

	/**
	 * Maps this REST request to the inbound {@link LoginCommand}.
	 *
	 * @return new {@link LoginCommand}
	 */
	public LoginCommand toCommand() {
		return toCommand(null);
	}

	/**
	 * Maps this REST request to the inbound {@link LoginCommand}, preferring the HTTP header tenant ID if present.
	 *
	 * @param headerTenantId optional tenant ID from request header
	 * @return new {@link LoginCommand}
	 */
	public LoginCommand toCommand(UUID headerTenantId) {
		UUID effectiveTenantId = headerTenantId != null ? headerTenantId : tenantId;
		return new LoginCommand(email, password, effectiveTenantId == null ? null : new TenantId(effectiveTenantId));
	}
}
