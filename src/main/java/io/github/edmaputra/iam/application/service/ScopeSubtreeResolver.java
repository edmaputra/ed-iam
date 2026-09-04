package io.github.edmaputra.iam.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

/**
 * Service for resolving hierarchical scope subtrees and verifying descendant scope access boundaries.
 *
 * @author edmaputra
 */
public class ScopeSubtreeResolver {

	private final ScopeNodeRepository scopeNodeRepository;

	/**
	 * Constructs the resolver with the scope node repository.
	 *
	 * @param scopeNodeRepository the scope node repository
	 */
	public ScopeSubtreeResolver(ScopeNodeRepository scopeNodeRepository) {
		this.scopeNodeRepository = Objects.requireNonNull(scopeNodeRepository, "ScopeNodeRepository must not be null.");
	}

	/**
	 * Resolves all accessible scope node IDs for a given assigned node ID.
	 * If {@code inheritChildren} is true, recursively includes all descendant node IDs.
	 *
	 * @param assignedNodeId  the scope node ID assigned to the user/group
	 * @param inheritChildren whether permissions cascade downward
	 * @return set of accessible scope node UUIDs
	 */
	public Set<UUID> resolveAccessibleScopeNodeIds(ScopeNodeId assignedNodeId, boolean inheritChildren) {
		Objects.requireNonNull(assignedNodeId, "AssignedNodeId must not be null.");

		return scopeNodeRepository.findById(assignedNodeId)
				.map(node -> {
					Set<UUID> result = new HashSet<>();
					result.add(node.getId().value());

					if (inheritChildren) {
						List<ScopeNode> descendants = scopeNodeRepository.findDescendantsByPathPrefix(node.getPath());
						result.addAll(descendants.stream().map(d -> d.getId().value()).collect(Collectors.toSet()));
					}

					return result;
				})
				.orElse(Set.of());
	}

	/**
	 * Resolves all accessible {@link ScopeNodeId}s for a tenant given a list of assigned node IDs.
	 *
	 * @param tenantId            the tenant ID
	 * @param assignedScopeNodeIds list of directly assigned scope node IDs
	 * @return set of accessible {@link ScopeNodeId}s
	 */
	public Set<ScopeNodeId> resolveAccessibleScopeNodeIds(TenantId tenantId, List<ScopeNodeId> assignedScopeNodeIds) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		if (assignedScopeNodeIds == null || assignedScopeNodeIds.isEmpty()) {
			return Set.of();
		}

		Set<ScopeNodeId> result = new HashSet<>();
		for (ScopeNodeId assignedId : assignedScopeNodeIds) {
			if (assignedId != null) {
				scopeNodeRepository.findById(assignedId).ifPresent(node -> {
					if (node.getTenantId().equals(tenantId)) {
						result.add(node.getId());
						List<ScopeNode> descendants = scopeNodeRepository.findDescendantsByPathPrefix(node.getPath());
						for (ScopeNode desc : descendants) {
							result.add(desc.getId());
						}
					}
				});
			}
		}
		return Set.copyOf(result);
	}

	/**
	 * Checks if a target scope node is accessible given a list of assigned scope nodes.
	 *
	 * @param tenantId            the tenant ID
	 * @param assignedScopeNodeIds list of assigned scope node IDs
	 * @param targetScopeNodeId   the target node ID to check
	 * @return true if accessible
	 */
	public boolean isScopeAccessible(TenantId tenantId, List<ScopeNodeId> assignedScopeNodeIds, ScopeNodeId targetScopeNodeId) {
		if (targetScopeNodeId == null) {
			return false;
		}
		Set<ScopeNodeId> accessible = resolveAccessibleScopeNodeIds(tenantId, assignedScopeNodeIds);
		return accessible.contains(targetScopeNodeId);
	}

	/**
	 * Evaluates whether a target materialized path is covered by any of the assigned accessible path prefixes.
	 *
	 * @param accessiblePathPrefixes set of accessible materialized path prefixes
	 * @param targetPath             the target node's materialized path
	 * @return true if the target path starts with any assigned prefix
	 */
	public static boolean isPathAccessible(Set<String> accessiblePathPrefixes, String targetPath) {
		if (accessiblePathPrefixes == null || accessiblePathPrefixes.isEmpty() || targetPath == null || targetPath.isBlank()) {
			return false;
		}
		return accessiblePathPrefixes.stream().anyMatch(targetPath::startsWith);
	}
}
