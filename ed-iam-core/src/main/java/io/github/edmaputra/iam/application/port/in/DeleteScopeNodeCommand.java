package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Command for deleting a leaf scope node from a tenant hierarchy.
 *
 * @param tenantId the tenant ID
 * @param id       the scope node ID to delete
 * @author edmaputra
 * @since 0.0.1
 */
public record DeleteScopeNodeCommand(
		TenantId tenantId,
		ScopeNodeId id) {

	public DeleteScopeNodeCommand {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");
	}
}
