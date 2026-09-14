package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantOwned;
import lombok.Getter;

/**
 * Pure domain entity representing an assignment of a {@link Role} to a {@link Group},
 * inheriting permissions to all members of the group.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Getter
public class GroupRoleAssignment implements TenantOwned {

	private final GroupRoleAssignmentId id;
	private final GroupId groupId;
	private final RoleId roleId;
	private final TenantId tenantId;
	private final ScopeNodeId scopeNodeId;
	private final boolean inheritChildren;
	private final Instant createdAt;

	/**
	 * Canonical constructor for reconstructing group role assignments.
	 *
	 * @param id              the unique assignment ID
	 * @param groupId         the group ID
	 * @param roleId          the role ID
	 * @param tenantId        the owning tenant ID
	 * @param scopeNodeId     optional scope node ID
	 * @param inheritChildren whether permissions cascade downward to descendant scopes
	 * @param createdAt       creation timestamp
	 */
	public GroupRoleAssignment(
			GroupRoleAssignmentId id,
			GroupId groupId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren,
			Instant createdAt) {
		this.id = Objects.requireNonNull(id, "Assignment ID must not be null.");
		this.groupId = Objects.requireNonNull(groupId, "GroupId must not be null.");
		this.roleId = Objects.requireNonNull(roleId, "RoleId must not be null.");
		this.tenantId = Objects.requireNonNull(tenantId, "TenantId must not be null.");
		this.scopeNodeId = scopeNodeId;
		this.inheritChildren = inheritChildren;
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
	}

	/**
	 * Factory method creating a scoped group role assignment.
	 *
	 * @param groupId         the group ID
	 * @param roleId          the role ID
	 * @param tenantId        the tenant ID
	 * @param scopeNodeId     the scope node ID
	 * @param inheritChildren whether to inherit across descendant scopes
	 * @return new {@link GroupRoleAssignment}
	 */
	public static GroupRoleAssignment create(
			GroupId groupId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren) {
		Objects.requireNonNull(scopeNodeId, "ScopeNodeId must not be null for scoped group assignment.");
		return new GroupRoleAssignment(
				GroupRoleAssignmentId.generate(),
				groupId,
				roleId,
				tenantId,
				scopeNodeId,
				inheritChildren,
				Instant.now());
	}

	/**
	 * Factory method creating a tenant-wide group role assignment.
	 *
	 * @param groupId  the group ID
	 * @param roleId   the role ID
	 * @param tenantId the tenant ID
	 * @return new {@link GroupRoleAssignment}
	 */
	public static GroupRoleAssignment createTenantWide(GroupId groupId, RoleId roleId, TenantId tenantId) {
		return new GroupRoleAssignment(
				GroupRoleAssignmentId.generate(),
				groupId,
				roleId,
				tenantId,
				null,
				false,
				Instant.now());
	}

	/**
	 * Factory method creating a tenant-wide group role assignment (alias).
	 *
	 * @param groupId  the group ID
	 * @param roleId   the role ID
	 * @param tenantId the tenant ID
	 * @return new {@link GroupRoleAssignment}
	 */
	public static GroupRoleAssignment forTenant(GroupId groupId, RoleId roleId, TenantId tenantId) {
		return createTenantWide(groupId, roleId, tenantId);
	}

	/**
	 * Factory method creating a scope-bounded group role assignment (alias).
	 *
	 * @param groupId         the group ID
	 * @param roleId          the role ID
	 * @param tenantId        the tenant ID
	 * @param scopeNodeId     the scope node ID
	 * @param inheritChildren whether to inherit across descendant scopes
	 * @return new {@link GroupRoleAssignment}
	 */
	public static GroupRoleAssignment forScope(
			GroupId groupId,
			RoleId roleId,
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren) {
		return create(groupId, roleId, tenantId, scopeNodeId, inheritChildren);
	}

	@Override
	public TenantId tenantId() {
		return this.tenantId;
	}

	/**
	 * Returns true if this assignment provides unrestricted access across all tenant scopes.
	 *
	 * @return true if tenant-wide
	 */
	public boolean isTenantWide() {
		return this.scopeNodeId == null;
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
