package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Command for updating the metadata of an existing scope node.
 *
 * @param tenantId the tenant ID
 * @param id       the scope node ID
 * @param code     the updated code
 * @param name     the updated name
 * @author edmaputra
 * @since 1.0.0
 */
public record UpdateScopeNodeCommand(
		TenantId tenantId,
		ScopeNodeId id,
		String code,
		String name) {

	public UpdateScopeNodeCommand {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");
		Objects.requireNonNull(code, "Code must not be null.");
		Objects.requireNonNull(name, "Name must not be null.");
		code = code.trim().toUpperCase();
		name = name.trim();
		if (code.isBlank()) {
			throw new IllegalArgumentException("Scope node code must not be blank.");
		}
		if (name.isBlank()) {
			throw new IllegalArgumentException("Scope node name must not be blank.");
		}
	}
}
