package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Command for creating a new organizational scope node.
 *
 * @param tenantId the tenant ID
 * @param parentId optional parent scope node ID (null for root node)
 * @param code     the unique node code
 * @param name     the human-readable name
 * @author edmaputra
 */
public record CreateScopeNodeCommand(
		TenantId tenantId,
		ScopeNodeId parentId,
		String code,
		String name) {

	public CreateScopeNodeCommand {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
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

	/**
	 * Factory method creating a root scope node command.
	 *
	 * @param tenantId the tenant ID
	 * @param code     the scope node code
	 * @param name     the scope node name
	 * @return new {@link CreateScopeNodeCommand}
	 */
	public static CreateScopeNodeCommand root(TenantId tenantId, String code, String name) {
		return new CreateScopeNodeCommand(tenantId, null, code, name);
	}

	/**
	 * Factory method creating a child scope node command.
	 *
	 * @param tenantId the tenant ID
	 * @param parentId the parent scope node ID
	 * @param code     the child scope node code
	 * @param name     the child scope node name
	 * @return new {@link CreateScopeNodeCommand}
	 */
	public static CreateScopeNodeCommand child(TenantId tenantId, ScopeNodeId parentId, String code, String name) {
		Objects.requireNonNull(parentId, "ParentId must not be null for child node command.");
		return new CreateScopeNodeCommand(tenantId, parentId, code, name);
	}

	/**
	 * Returns true if this command represents a root scope node.
	 *
	 * @return true if root
	 */
	public boolean isRoot() {
		return parentId == null;
	}

	/**
	 * Returns the optional parent node ID.
	 *
	 * @return optional {@link ScopeNodeId}
	 */
	public Optional<ScopeNodeId> optionalParentId() {
		return Optional.ofNullable(parentId);
	}
}
