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
 * @since 0.0.1
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
	@DisplayName("Should grant global access for Platform SuperAdmin with null tenant")
	void shouldGrantGlobalAccessForPlatformSuperAdmin() {
		User superAdmin = User.create("super@platform.org", "hash", "Super Admin", true);
		TenantId tenant1 = TenantId.generate();

		UserRoleAssignment ua = UserRoleAssignment.createTenantWide(superAdmin.getId(), RoleId.generate(), tenant1);
		when(userRoleAssignmentRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of(ua));
		when(userGroupMembershipRepository.findAllByUserId(superAdmin.getId())).thenReturn(List.of());

		EffectiveAccess access = resolver.resolve(superAdmin, null);

		assertThat(access.platformSuperAdmin()).isTrue();
		assertThat(access.tenantWide()).isTrue();
		assertThat(access.tenantId()).isNull();
		assertThat(access.roles()).containsExactly("PLATFORM_SUPERADMIN");
		assertThat(access.permissions()).containsExactly("*");
		assertThat(access.accessibleScopePaths()).containsExactly("/");
		assertThat(access.availableTenants()).containsExactly(tenant1);
	}

	@Test
	@DisplayName("Should auto-resolve single tenant for normal user when tenantId is null")
	void shouldAutoResolveSingleTenant() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("user@metro.org", "hash", "User Metro", false);
		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(tenantId, "DOCTOR", "Doctor Role", "Desc", Set.of("PATIENT_READ"));

		UserRoleAssignment ua = UserRoleAssignment.createTenantWide(user.getId(), roleId, tenantId);
		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(ua));
		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(roleRepository.findAllByIds(Set.of(roleId))).thenReturn(List.of(role));

		EffectiveAccess access = resolver.resolve(user, null);

		assertThat(access.tenantId()).isEqualTo(tenantId);
		assertThat(access.roles()).containsExactly("DOCTOR");
		assertThat(access.permissions()).containsExactly("PATIENT_READ");
		assertThat(access.tenantWide()).isTrue();
	}

	@Test
	@DisplayName("Should resolve direct and group-inherited roles and permissions in specified tenant")
	void shouldResolveDirectAndGroupInheritedRoles() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("nurse@metro.org", "hash", "Nurse", false);

		// Direct role
		RoleId directRoleId = RoleId.generate();
		Role directRole = Role.createCustom(tenantId, "NURSE", "Nurse Role", "Desc", Set.of("CHART_READ"));
		UserRoleAssignment directAssignment = UserRoleAssignment.createTenantWide(user.getId(), directRoleId, tenantId);

		// Group role
		Group group = Group.create(tenantId, "CLINICAL", "Clinical Dept", "Desc", null);
		GroupId groupId = group.getId();
		UserGroupMembership membership = UserGroupMembership.of(groupId, user.getId());

		RoleId groupRoleId = RoleId.generate();
		Role groupRole = Role.createCustom(tenantId, "STAFF", "Staff Role", "Desc", Set.of("VITALS_WRITE"));
		GroupRoleAssignment groupAssignment = GroupRoleAssignment.createTenantWide(groupId, groupRoleId, tenantId);

		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(directAssignment));
		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of(membership));
		when(groupRepository.findAllByIds(List.of(groupId))).thenReturn(List.of(group));
		when(groupRoleAssignmentRepository.findAllByGroupIds(List.of(groupId))).thenReturn(List.of(groupAssignment));
		when(roleRepository.findAllByIds(Set.of(directRoleId, groupRoleId))).thenReturn(List.of(directRole, groupRole));

		EffectiveAccess access = resolver.resolve(user, tenantId);

		assertThat(access.tenantId()).isEqualTo(tenantId);
		assertThat(access.roles()).containsExactlyInAnyOrder("NURSE", "STAFF");
		assertThat(access.permissions()).containsExactlyInAnyOrder("CHART_READ", "VITALS_WRITE");
		assertThat(access.groups()).containsExactly("CLINICAL");
		assertThat(access.tenantWide()).isTrue();
	}

	@Test
	@DisplayName("Should resolve scoped role assignments with subtree inheritance")
	void shouldResolveScopedRoleAssignments() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("doctor@metro.org", "hash", "Doctor", false);

		ScopeNode parentNode = ScopeNode.createRoot(tenantId, "ROOT", "Root Clinic");
		ScopeNodeId parentScopeId = parentNode.getId();
		ScopeNode childNode = ScopeNode.createChild(tenantId, parentNode, "CARDIO", "Cardiology");

		RoleId roleId = RoleId.generate();
		Role role = Role.createCustom(tenantId, "CARDIO_DOC", "Cardio Doctor", "Desc", Set.of("ECG_READ"));

		UserRoleAssignment ua = UserRoleAssignment.create(user.getId(), roleId, tenantId, parentScopeId, true);

		when(userRoleAssignmentRepository.findAllByUserId(user.getId())).thenReturn(List.of(ua));
		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(roleRepository.findAllByIds(Set.of(roleId))).thenReturn(List.of(role));

		when(scopeNodeRepository.findById(parentScopeId)).thenReturn(Optional.of(parentNode));
		when(scopeNodeRepository.findDescendantsByPathPrefix(parentNode.getPath())).thenReturn(List.of(childNode));

		EffectiveAccess access = resolver.resolve(user, tenantId);

		assertThat(access.tenantWide()).isFalse();
		assertThat(access.accessibleScopeNodeIds()).containsExactlyInAnyOrder(parentNode.getId().value(), childNode.getId().value());
		assertThat(access.accessibleScopePaths()).containsExactlyInAnyOrder(parentNode.getPath(), childNode.getPath());
	}
}
