package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EffectiveAccessResolver}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class EffectiveAccessResolverTest {

	private UserGroupMembershipRepository userGroupMembershipRepository;
	private GroupRepository groupRepository;
	private UserRoleAssignmentRepository userRoleAssignmentRepository;
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private RoleRepository roleRepository;
	private ScopeNodeRepository scopeNodeRepository;
	private ScopeSubtreeResolver scopeSubtreeResolver;

	private EffectiveAccessResolver resolver;

	@BeforeEach
	void setUp() {
		userGroupMembershipRepository = mock(UserGroupMembershipRepository.class);
		groupRepository = mock(GroupRepository.class);
		userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
		groupRoleAssignmentRepository = mock(GroupRoleAssignmentRepository.class);
		roleRepository = mock(RoleRepository.class);
		scopeNodeRepository = mock(ScopeNodeRepository.class);
		scopeSubtreeResolver = mock(ScopeSubtreeResolver.class);

		resolver = new EffectiveAccessResolver(
				userGroupMembershipRepository,
				groupRepository,
				userRoleAssignmentRepository,
				groupRoleAssignmentRepository,
				roleRepository,
				scopeNodeRepository,
				scopeSubtreeResolver);
	}

	@Test
	@DisplayName("Should resolve global platform superadmin access when tenant is null")
	void shouldResolvePlatformSuperadminGlobal() {
		User superAdmin = User.create("admin@platform.org", "secret", "Super Admin", true);

		when(userGroupMembershipRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of());
		when(userRoleAssignmentRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of());

		EffectiveAccess access = resolver.resolve(superAdmin, null);

		assertThat(access.platformSuperAdmin()).isTrue();
		assertThat(access.permissions()).contains("*");
		assertThat(access.roles()).contains("PLATFORM_SUPERADMIN");
		assertThat(access.accessibleScopePaths()).contains("/");
		assertThat(access.tenantId()).isNull();
	}

	@Test
	@DisplayName("Should auto-resolve tenant when tenantId is null and user has exactly one available tenant")
	void shouldAutoResolveSingleTenant() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("single@test.org", "secret", "Single Tenant User", false);
		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(tenantId, "EDITOR", "Editor", "Desc", Set.of("doc:write"));

		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(user.getId(), roleId, tenantId);

		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(assignment));
		when(roleRepository.findAllByIds(any())).thenReturn(List.of(role));
		when(scopeNodeRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

		EffectiveAccess access = resolver.resolve(user, null);

		assertThat(access.tenantId()).isEqualTo(tenantId);
		assertThat(access.tenantWide()).isTrue();
		assertThat(access.permissions()).contains("doc:write");
	}

	@Test
	@DisplayName("Should resolve scoped access combining direct and group assignments with hierarchy")
	void shouldResolveScopedAccessWithGroupAndDirect() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("bob@test.org", "secret", "Bob", false);

		// Direct assignment to scope node 1 without children
		RoleId directRoleId = RoleId.generate();
		Role directRole = Role.createCustom(tenantId, "VIEWER", "Viewer", "Desc", Set.of("read"));
		ScopeNode node1 = ScopeNode.createRoot(tenantId, "root", "Root");
		UserRoleAssignment directAssignment = UserRoleAssignment.create(
				user.getId(), directRoleId, tenantId, node1.getId(), false);

		Group group = Group.create(tenantId, "TEAM", "Team", "Desc", null);
		GroupId groupId = group.getId();
		UserGroupMembership membership = UserGroupMembership.of(groupId, user.getId());

		RoleId groupRoleId = RoleId.generate();
		Role groupRole = Role.createCustom(tenantId, "MEMBER", "Member", "Desc", Set.of("comment"));
		ScopeNode node2 = ScopeNode.createChild(tenantId, node1, "child", "Child");
		ScopeNode node3 = ScopeNode.createChild(tenantId, node2, "subchild", "SubChild");
		GroupRoleAssignment groupAssignment = GroupRoleAssignment.create(
				groupId, groupRoleId, tenantId, node2.getId(), true);

		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of(membership));
		when(groupRepository.findAllByIds(List.of(groupId))).thenReturn(List.of(group));
		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(directAssignment));
		when(groupRoleAssignmentRepository.findAllByGroupIds(List.of(groupId))).thenReturn(List.of(groupAssignment));

		when(roleRepository.findAllByIds(any())).thenReturn(List.of(directRole, groupRole));

		when(scopeNodeRepository.findById(node1.getId())).thenReturn(Optional.of(node1));
		when(scopeNodeRepository.findById(node2.getId())).thenReturn(Optional.of(node2));
		when(scopeNodeRepository.findDescendantsByPathPrefix(node2.getPath())).thenReturn(List.of(node3));

		EffectiveAccess access = resolver.resolve(user, tenantId);

		assertThat(access.tenantId()).isEqualTo(tenantId);
		assertThat(access.tenantWide()).isFalse();
		assertThat(access.permissions()).containsExactlyInAnyOrder("read", "comment");
		assertThat(access.roles()).containsExactlyInAnyOrder("VIEWER", "MEMBER");
		assertThat(access.groups()).containsExactly("TEAM");
		assertThat(access.accessibleScopeNodeIds()).contains(node1.getId().value(), node2.getId().value(), node3.getId().value());
	}

	@Test
	@DisplayName("Should ignore scope node when node belongs to different tenant")
	void shouldIgnoreScopeNodeFromDifferentTenant() {
		TenantId tenantA = TenantId.generate();
		TenantId tenantB = TenantId.generate();
		User user = User.create("user@test.org", "secret", "User", false);

		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(tenantA, "VIEWER", "Viewer", "Desc", Set.of("read"));
		ScopeNode foreignNode = ScopeNode.createRoot(tenantB, "foreign", "Foreign");
		UserRoleAssignment assignment = UserRoleAssignment.create(
				user.getId(), roleId, tenantA, foreignNode.getId(), false);

		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(assignment));
		when(roleRepository.findAllByIds(any())).thenReturn(List.of(role));
		when(scopeNodeRepository.findById(foreignNode.getId())).thenReturn(Optional.of(foreignNode));

		EffectiveAccess access = resolver.resolve(user, tenantA);

		assertThat(access.accessibleScopeNodeIds()).doesNotContain(foreignNode.getId().value());
	}

	@Test
	@DisplayName("Should resolve tenant-wide access for platform superadmin within a specific tenant")
	void shouldResolvePlatformSuperadminWithTenant() {
		TenantId tenantId = TenantId.generate();
		User superAdmin = User.create("admin@platform.org", "secret", "Super Admin", true);
		ScopeNode root = ScopeNode.createRoot(tenantId, "root", "Root");

		when(userGroupMembershipRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of());
		when(userRoleAssignmentRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of());
		when(scopeNodeRepository.findAllByTenantId(tenantId)).thenReturn(List.of(root));

		EffectiveAccess access = resolver.resolve(superAdmin, tenantId);

		assertThat(access.platformSuperAdmin()).isTrue();
		assertThat(access.tenantWide()).isTrue();
		assertThat(access.roles()).contains("PLATFORM_SUPERADMIN");
		assertThat(access.permissions()).contains("*");
		assertThat(access.accessibleScopeNodeIds()).contains(root.getId().value());
	}

	@Test
	@DisplayName("Should resolve tenant-wide access from tenant-wide group role assignment")
	void shouldResolveTenantWideFromGroupAssignment() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("groupuser@test.org", "secret", "Group User", false);
		Group group = Group.create(tenantId, "ADMINS", "Admins", "Desc", null);
		UserGroupMembership membership = UserGroupMembership.of(group.getId(), user.getId());
		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(tenantId, "ADMIN_ROLE", "Admin Role", "Desc", Set.of("all"));
		GroupRoleAssignment groupAssignment = GroupRoleAssignment.createTenantWide(group.getId(), roleId, tenantId);
		ScopeNode root = ScopeNode.createRoot(tenantId, "root", "Root");

		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of(membership));
		when(groupRepository.findAllByIds(List.of(group.getId()))).thenReturn(List.of(group));
		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(groupRoleAssignmentRepository.findAllByGroupIds(List.of(group.getId()))).thenReturn(List.of(groupAssignment));
		when(roleRepository.findAllByIds(any())).thenReturn(List.of(role));
		when(scopeNodeRepository.findAllByTenantId(tenantId)).thenReturn(List.of(root));

		EffectiveAccess access = resolver.resolve(user, tenantId);

		assertThat(access.tenantWide()).isTrue();
		assertThat(access.permissions()).contains("all");
		assertThat(access.accessibleScopeNodeIds()).contains(root.getId().value());
	}
}
