package io.github.edmaputra.iam.domain.security;

import java.util.Set;
import java.util.UUID;

/**
 * Test fixture record implementing pure domain {@link CurrentActor}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public record TestCurrentActor(
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

	@Override
	public boolean isPlatformSuperAdmin() {
		return platformSuperAdmin;
	}

	@Override
	public boolean isTenantWide() {
		return tenantWide;
	}

	public static TestCurrentActor superAdmin(UUID userId, String email) {
		return new TestCurrentActor(userId, email, null, true, true, Set.of(), Set.of("SUPER_ADMIN"), Set.of("*"), Set.of(), Set.of());
	}

	public static TestCurrentActor tenantUser(UUID userId, String email, UUID tenantId, Set<String> roles, Set<String> permissions) {
		return new TestCurrentActor(userId, email, tenantId, false, false, Set.of(), roles, permissions, Set.of(), Set.of());
	}

	public static TestCurrentActor tenantWide(UUID userId, String email, UUID tenantId, Set<String> roles, Set<String> permissions) {
		return new TestCurrentActor(userId, email, tenantId, false, true, Set.of(), roles, permissions, Set.of(), Set.of());
	}

	public static TestCurrentActor scoped(UUID userId, String email, UUID tenantId, Set<UUID> scopeIds, Set<String> scopePaths) {
		return new TestCurrentActor(userId, email, tenantId, false, false, Set.of(), Set.of(), Set.of(), scopeIds, scopePaths);
	}
}
