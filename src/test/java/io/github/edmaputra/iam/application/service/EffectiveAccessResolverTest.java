package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EffectiveAccessResolverTest {

	private UserGroupMembershipRepository userGroupMembershipRepository;
	private GroupRepository groupRepository;
	private UserRoleAssignmentRepository userRoleAssignmentRepository;
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private RoleRepository roleRepository;
	private ScopeNodeRepository scopeNodeRepository;
	private ScopeSubtreeResolver scopeSubtreeResolver;

	private EffectiveAccessResolver resolver;

	private final TenantId tenantId = TenantId.generate();

	@BeforeEach
	void setUp() {
		userGroupMembershipRepository = Mockito.mock(UserGroupMembershipRepository.class);
		groupRepository = Mockito.mock(GroupRepository.class);
		userRoleAssignmentRepository = Mockito.mock(UserRoleAssignmentRepository.class);
		groupRoleAssignmentRepository = Mockito.mock(GroupRoleAssignmentRepository.class);
		roleRepository = Mockito.mock(RoleRepository.class);
		scopeNodeRepository = Mockito.mock(ScopeNodeRepository.class);
		scopeSubtreeResolver = new ScopeSubtreeResolver(scopeNodeRepository);

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
	@DisplayName("Should resolve platform superadmin with full access")
	void shouldResolvePlatformSuperadmin() {
		User superadmin = User.create("superadmin@uwati.org", "hash", "Super Admin", true);

		EffectiveAccess access = resolver.resolve(superadmin, null);

		assertThat(access.platformSuperAdmin()).isTrue();
		assertThat(access.tenantWide()).isTrue();
		assertThat(access.roles()).contains("PLATFORM_SUPERADMIN");
		assertThat(access.permissions()).contains("*");
	}

	@Test
	@DisplayName("Should resolve user with direct tenant-wide role assignment")
	void shouldResolveDirectTenantWideRole() {
		User user = User.create("admin@hospital.org", "hash", "Hospital Admin", false);
		Role adminRole = Role.createCustom(tenantId, "ADMIN", "Administrator", null, Set.of("PATIENT_READ", "PATIENT_WRITE"));
		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(user.getId(), adminRole.getId(), tenantId);

		ScopeNode rootNode = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");

		when(userGroupMembershipRepository.findAllByUserId(user.getId())).thenReturn(List.of());
		when(userRoleAssignmentRepository.findAllByUserIdAndTenantId(user.getId(), tenantId)).thenReturn(List.of(assignment));
		when(roleRepository.findAllByIds(any())).thenReturn(List.of(adminRole));
		when(scopeNodeRepository.findAllByTenantId(tenantId)).thenReturn(List.of(rootNode));

		EffectiveAccess access = resolver.resolve(user, tenantId);

		assertThat(access.platformSuperAdmin()).isFalse();
		assertThat(access.tenantWide()).isTrue();
		assertThat(access.roles()).containsExactly("ADMIN");
		assertThat(access.permissions()).containsExactlyInAnyOrder("PATIENT_READ", "PATIENT_WRITE");
		assertThat(access.accessibleScopeNodeIds()).containsExactly(rootNode.getId().value());
	}

	@Test
	@DisplayName("Should resolve user with group-inherited roles and subtree scopes")
	void shouldResolveGroupInheritedRolesAndSubtreeScopes() {
		User doctor = User.create("doctor.alice@hospital.org", "hash", "Dr. Alice", false);
		Group cardioTeam = Group.create(tenantId, "CARDIO_TEAM", "Cardiology Team", null, null);
		UserGroupMembership membership = UserGroupMembership.of(cardioTeam.getId(), doctor.getId());

		ScopeNode root = ScopeNode.createRoot(tenantId, "HOSPITAL", "Hospital");
		ScopeNode cardio = ScopeNode.createChild(tenantId, root, "CARDIO", "Cardiology Clinic");
		ScopeNode cardioEcho = ScopeNode.createChild(tenantId, cardio, "ECHO_UNIT", "Echo Unit");

		Role physicianRole = Role.createCustom(tenantId, "PHYSICIAN", "Physician", null, Set.of("CLINICAL_NOTE_WRITE"));
		GroupRoleAssignment groupAssignment = GroupRoleAssignment.create(
				cardioTeam.getId(),
				physicianRole.getId(),
				tenantId,
				cardio.getId(),
				true); // inherit children = true

		when(userGroupMembershipRepository.findAllByUserId(doctor.getId())).thenReturn(List.of(membership));
		when(groupRepository.findAllByIds(any())).thenReturn(List.of(cardioTeam));
		when(userRoleAssignmentRepository.findAllByUserIdAndTenantId(doctor.getId(), tenantId)).thenReturn(List.of());
		when(groupRoleAssignmentRepository.findAllByGroupIds(any())).thenReturn(List.of(groupAssignment));
		when(roleRepository.findAllByIds(any())).thenReturn(List.of(physicianRole));

		when(scopeNodeRepository.findById(cardio.getId())).thenReturn(Optional.of(cardio));
		when(scopeNodeRepository.findDescendantsByPathPrefix(cardio.getPath())).thenReturn(List.of(cardioEcho));

		EffectiveAccess access = resolver.resolve(doctor, tenantId);

		assertThat(access.groups()).containsExactly("CARDIO_TEAM");
		assertThat(access.roles()).containsExactly("PHYSICIAN");
		assertThat(access.permissions()).containsExactly("CLINICAL_NOTE_WRITE");
		assertThat(access.tenantWide()).isFalse();
		assertThat(access.accessibleScopeNodeIds()).containsExactlyInAnyOrder(cardio.getId().value(), cardioEcho.getId().value());
		assertThat(access.accessibleScopePaths()).contains(cardio.getPath(), cardioEcho.getPath());
	}
}
