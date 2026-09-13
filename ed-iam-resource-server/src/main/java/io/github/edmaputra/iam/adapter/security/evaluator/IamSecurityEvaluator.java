package io.github.edmaputra.iam.adapter.security.evaluator;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Spring Security SpEL evaluator bean named {@code "iam"}.
 *
 * <p>Enables declarative method-level expressions, such as:
 * <pre>{@code
 * @PreAuthorize("@iam.hasPermission('iam:user:create')")
 * @PreAuthorize("@iam.hasPermission('CLINICAL_WRITE') and @iam.canAccessScope(#departmentId)")
 * }</pre>
 *
 * @author edmaputra
 * @since 0.1.0
 */
public class IamSecurityEvaluator {

	private final CurrentActorProvider currentActorProvider;

	public IamSecurityEvaluator(CurrentActorProvider currentActorProvider) {
		this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "CurrentActorProvider must not be null.");
	}

	/**
	 * Checks if the authenticated actor has the specified permission.
	 * Returns {@code true} if the actor is a platform superadmin.
	 *
	 * @param permission the required permission code
	 * @return true if granted, false otherwise
	 */
	public boolean hasPermission(String permission) {
		if (permission == null || permission.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.hasPermission(permission)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has at least one of the specified permissions.
	 *
	 * @param permissions the permission codes to check
	 * @return true if at least one permission is granted, false otherwise
	 */
	public boolean hasAnyPermission(String... permissions) {
		if (permissions == null || permissions.length == 0) {
			return false;
		}
		return currentActor().map(actor -> {
			if (actor.isPlatformSuperAdmin()) {
				return true;
			}
			for (String permission : permissions) {
				if (actor.hasPermission(permission)) {
					return true;
				}
			}
			return false;
		}).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has all of the specified permissions.
	 *
	 * @param permissions the permission codes to check
	 * @return true if all permissions are granted, false otherwise
	 */
	public boolean hasAllPermissions(String... permissions) {
		if (permissions == null || permissions.length == 0) {
			return false;
		}
		return currentActor().map(actor -> {
			if (actor.isPlatformSuperAdmin()) {
				return true;
			}
			for (String permission : permissions) {
				if (!actor.hasPermission(permission)) {
					return false;
				}
			}
			return true;
		}).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has access to the target scope hierarchy node.
	 *
	 * @param scopeNodeId target scope node UUID
	 * @return true if scope is accessible, false otherwise
	 */
	public boolean canAccessScope(UUID scopeNodeId) {
		if (scopeNodeId == null) {
			return false;
		}
		return currentActor().map(actor -> actor.canAccessScope(scopeNodeId)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor is a platform superadmin.
	 *
	 * @return true if platform superadmin, false otherwise
	 */
	public boolean isPlatformSuperAdmin() {
		return currentActor().map(CurrentActor::isPlatformSuperAdmin).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has tenant-wide access.
	 *
	 * @return true if tenant-wide or platform superadmin, false otherwise
	 */
	public boolean isTenantWide() {
		return currentActor().map(CurrentActor::isTenantWide).orElse(false);
	}

	/**
	 * Checks if the authenticated actor has the specified role code.
	 *
	 * @param role the role code
	 * @return true if assigned, false otherwise
	 */
	public boolean hasRole(String role) {
		if (role == null || role.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.roles() != null && actor.roles().contains(role)).orElse(false);
	}

	/**
	 * Checks if the authenticated actor belongs to the specified group code.
	 *
	 * @param group the group code
	 * @return true if member, false otherwise
	 */
	public boolean isInGroup(String group) {
		if (group == null || group.isBlank()) {
			return false;
		}
		return currentActor().map(actor -> actor.groups() != null && actor.groups().contains(group)).orElse(false);
	}

	private Optional<CurrentActor> currentActor() {
		return currentActorProvider.currentActor();
	}
}
