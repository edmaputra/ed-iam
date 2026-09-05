package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Command for moving/re-parenting an existing scope node.
 *
 * @param tenantId    the tenant ID
 * @param id          the scope node ID to move
 * @param newParentId the new parent scope node ID (null to move to root)
 * @author edmaputra
 * @since 1.0.0
 */
public record MoveScopeNodeCommand(
		TenantId tenantId,
		ScopeNodeId id,
		ScopeNodeId newParentId) {

	public MoveScopeNodeCommand {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		Objects.requireNonNull(id, "ScopeNodeId must not be null.");
	}

	/**
	 * Returns the optional new parent ID.
	 *
	 * @return optional {@link ScopeNodeId}
	 */
	public Optional<ScopeNodeId> optionalNewParentId() {
		return Optional.ofNullable(newParentId);
	}
}
