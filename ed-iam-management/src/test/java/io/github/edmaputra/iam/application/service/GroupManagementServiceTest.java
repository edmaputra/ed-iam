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
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
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
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

		Group group = Group.create(TenantId.generate(), "GROUP1", "Group One", "Desc", null);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		when(userGroupMembershipRepository.findAllByGroupId(groupId)).thenReturn(List.of(
				UserGroupMembership.of(groupId, userId1),
				UserGroupMembership.of(groupId, userId2)));

		when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
		when(userRepository.findById(userId2)).thenReturn(Optional.empty());

		List<User> members = service.getGroupMembers(groupId);
		assertThat(members).containsExactly(user1);
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from creating group in a different tenant")
	void shouldRejectCreatingGroupForDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId otherTenantId = TenantId.generate();

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		GroupManagementService securedService = new GroupManagementService(
				groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, actorProvider);

		CreateGroupCommand command = new CreateGroupCommand(otherTenantId, "OTHER_GROUP", "Other Group", "Desc", null);

		assertThatThrownBy(() -> securedService.createGroup(command))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}

	@Test
	@DisplayName("Should prevent non-superadmin actor from reading group of a different tenant")
	void shouldRejectAccessingGroupFromDifferentTenantWhenNotSuperAdmin() {
		TenantId actorTenantId = TenantId.generate();
		TenantId otherTenantId = TenantId.generate();
		GroupId groupId = GroupId.generate();
		Group group = Group.create(otherTenantId, "OTHER_GROUP", "Other Group", "Desc", null);

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenantId.value());
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		GroupManagementService securedService = new GroupManagementService(
				groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, actorProvider);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> securedService.getGroupById(groupId))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Access denied: operation not permitted for a different tenant.");
	}

	@Test
	@DisplayName("Should allow superadmin actor to manage groups in any tenant")
	void shouldAllowSuperAdminToManageGroupsInAnyTenant() {
		TenantId targetTenantId = TenantId.generate();
		GroupId groupId = GroupId.generate();
		Group group = Group.create(targetTenantId, "TARGET_GROUP", "Target Group", "Desc", null);

		CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(true);
		when(actorProvider.currentActor()).thenReturn(Optional.of(actor));

		GroupManagementService securedService = new GroupManagementService(
				groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, actorProvider);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		Group result = securedService.getGroupById(groupId);
		assertThat(result.getCode()).isEqualTo("TARGET_GROUP");
	}

	@Test
	@DisplayName("Should publish audit events for group lifecycle")
	void shouldPublishAuditEventsForGroupLifecycle() {
		TenantId tenantId = TenantId.generate();
		EventPublisherPort publisher = mock(EventPublisherPort.class);
		GroupManagementService eventService = new GroupManagementService(
				groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, null, publisher);

		when(groupRepository.existsByTenantIdAndCode(tenantId, "SEC_GRP")).thenReturn(false);
		when(groupRepository.save(any(Group.class))).thenAnswer(i -> i.getArgument(0));

		Group group = eventService.createGroup(new CreateGroupCommand(
				tenantId, "SEC_GRP", "Security Group", "Desc", null));

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.GROUP_CREATED);

		when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
		eventService.updateGroup(new UpdateGroupCommand(
				group.getId(), "Updated Name", "Updated Desc", null));
		verify(publisher, times(2)).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.GROUP_UPDATED);

		eventService.deleteGroup(group.getId());
		verify(publisher, times(3)).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.GROUP_DELETED);
	}

	@Test
	@DisplayName("Should construct facade with sub-services and delegate successfully")
	void shouldDelegateToSubServicesWhenConstructedWithSpecializedServices() {
		GroupLifecycleService lifecycleService = mock(GroupLifecycleService.class);
		GroupRoleAssignmentService roleAssignmentService = mock(GroupRoleAssignmentService.class);
		UserGroupMembershipService membershipService = mock(UserGroupMembershipService.class);

		GroupManagementService facade = new GroupManagementService(lifecycleService, roleAssignmentService, membershipService);

		GroupId groupId = GroupId.generate();
		Group group = Group.create(TenantId.generate(), "FACADE_GRP", "Facade Group", "Desc", null);
		when(lifecycleService.getGroupById(groupId)).thenReturn(group);

		Group result = facade.getGroupById(groupId);
		assertThat(result).isSameAs(group);
		verify(lifecycleService).getGroupById(groupId);

		// Null checks
		assertThatThrownBy(() -> new GroupManagementService(null, roleAssignmentService, membershipService))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("GroupLifecycleService must not be null.");

		assertThatThrownBy(() -> new GroupManagementService(lifecycleService, null, membershipService))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("GroupRoleAssignmentService must not be null.");

		assertThatThrownBy(() -> new GroupManagementService(lifecycleService, roleAssignmentService, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("UserGroupMembershipService must not be null.");
	}
}
