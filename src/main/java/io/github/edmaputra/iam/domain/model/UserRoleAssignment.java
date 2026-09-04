package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import lombok.Getter;

/**
 * Pure domain entity representing a direct assignment of a {@link Role} to a {@link User},
 * scoped globally, tenant-wide, or bounded to a specific {@link ScopeNode} hierarchy.
 *
 * @author edmaputra
 */
@Getter
public class UserRoleAssignment {

	private final UserRoleAssignmentId id;
	private final UserId userId;
	private final RoleId roleId;
	private final TenantId tenantId;
	private final ScopeNodeId scopeNodeId;
	private final boolean inheritChildren;
	private final Instant createdAt;

	/**
	 * Canonical constructor for reconstructing user role assignments.
	 *
	 * @param id              the unique assignment ID
	 * @param userId          the assigned user ID
	 * @param roleId          the assigned role ID
	 * @param tenantId        optional tenant ID context
	 * @param scopeNodeId     optional scope node ID
	 * @param inheritChildren whether permissions cascade downward to descendant scopes
	 * @param createdAt       creation timestamp
	 */
	public UserRoleAssignment(
			UserRoleAssignmentId id,
			UserId userId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren,
			Instant createdAt) {
		this.id = Objects.requireNonNull(id, "Assignment ID must not be null.");
		this.userId = Objects.requireNonNull(userId, "UserId must not be null.");
		this.roleId = Objects.requireNonNull(roleId, "RoleId must not be null.");
		this.tenantId = tenantId;
		this.scopeNodeId = scopeNodeId;
		this.inheritChildren = inheritChildren;
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
	}

	/**
	 * Factory method creating a scoped role assignment.
	 *
	 * @param userId          the user ID
	 * @param roleId          the role ID
	 * @param tenantId        the tenant ID
	 * @param scopeNodeId     the scope node ID
	 * @param inheritChildren whether permissions cascade downward
	 * @return new {@link UserRoleAssignment}
	 */
	public static UserRoleAssignment create(
			UserId userId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren) {
		Objects.requireNonNull(tenantId, "TenantId must not be null for scoped assignment.");
		Objects.requireNonNull(scopeNodeId, "ScopeNodeId must not be null for scoped assignment.");
		return new UserRoleAssignment(
				UserRoleAssignmentId.generate(),
				userId,
				roleId,
				tenantId,
				scopeNodeId,
				inheritChildren,
				Instant.now());
	}

	/**
	 * Factory method creating a tenant-wide role assignment.
	 *
	 * @param userId   the user ID
	 * @param roleId   the role ID
	 * @param tenantId the tenant ID
	 * @return new {@link UserRoleAssignment}
	 */
	public static UserRoleAssignment createTenantWide(UserId userId, RoleId roleId, TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null for tenant-wide assignment.");
		return new UserRoleAssignment(
				UserRoleAssignmentId.generate(),
				userId,
				roleId,
				tenantId,
				null,
				false,
				Instant.now());
	}

	/**
	 * Factory method creating a global superadmin assignment (unscoped).
	 *
	 * @param userId the user ID
	 * @param roleId the role ID
	 * @return new {@link UserRoleAssignment}
	 */
	public static UserRoleAssignment createGlobalSuperadmin(UserId userId, RoleId roleId) {
		return new UserRoleAssignment(
				UserRoleAssignmentId.generate(),
				userId,
				roleId,
				null,
				null,
				false,
				Instant.now());
	}

	/**
	 * Factory method creating a tenant-wide role assignment (alias).
	 *
	 * @param userId   the user ID
	 * @param roleId   the role ID
	 * @param tenantId the tenant ID
	 * @return new {@link UserRoleAssignment}
	 */
	public static UserRoleAssignment forTenant(UserId userId, RoleId roleId, TenantId tenantId) {
		return createTenantWide(userId, roleId, tenantId);
	}

	/**
	 * Factory method creating a scope-bounded role assignment (alias).
	 *
	 * @param userId          the user ID
	 * @param roleId          the role ID
	 * @param tenantId        the tenant ID
	 * @param scopeNodeId     the scope node ID
	 * @param inheritChildren whether to inherit across descendant scopes
	 * @return new {@link UserRoleAssignment}
	 */
	public static UserRoleAssignment forScope(
			UserId userId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren) {
		return create(userId, roleId, tenantId, scopeNodeId, inheritChildren);
	}

	/**
	 * Returns true if this assignment is global (not bound to any tenant).
	 *
	 * @return true if global
	 */
	public boolean isGlobal() {
		return this.tenantId == null;
	}

	/**
	 * Returns true if this assignment provides unrestricted access across all tenant scopes.
	 *
	 * @return true if tenant-wide or global
	 */
	public boolean isTenantWide() {
		return this.scopeNodeId == null;
	}

	/**
	 * Returns the optional tenant ID context.
	 *
	 * @return optional {@link TenantId}
	 */
	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	/**
	 * Returns the optional scope node ID boundary.
	 *
	 * @return optional {@link ScopeNodeId}
	 */
	public Optional<ScopeNodeId> optionalScopeNodeId() {
		return Optional.ofNullable(scopeNodeId);
	}
}
