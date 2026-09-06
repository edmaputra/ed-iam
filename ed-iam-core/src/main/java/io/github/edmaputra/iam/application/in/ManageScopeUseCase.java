package io.github.edmaputra.iam.application.port.in;

import java.util.List;

import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.ScopeTreeNode;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Inbound port for managing organizational scope hierarchies, re-parenting nodes, and querying trees.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface ManageScopeUseCase {

	/**
	 * Creates a new root or child scope node.
	 *
	 * @param command the creation command
	 * @param context the tenant operation context
	 * @return the created {@link ScopeNode}
	 */
	ScopeNode createScopeNode(CreateScopeNodeCommand command, OperationContext context);

	/**
	 * Updates the code and display name of an existing scope node.
	 *
	 * @param command the update command
	 * @param context the tenant operation context
	 * @return the updated {@link ScopeNode}
	 */
	ScopeNode updateMetadata(UpdateScopeNodeCommand command, OperationContext context);

	/**
	 * Moves a scope node to a new parent and updates descendant materialized paths.
	 *
	 * @param command the move command
	 * @param context the tenant operation context
	 * @return the moved {@link ScopeNode}
	 */
	ScopeNode moveNode(MoveScopeNodeCommand command, OperationContext context);

	/**
	 * Deletes a leaf scope node from the hierarchy.
	 *
	 * @param command the deletion command
	 * @param context the tenant operation context
	 */
	void deleteNode(DeleteScopeNodeCommand command, OperationContext context);

	/**
	 * Retrieves the full nested scope hierarchy tree for a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of root {@link ScopeTreeNode} elements with nested children
	 */
	List<ScopeTreeNode> getScopeTree(TenantId tenantId);

	/**
	 * Retrieves a flat list of all scope nodes in a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @return list of all scope nodes
	 */
	List<ScopeNode> getFlatScopeList(TenantId tenantId);

	/**
	 * Retrieves a specific scope node by ID within a tenant.
	 *
	 * @param tenantId the tenant ID
	 * @param id       the scope node ID
	 * @return the matching {@link ScopeNode}
	 */
	ScopeNode getById(TenantId tenantId, ScopeNodeId id);
}
