package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link GroupRoleAssignmentService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class GroupRoleAssignmentServiceTest {

	private GroupRepository groupRepository;
	private RoleRepository roleRepository;
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;

	private GroupRoleAssignmentService service;

	@BeforeEach
	void setUp() {
		groupRepository = mock(GroupRepository.class);
		roleRepository = mock(RoleRepository.class);
		groupRoleAssignmentRepository = mock(GroupRoleAssignmentRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);

		service = new GroupRoleAssignmentService(
				groupRepository,
				roleRepository,
				groupRoleAssignmentRepository,
				currentActorProvider,
				eventPublisher);
	}

	@Test
	@DisplayName("Should assign role to group scoped and tenant-wide")
	void shouldAssignRoleToGroup() {
		TenantId tenantId = TenantId.generate();
		Group group = Group.create(tenantId, "NURSES", "Nurses", "Desc", null);
		GroupId groupId = group.getId();

		Role role = Role.createCustom(tenantId, "NURSE_ROLE", "Nurse Role", "Desc", Set.of());
		RoleId roleId = role.getId();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(groupRoleAssignmentRepository.save(any(GroupRoleAssignment.class))).thenAnswer(i -> i.getArgument(0));

		// Scoped assignment
		AssignGroupRoleCommand scopedCmd = new AssignGroupRoleCommand(groupId, roleId, tenantId, scopeId);
		GroupRoleAssignment scopedAssignment = service.assignRole(scopedCmd);

		assertThat(scopedAssignment.getGroupId()).isEqualTo(groupId);
		assertThat(scopedAssignment.getRoleId()).isEqualTo(roleId);
		assertThat(scopedAssignment.getScopeNodeId()).isEqualTo(scopeId);
		assertThat(scopedAssignment.isTenantWide()).isFalse();

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_ASSIGNMENT_CREATED);

		// Tenant-wide assignment
		AssignGroupRoleCommand tenantWideCmd = new AssignGroupRoleCommand(groupId, roleId, tenantId, null);
		GroupRoleAssignment tenantWideAssignment = service.assignRole(tenantWideCmd);
		assertThat(tenantWideAssignment.isTenantWide()).isTrue();
	}

	@Test
	@DisplayName("Should reject assigning role when group or role not found")
	void shouldRejectWhenGroupOrRoleNotFound() {
		TenantId tenantId = TenantId.generate();
		GroupId groupId = GroupId.generate();
		RoleId roleId = RoleId.generate();

		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.assignRole(new AssignGroupRoleCommand(groupId, roleId, tenantId, null)))
				.isInstanceOf(GroupNotFoundException.class);

		Group group = Group.create(tenantId, "GRP", "Name", "Desc", null);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.assignRole(new AssignGroupRoleCommand(groupId, roleId, tenantId, null)))
				.isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	@DisplayName("Should reject assigning role for different tenant when not superadmin")
	void shouldRejectAssigningRoleForDifferentTenant() {
		TenantId groupTenant = TenantId.generate();
		TenantId commandTenant = TenantId.generate();
		TenantId actorTenant = TenantId.generate();

		Group group = Group.create(groupTenant, "GRP", "Name", "Desc", null);
		when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenant.value());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		assertThatThrownBy(() -> service.assignRole(new AssignGroupRoleCommand(group.getId(), RoleId.generate(), commandTenant, null)))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("Should revoke role assignment and publish event")
	void shouldRevokeRole() {
		UUID assignmentUuid = UUID.randomUUID();
		GroupRoleAssignmentId assignmentId = new GroupRoleAssignmentId(assignmentUuid);
		GroupRoleAssignment assignment = GroupRoleAssignment.createTenantWide(GroupId.generate(), RoleId.generate(), TenantId.generate());

		when(groupRoleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

		service.revokeRole(assignmentUuid);

		verify(groupRoleAssignmentRepository).delete(assignmentId);
		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_ASSIGNMENT_REVOKED);
	}

	@Test
	@DisplayName("Should get role assignments by group ID")
	void shouldGetRoleAssignments() {
		TenantId tenantId = TenantId.generate();
		Group group = Group.create(tenantId, "GRP", "Name", "Desc", null);
		GroupId groupId = group.getId();

		GroupRoleAssignment assignment = GroupRoleAssignment.createTenantWide(groupId, RoleId.generate(), tenantId);

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(groupRoleAssignmentRepository.findAllByGroupIds(Set.of(groupId))).thenReturn(List.of(assignment));

		List<GroupRoleAssignment> assignments = service.getRoleAssignments(groupId);
		assertThat(assignments).containsExactly(assignment);
	}
}
