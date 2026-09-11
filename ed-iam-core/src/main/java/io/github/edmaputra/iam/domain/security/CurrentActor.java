package io.github.edmaputra.iam.domain.security;

import java.util.Set;
import java.util.UUID;

/**
 * Domain representation of the currently authenticated actor.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public interface CurrentActor {

	UUID userId();

	String email();

	UUID tenantId();

	boolean isPlatformSuperAdmin();

	boolean isTenantWide();

	Set<String> groups();

	Set<String> roles();

	Set<String> permissions();

	default boolean hasPermission(String permission) {
		if (isPlatformSuperAdmin()) {
			return true;
		}
		return permissions() != null && permissions().contains(permission);
	}

	default boolean canAccessScope(UUID targetScopeNodeId) {
		if (isPlatformSuperAdmin() || isTenantWide()) {
			return true;
		}
		if (targetScopeNodeId == null) {
			return false;
		}
		return accessibleScopeNodeIds() != null && accessibleScopeNodeIds().contains(targetScopeNodeId);
	}

	Set<UUID> accessibleScopeNodeIds();

	/**
	 * Returns the set of materialized hierarchy scope path prefixes accessible to the actor.
	 *
	 * @return set of accessible scope path strings, or empty set if none
	 */
	default Set<String> accessibleScopePaths() {
		return Set.of();
	}
}
