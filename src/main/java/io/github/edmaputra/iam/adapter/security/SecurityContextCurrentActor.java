package io.github.edmaputra.iam.adapter.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.edmaputra.iam.domain.security.CurrentActor;

/**
 * Concrete implementation of the pure domain {@link CurrentActor} contract created from verified JWT claims.
 *
 * @param userId                 the unique user UUID
 * @param email                  the user email
 * @param tenantId               the current tenant UUID (null for platform superadmin global context)
 * @param platformSuperAdmin     flag indicating platform superadmin privilege
 * @param tenantWide             flag indicating unrestricted access across the tenant
 * @param groups                 set of active group codes
 * @param roles                  set of active role codes
 * @param permissions            set of distinct permission strings
 * @param accessibleScopeNodeIds set of accessible scope node UUIDs
 * @param accessibleScopePaths   set of accessible materialized path prefixes
 * @author edmaputra
 * @since 1.0.0
 */
public record SecurityContextCurrentActor(
		UUID userId,
		String email,
		UUID tenantId,
		boolean platformSuperAdmin,
		boolean tenantWide,
		Set<String> groups,
		Set<String> roles,
		Set<String> permissions,
		Set<UUID> accessibleScopeNodeIds,
		Set<String> accessibleScopePaths) implements CurrentActor {

	public SecurityContextCurrentActor {
		Objects.requireNonNull(userId, "UserId must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");
		groups = groups == null ? Set.of() : Set.copyOf(groups);
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		accessibleScopeNodeIds = accessibleScopeNodeIds == null ? Set.of() : Set.copyOf(accessibleScopeNodeIds);
		accessibleScopePaths = accessibleScopePaths == null ? Set.of() : Set.copyOf(accessibleScopePaths);
	}

	@Override
	public boolean isPlatformSuperAdmin() {
		return platformSuperAdmin;
	}

	@Override
	public boolean isTenantWide() {
		return tenantWide;
	}

	@Override
	public Set<String> groups() {
		return groups;
	}

	@Override
	public Set<String> roles() {
		return roles;
	}

	@Override
	public Set<String> permissions() {
		return permissions;
	}

	@Override
	public Set<UUID> accessibleScopeNodeIds() {
		return accessibleScopeNodeIds;
	}
}
