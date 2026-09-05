package io.github.edmaputra.iam.application.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * User profile and active authorization claims returned by {@code /api/v1/auth/me} and login endpoints.
 *
 * @param id                     the user ID
 * @param email                  the user's email address
 * @param fullName               the user's full name
 * @param tenantId               the current tenant UUID context (or null)
 * @param platformSuperAdmin     flag indicating if user has platform superadmin privileges
 * @param tenantWide             flag indicating if user has unrestricted tenant-wide scope
 * @param groups                 set of active group codes
 * @param roles                  set of active role codes
 * @param permissions            set of distinct permission codes
 * @param accessibleScopeNodeIds set of accessible scope node UUIDs
 * @param accessibleScopePaths   set of accessible materialized path prefixes
 * @author edmaputra
 * @since 1.0.0
 */
public record UserProfileResponse(
		UUID id,
		String email,
		String fullName,
		UUID tenantId,
		boolean platformSuperAdmin,
		boolean tenantWide,
		Set<String> groups,
		Set<String> roles,
		Set<String> permissions,
		Set<UUID> accessibleScopeNodeIds,
		Set<String> accessibleScopePaths) {

	public UserProfileResponse {
		Objects.requireNonNull(id, "User ID must not be null.");
		Objects.requireNonNull(email, "Email must not be null.");
		Objects.requireNonNull(fullName, "FullName must not be null.");
		groups = groups == null ? Set.of() : Set.copyOf(groups);
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		accessibleScopeNodeIds = accessibleScopeNodeIds == null ? Set.of() : Set.copyOf(accessibleScopeNodeIds);
		accessibleScopePaths = accessibleScopePaths == null ? Set.of() : Set.copyOf(accessibleScopePaths);
	}
}
