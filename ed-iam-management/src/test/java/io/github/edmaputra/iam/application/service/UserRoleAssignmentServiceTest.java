package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
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
 * Unit test for {@link UserRoleAssignmentService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class UserRoleAssignmentServiceTest {

	private UserRepository userRepository;
	private RoleRepository roleRepository;
	private UserRoleAssignmentRepository userRoleAssignmentRepository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;

	private UserRoleAssignmentService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		roleRepository = mock(RoleRepository.class);
		userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);

		service = new UserRoleAssignmentService(
				userRepository,
				roleRepository,
				userRoleAssignmentRepository,
				currentActorProvider,
				eventPublisher);
	}

	@Test
	@DisplayName("Should assign role with organizational scope")
	void shouldAssignRoleWithScope() {
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		User user = User.create("test@test.org", "hash", "Test", false);
		UserId userId = user.getId();
		Role role = Role.createCustom(tenantId, "DOCTOR", "Doctor", "Medical Doctor", Set.of());
		RoleId roleId = role.getId();

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(i -> i.getArgument(0));

		AssignUserRoleCommand command = new AssignUserRoleCommand(userId, roleId, tenantId, scopeId);
		UserRoleAssignment assignment = service.assignRole(command);

		assertThat(assignment.getUserId()).isEqualTo(userId);
		assertThat(assignment.getRoleId()).isEqualTo(roleId);
		assertThat(assignment.getScopeNodeId()).isEqualTo(scopeId);
		assertThat(assignment.isTenantWide()).isFalse();

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_ASSIGNMENT_CREATED);
	}

	@Test
	@DisplayName("Should assign tenant-wide role when no scope node is provided")
	void shouldAssignTenantWideRole() {
		TenantId tenantId = TenantId.generate();

		User user = User.create("test@test.org", "hash", "Test", false);
		UserId userId = user.getId();
		Role role = Role.createCustom(tenantId, "ADMIN", "Admin", "Tenant Admin", Set.of());
		RoleId roleId = role.getId();

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(userRoleAssignmentRepository.save(any(UserRoleAssignment.class))).thenAnswer(i -> i.getArgument(0));

		AssignUserRoleCommand command = new AssignUserRoleCommand(userId, roleId, tenantId, null);
		UserRoleAssignment assignment = service.assignRole(command);

		assertThat(assignment.isTenantWide()).isTrue();
		assertThat(assignment.getScopeNodeId()).isNull();
	}

	@Test
	@DisplayName("Should reject assigning role when user not found")
	void shouldRejectWhenUserNotFound() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		AssignUserRoleCommand command = new AssignUserRoleCommand(userId, roleId, TenantId.generate(), null);
		assertThatThrownBy(() -> service.assignRole(command))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should reject assigning role when role not found")
	void shouldRejectWhenRoleNotFound() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		User user = User.create("test@test.org", "hash", "Test", false);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

		AssignUserRoleCommand command = new AssignUserRoleCommand(userId, roleId, TenantId.generate(), null);
		assertThatThrownBy(() -> service.assignRole(command))
				.isInstanceOf(RoleNotFoundException.class);
	}

	@Test
	@DisplayName("Should reject role assignment for different tenant when caller is not superadmin")
	void shouldRejectRoleAssignmentForDifferentTenant() {
		TenantId actorTenant = TenantId.generate();
		TenantId targetTenant = TenantId.generate();

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenant.value());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		AssignUserRoleCommand command = new AssignUserRoleCommand(UserId.generate(), RoleId.generate(), targetTenant, null);
		assertThatThrownBy(() -> service.assignRole(command))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("Should revoke role assignment and publish event")
	void shouldRevokeRole() {
		UUID assignmentUuid = UUID.randomUUID();
		UserRoleAssignmentId assignmentId = new UserRoleAssignmentId(assignmentUuid);
		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(UserId.generate(), RoleId.generate(), TenantId.generate());

		when(userRoleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

		service.revokeRole(assignmentUuid);

		verify(userRoleAssignmentRepository).delete(assignmentId);
		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.ROLE_ASSIGNMENT_REVOKED);
	}

	@Test
	@DisplayName("Should get role assignments by user id")
	void shouldGetRoleAssignments() {
		UserId userId = UserId.generate();
		List<UserRoleAssignment> assignments = List.of(
				UserRoleAssignment.createTenantWide(userId, RoleId.generate(), TenantId.generate()));
		when(userRoleAssignmentRepository.findAllByUserId(userId)).thenReturn(assignments);

		List<UserRoleAssignment> actual = service.getRoleAssignments(userId);
		assertThat(actual).isEqualTo(assignments);
	}
}
