package io.github.edmaputra.iam.application.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Resolved access summary for a user within a target tenant, combining direct and group-inherited permissions and scopes.
 *
 * @param userId                 the unique user ID
 * @param email                  the user email
 * @param tenantId               the target tenant ID (null for platform superadmin)
 * @param platformSuperAdmin     flag indicating platform superadmin status
 * @param tenantWide             flag indicating unrestricted access across all tenant scopes
 * @param availableTenants       set of distinct accessible tenant IDs for the user
 * @param groups                 effective group codes assigned to the user
 * @param roles                  effective role codes resolved from direct and group assignments
 * @param permissions            flattened distinct permission strings
 * @param accessibleScopeNodeIds resolved accessible scope node UUIDs
 * @param accessibleScopePaths   resolved accessible scope materialized path prefixes
 * @author edmaputra
 * @since 0.0.1
 */
public record EffectiveAccess(
		UserId userId,
		String email,
		TenantId tenantId,
		boolean platformSuperAdmin,
		boolean tenantWide,
		Set<TenantId> availableTenants,
		Set<String> groups,
		Set<String> roles,
		Set<String> permissions,
		Set<UUID> accessibleScopeNodeIds,
		Set<String> accessibleScopePaths) {

	public EffectiveAccess {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");
		availableTenants = availableTenants == null ? Set.of() : Set.copyOf(availableTenants);
		groups = groups == null ? Set.of() : Set.copyOf(groups);
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		accessibleScopeNodeIds = accessibleScopeNodeIds == null ? Set.of() : Set.copyOf(accessibleScopeNodeIds);
		accessibleScopePaths = accessibleScopePaths == null ? Set.of() : Set.copyOf(accessibleScopePaths);
	}

	/**
	 * Secondary constructor maintaining compatibility with callers that omit availableTenants.
	 */
	public EffectiveAccess(
			UserId userId,
			String email,
			TenantId tenantId,
			boolean platformSuperAdmin,
			boolean tenantWide,
			Set<String> groups,
			Set<String> roles,
			Set<String> permissions,
			Set<UUID> accessibleScopeNodeIds,
			Set<String> accessibleScopePaths) {
		this(userId, email, tenantId, platformSuperAdmin, tenantWide, Set.of(), groups, roles, permissions, accessibleScopeNodeIds, accessibleScopePaths);
	}
}
