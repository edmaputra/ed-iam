package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link UserManagementService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class UserManagementServiceTest {

	private UserRepository userRepository;
	private PasswordEncoderPort passwordEncoder;
	private RoleRepository roleRepository;
	private UserRoleAssignmentRepository userRoleAssignmentRepository;
	private GroupRepository groupRepository;
	private UserGroupMembershipRepository userGroupMembershipRepository;

	private UserManagementService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoderPort.class);
		roleRepository = mock(RoleRepository.class);
		userRoleAssignmentRepository = mock(UserRoleAssignmentRepository.class);
		groupRepository = mock(GroupRepository.class);
		userGroupMembershipRepository = mock(UserGroupMembershipRepository.class);

		service = new UserManagementService(
				userRepository,
				passwordEncoder,
				userRoleAssignmentRepository,
				userGroupMembershipRepository,
				roleRepository,
				groupRepository);
	}

	@Test
	@DisplayName("Should create user with password and without password")
	void shouldCreateUser() {
		when(userRepository.findByEmail("alice@test.org")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("secret")).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		CreateUserCommand cmd1 = new CreateUserCommand("alice@test.org", "secret", "Alice", false);
		User user1 = service.createUser(cmd1);
		assertThat(user1.getEmail()).isEqualTo("alice@test.org");
		assertThat(user1.getPasswordHash()).isEqualTo("hashed");

		// SSO user with null password
		when(userRepository.findByEmail("sso@test.org")).thenReturn(Optional.empty());
		CreateUserCommand cmd2 = new CreateUserCommand("sso@test.org", null, "SSO User", true);
		User user2 = service.createUser(cmd2);
		assertThat(user2.getPasswordHash()).isNull();

		// Duplicate email check
		when(userRepository.findByEmail("alice@test.org")).thenReturn(Optional.of(user1));
		assertThatThrownBy(() -> service.createUser(cmd1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should get user by ID and email, throwing UserNotFoundException when absent")
	void shouldGetUser() {
		UserId userId = UserId.generate();
		User user = User.create("test@test.org", "hash", "Name", false);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.findByEmail("test@test.org")).thenReturn(Optional.of(user));

		assertThat(service.getUserById(userId)).isSameAs(user);
		assertThat(service.getUserByEmail("test@test.org")).isSameAs(user);

		UserId unknownId = UserId.generate();
		when(userRepository.findById(unknownId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getUserById(unknownId)).isInstanceOf(UserNotFoundException.class);

		when(userRepository.findByEmail("unknown@test.org")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getUserByEmail("unknown@test.org")).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should update user profile and change status across all transitions")
	void shouldUpdateUserAndStatus() {
		UserId userId = UserId.generate();
		User user = User.create("test@test.org", "hash", "Name", false);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		User updated = service.updateUser(new UpdateUserCommand(userId, "New Name"));
		assertThat(updated.getFullName()).isEqualTo("New Name");

		// Status transitions: SUSPENDED, ACTIVE, DEACTIVATED
		User suspended = service.changeUserStatus(new ChangeUserStatusCommand(userId, UserStatus.SUSPENDED));
		assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);

		User active = service.changeUserStatus(new ChangeUserStatusCommand(userId, UserStatus.ACTIVE));
		assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);

		User deactivated = service.changeUserStatus(new ChangeUserStatusCommand(userId, UserStatus.DEACTIVATED));
		assertThat(deactivated.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
	}

	@Test
	@DisplayName("Should assign scoped and tenant-wide roles and revoke role")
	void shouldManageRoleAssignments() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		User user = User.create("test@test.org", "hash", "Name", false);
		Role role = Role.createCustom(tenantId, "ROLE", "Role", "Desc", java.util.Set.of());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
		when(userRoleAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

		// Scoped assignment
		UserRoleAssignment scoped = service.assignRole(new AssignUserRoleCommand(userId, roleId, tenantId, scopeId));
		assertThat(scoped.getScopeNodeId()).isEqualTo(scopeId);

		// Tenant-wide assignment
		UserRoleAssignment wide = service.assignRole(new AssignUserRoleCommand(userId, roleId, tenantId, null));
		assertThat(wide.getScopeNodeId()).isNull();

		// Role not found
		RoleId missingRole = RoleId.generate();
		when(roleRepository.findById(missingRole)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.assignRole(new AssignUserRoleCommand(userId, missingRole, tenantId, null)))
				.isInstanceOf(RoleNotFoundException.class);

		// Revoke role
		UUID assignUuid = UUID.randomUUID();
		service.revokeRole(assignUuid);
		verify(userRoleAssignmentRepository).delete(new UserRoleAssignmentId(assignUuid));

		// Get assignments
		service.getRoleAssignments(userId);
		verify(userRoleAssignmentRepository).findAllByUserId(userId);
	}

	@Test
	@DisplayName("Should manage group memberships and get user groups")
	void shouldManageGroupMemberships() {
		UserId userId = UserId.generate();
		GroupId groupId = GroupId.generate();
		TenantId tenantId = TenantId.generate();

		User user = User.create("test@test.org", "hash", "Name", false);
		Group group = Group.create(tenantId, "GRP", "Group", "Desc", null);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		service.addUserToGroup(groupId, userId);
		verify(userGroupMembershipRepository).save(any(UserGroupMembership.class));

		// Add again when already exists
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
		service.addUserToGroup(groupId, userId);

		// Group not found
		GroupId missingGroup = GroupId.generate();
		when(groupRepository.findById(missingGroup)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.addUserToGroup(missingGroup, userId))
				.isInstanceOf(GroupNotFoundException.class);

		// Remove user from group
		service.removeUserFromGroup(groupId, userId);
		verify(userGroupMembershipRepository).delete(groupId, userId);

		// Get user groups
		when(userGroupMembershipRepository.findAllByUserId(userId)).thenReturn(List.of(UserGroupMembership.of(groupId, userId)));
		List<Group> groups = service.getUserGroups(userId);
		assertThat(groups).containsExactly(group);

		// Delete user
		service.deleteUser(userId);
		verify(userRepository).delete(userId);
	}

	@Test
	@DisplayName("Should retrieve paginated users from repository")
	void shouldGetUsersWithPagination() {
		PageQuery query = PageQuery.of(0, 10);
		User user1 = User.create("u1@test.org", "hash", "User 1", false);
		User user2 = User.create("u2@test.org", "hash", "User 2", false);
		PagedResult<User> expected = new PagedResult<>(List.of(user1, user2), 0, 10, 2L, 1);

		when(userRepository.findAll(UserFilter.empty(), query)).thenReturn(expected);

		PagedResult<User> actual = service.getUsers(null, query);
		assertThat(actual.content()).containsExactly(user1, user2);
		assertThat(actual.totalElements()).isEqualTo(2L);
		assertThat(actual.totalPages()).isEqualTo(1);
		assertThat(actual.page()).isZero();
		assertThat(actual.size()).isEqualTo(10);
		verify(userRepository).findAll(UserFilter.empty(), query);

		// With filter
		UserFilter filter = new UserFilter("u1", null, null, null, null);
		PagedResult<User> filteredExpected = new PagedResult<>(List.of(user1), 0, 10, 1L, 1);
		when(userRepository.findAll(filter, query)).thenReturn(filteredExpected);

		PagedResult<User> filteredActual = service.getUsers(filter, query);
		assertThat(filteredActual.content()).containsExactly(user1);
		verify(userRepository).findAll(filter, query);
	}
}
