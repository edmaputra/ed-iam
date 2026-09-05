package io.github.edmaputra.iam.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Access resolution engine that computes the composite effective permissions, roles, and scope boundaries for a user.
 * Merges direct user assignments with group-inherited assignments and resolves downward scope hierarchies.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class EffectiveAccessResolver {

	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final GroupRepository groupRepository;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private final RoleRepository roleRepository;
	private final ScopeNodeRepository scopeNodeRepository;
	private final ScopeSubtreeResolver scopeSubtreeResolver;

	/**
	 * Constructs the effective access resolver with required repositories and subtree engine.
	 *
	 * @param userGroupMembershipRepository user group membership repository
	 * @param groupRepository               group repository
	 * @param userRoleAssignmentRepository  user role assignment repository
	 * @param groupRoleAssignmentRepository group role assignment repository
	 * @param roleRepository                role repository
	 * @param scopeNodeRepository           scope node repository
	 * @param scopeSubtreeResolver          scope subtree calculation resolver
	 */
	public EffectiveAccessResolver(
			UserGroupMembershipRepository userGroupMembershipRepository,
			GroupRepository groupRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			RoleRepository roleRepository,
			ScopeNodeRepository scopeNodeRepository,
			ScopeSubtreeResolver scopeSubtreeResolver) {
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository, "UserGroupMembershipRepository must not be null.");
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.userRoleAssignmentRepository = Objects.requireNonNull(userRoleAssignmentRepository, "UserRoleAssignmentRepository must not be null.");
		this.groupRoleAssignmentRepository = Objects.requireNonNull(groupRoleAssignmentRepository, "GroupRoleAssignmentRepository must not be null.");
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.scopeNodeRepository = Objects.requireNonNull(scopeNodeRepository, "ScopeNodeRepository must not be null.");
		this.scopeSubtreeResolver = Objects.requireNonNull(scopeSubtreeResolver, "ScopeSubtreeResolver must not be null.");
	}

	/**
	 * Computes the complete {@link EffectiveAccess} for a given user within a tenant context.
	 *
	 * @param user     the user entity
	 * @param tenantId the tenant context ID (or null for platform superadmin global context)
	 * @return the resolved {@link EffectiveAccess}
	 */
	public EffectiveAccess resolve(User user, TenantId tenantId) {
		Objects.requireNonNull(user, "User must not be null.");

		// 1. Resolve User Groups across all tenants
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(user.getId());
		List<GroupId> groupIds = memberships.stream().map(UserGroupMembership::groupId).toList();
		List<Group> allUserGroups = groupIds.isEmpty() ? List.of() : groupRepository.findAllByIds(groupIds);

		// 2. Direct User Role Assignments across all tenants
		List<UserRoleAssignment> allUserAssignments = userRoleAssignmentRepository.findAllByUserId(user.getId());

		// 3. Compute distinct available tenants for this user
		Set<TenantId> availableTenants = new HashSet<>();
		allUserAssignments.stream().map(UserRoleAssignment::getTenantId).filter(Objects::nonNull).forEach(availableTenants::add);
		allUserGroups.stream().map(Group::getTenantId).filter(Objects::nonNull).forEach(availableTenants::add);

		if (user.isPlatformSuperAdmin() && tenantId == null) {
			return new EffectiveAccess(
					user.getId(),
					user.getEmail(),
					null,
					true,
					true,
					availableTenants,
					Set.of(),
					Set.of("PLATFORM_SUPERADMIN"),
					Set.of("*"),
					Set.of(),
					Set.of("/"));
		}

		// 4. Auto-resolve tenant if tenantId was not supplied and user is not a platform superadmin
		final TenantId effectiveTenantId = (tenantId == null && !user.isPlatformSuperAdmin() && availableTenants.size() == 1)
				? availableTenants.iterator().next()
				: tenantId;

		List<Group> userGroups = allUserGroups;
		if (effectiveTenantId != null) {
			userGroups = userGroups.stream()
					.filter(g -> g.getTenantId().equals(effectiveTenantId))
					.toList();
		}

		Set<String> groupCodes = userGroups.stream()
				.map(Group::getCode)
				.collect(Collectors.toUnmodifiableSet());
		List<GroupId> tenantGroupIds = userGroups.stream().map(Group::getId).toList();

		List<UserRoleAssignment> userAssignments = effectiveTenantId == null
				? allUserAssignments
				: allUserAssignments.stream().filter(ua -> effectiveTenantId.equals(ua.getTenantId())).toList();

		// 5. Group Role Assignments
		List<GroupRoleAssignment> groupAssignments = tenantGroupIds.isEmpty()
				? List.of()
				: groupRoleAssignmentRepository.findAllByGroupIds(tenantGroupIds);

		if (effectiveTenantId != null) {
			groupAssignments = groupAssignments.stream()
					.filter(ga -> ga.getTenantId().equals(effectiveTenantId))
					.toList();
		}

		// 6. Resolve Roles and Permissions
		Set<RoleId> allRoleIds = new HashSet<>();
		userAssignments.forEach(ua -> allRoleIds.add(ua.getRoleId()));
		groupAssignments.forEach(ga -> allRoleIds.add(ga.getRoleId()));

		List<Role> roles = allRoleIds.isEmpty() ? List.of() : roleRepository.findAllByIds(allRoleIds);
		Set<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toSet());
		Set<String> permissions = new HashSet<>();
		roles.forEach(r -> permissions.addAll(r.permissions()));

		if (user.isPlatformSuperAdmin()) {
			roleCodes.add("PLATFORM_SUPERADMIN");
			permissions.add("*");
		}

		// 7. Evaluate Scopes
		boolean isTenantWide = user.isPlatformSuperAdmin()
				|| userAssignments.stream().anyMatch(UserRoleAssignment::isTenantWide)
				|| groupAssignments.stream().anyMatch(GroupRoleAssignment::isTenantWide);

		Set<UUID> accessibleScopeNodeIds = new HashSet<>();
		Set<String> accessibleScopePaths = new HashSet<>();

		if (effectiveTenantId != null) {
			if (isTenantWide) {
				List<ScopeNode> allNodes = scopeNodeRepository.findAllByTenantId(effectiveTenantId);
				for (ScopeNode node : allNodes) {
					accessibleScopeNodeIds.add(node.getId().value());
					accessibleScopePaths.add(node.getPath());
				}
			}
			else {
				// Resolve scopes with subtree inheritance or single node
				for (UserRoleAssignment ua : userAssignments) {
					if (ua.getScopeNodeId() != null) {
						addScopesForNode(effectiveTenantId, ua.getScopeNodeId(), ua.isInheritChildren(), accessibleScopeNodeIds, accessibleScopePaths);
					}
				}
				for (GroupRoleAssignment ga : groupAssignments) {
					if (ga.getScopeNodeId() != null) {
						addScopesForNode(effectiveTenantId, ga.getScopeNodeId(), ga.isInheritChildren(), accessibleScopeNodeIds, accessibleScopePaths);
					}
				}
			}
		}

		return new EffectiveAccess(
				user.getId(),
				user.getEmail(),
				effectiveTenantId,
				user.isPlatformSuperAdmin(),
				isTenantWide,
				availableTenants,
				groupCodes,
				roleCodes,
				permissions,
				accessibleScopeNodeIds,
				accessibleScopePaths);
	}

	private void addScopesForNode(
			TenantId tenantId,
			ScopeNodeId scopeNodeId,
			boolean inheritChildren,
			Set<UUID> targetIds,
			Set<String> targetPaths) {
		scopeNodeRepository.findById(scopeNodeId).ifPresent(node -> {
			if (node.getTenantId().equals(tenantId)) {
				targetIds.add(node.getId().value());
				targetPaths.add(node.getPath());
				if (inheritChildren) {
					List<ScopeNode> descendants = scopeNodeRepository.findDescendantsByPathPrefix(node.getPath());
					for (ScopeNode descendant : descendants) {
						targetIds.add(descendant.getId().value());
						targetPaths.add(descendant.getPath());
					}
				}
			}
		});
	}
}
