package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link GroupManagementService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class GroupManagementServiceTest {

	private GroupRepository groupRepository;
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private UserGroupMembershipRepository userGroupMembershipRepository;
	private RoleRepository roleRepository;
	private UserRepository userRepository;

	private GroupManagementService service;

	@BeforeEach
	void setUp() {
		groupRepository = mock(GroupRepository.class);
		groupRoleAssignmentRepository = mock(GroupRoleAssignmentRepository.class);
		userGroupMembershipRepository = mock(UserGroupMembershipRepository.class);
		roleRepository = mock(RoleRepository.class);
		userRepository = mock(UserRepository.class);

		service = new GroupManagementService(
				groupRepository,
				groupRoleAssignmentRepository,
				userGroupMembershipRepository,
				roleRepository,
				userRepository);
	}

	@Test
	@DisplayName("Should create group and enforce unique code per tenant")
	void shouldCreateGroup() {
		TenantId tenantId = TenantId.generate();
		when(groupRepository.existsByTenantIdAndCode(tenantId, "SURGERY")).thenReturn(false);
		when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

		Group group = service
				.createGroup(new CreateGroupCommand(tenantId, "SURGERY", "Surgery", "Desc", "idp-surgery"));
		assertThat(group.getCode()).isEqualTo("SURGERY");

		when(groupRepository.existsByTenantIdAndCode(tenantId, "SURGERY")).thenReturn(true);
		assertThatThrownBy(() -> service
				.createGroup(new CreateGroupCommand(tenantId, "SURGERY", "Surgery", "Desc", "idp-surgery")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should get group by ID, get by tenant, update, and delete group")
	void shouldGetAndUpdateGroup() {
		GroupId groupId = GroupId.generate();
		TenantId tenantId = TenantId.generate();
		Group group = Group.create(tenantId, "SURGERY", "Surgery", "Desc", "idp-surgery");

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(groupRepository.findAllByTenantId(tenantId)).thenReturn(List.of(group));
		when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(service.getGroupById(groupId)).isSameAs(group);
		assertThat(service.getGroupsByTenant(tenantId)).containsExactly(group);

		Group updated = service.updateGroup(new UpdateGroupCommand(groupId, "General Surgery", "New Desc", "new-idp"));
		assertThat(updated.getName()).isEqualTo("General Surgery");

		service.deleteGroup(groupId);
		verify(groupRepository).delete(groupId);
	}

	@Test
	@DisplayName("Should assign scoped and tenant-wide roles, revoke role, and get role assignments")
	void shouldManageGroupRoleAssignments() {
		GroupId groupId = GroupId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		Group group = Group.create(tenantId, "SURGERY", "Surgery", "Desc", null);
		Role role = Role.createCustom(tenantId, "SURGEON", "Surgeon", "Desc", Set.of());

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(groupRoleAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

		// Scoped assignment
		GroupRoleAssignment scoped = service.assignRole(new AssignGroupRoleCommand(groupId, roleId, tenantId, scopeId));
		assertThat(scoped.getScopeNodeId()).isEqualTo(scopeId);

		// Tenant-wide assignment
		GroupRoleAssignment wide = service.assignRole(new AssignGroupRoleCommand(groupId, roleId, tenantId, null));
		assertThat(wide.getScopeNodeId()).isNull();

		// Role not found
		RoleId missingRole = RoleId.generate();
		when(roleRepository.findById(missingRole)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.assignRole(new AssignGroupRoleCommand(groupId, missingRole, tenantId, null)))
				.isInstanceOf(RoleNotFoundException.class);

		// Revoke role
		UUID assignUuid = UUID.randomUUID();
		service.revokeRole(assignUuid);
		verify(groupRoleAssignmentRepository).delete(new GroupRoleAssignmentId(assignUuid));

		// Get assignments
		service.getRoleAssignments(groupId);
		verify(groupRoleAssignmentRepository).findAllByGroupIds(Set.of(groupId));
	}

	@Test
	@DisplayName("Should get group members with resolving user entities")
	void shouldGetGroupMembers() {
		GroupId groupId = GroupId.generate();
		UserId userId1 = UserId.generate();
		UserId userId2 = UserId.generate();

		User user1 = User.create("u1@test.org", "hash", "User One", false);

		when(userGroupMembershipRepository.findAllByGroupId(groupId)).thenReturn(List.of(
				UserGroupMembership.of(groupId, userId1),
				UserGroupMembership.of(groupId, userId2)));

		when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
		when(userRepository.findById(userId2)).thenReturn(Optional.empty());

		List<User> members = service.getGroupMembers(groupId);
		assertThat(members).containsExactly(user1);
	}
}
