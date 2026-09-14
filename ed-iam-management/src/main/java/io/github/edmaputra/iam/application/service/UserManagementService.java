package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
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
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Application service implementing {@link ManageUserUseCase} for user provisioning,
 * profile updates, status management, role assignments, and group memberships.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
public class UserManagementService implements ManageUserUseCase {

	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final RoleRepository roleRepository;
	private final GroupRepository groupRepository;

	@Override
	public User createUser(CreateUserCommand command) {
		Objects.requireNonNull(command, "CreateUserCommand must not be null.");

		if (userRepository.findByEmail(command.email()).isPresent()) {
			throw new IllegalArgumentException("User with email already exists: " + command.email());
		}

		String passwordHash = (command.password() != null && !command.password().isBlank())
				? passwordEncoder.encode(command.password())
				: null;

		User user = User.create(
				command.email(),
				passwordHash,
				command.fullName(),
				command.platformSuperAdmin());

		return userRepository.save(user);
	}

	@Override
	public User getUserById(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		return userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id.value()));
	}

	@Override
	public User getUserByEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
	}

	@Override
	public User updateUser(UpdateUserCommand command) {
		Objects.requireNonNull(command, "UpdateUserCommand must not be null.");
		User user = getUserById(command.userId());
		user.updateProfile(command.fullName());
		return userRepository.save(user);
	}

	@Override
	public User changeUserStatus(ChangeUserStatusCommand command) {
		Objects.requireNonNull(command, "ChangeUserStatusCommand must not be null.");
		User user = getUserById(command.userId());

		switch (command.status()) {
			case ACTIVE -> user.activate();
			case SUSPENDED -> user.suspend();
			case DEACTIVATED -> user.deactivate();
		}

		return userRepository.save(user);
	}

	@Override
	public void deleteUser(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		userRepository.delete(id);
	}

	@Override
	public UserRoleAssignment assignRole(AssignUserRoleCommand command) {
		Objects.requireNonNull(command, "AssignUserRoleCommand must not be null.");

		// Verify user and role exist
		getUserById(command.userId());
		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId().value()));

		UserRoleAssignment assignment = command.scopeNodeId() != null
				? UserRoleAssignment.create(command.userId(), role.getId(), command.tenantId(), command.scopeNodeId(), true)
				: UserRoleAssignment.createTenantWide(command.userId(), role.getId(), command.tenantId());

		return userRoleAssignmentRepository.save(assignment);
	}

	@Override
	public void revokeRole(UUID assignmentId) {
		Objects.requireNonNull(assignmentId, "AssignmentId must not be null.");
		userRoleAssignmentRepository.delete(new UserRoleAssignmentId(assignmentId));
	}

	@Override
	public List<UserRoleAssignment> getRoleAssignments(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return userRoleAssignmentRepository.findAllByUserId(userId);
	}

	@Override
	public void addUserToGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");

		getUserById(userId);
		groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));

		if (!userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
			userGroupMembershipRepository.save(UserGroupMembership.of(groupId, userId));
		}
	}

	@Override
	public void removeUserFromGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");
		userGroupMembershipRepository.delete(groupId, userId);
	}

	@Override
	public List<Group> getUserGroups(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(userId);
		return memberships.stream()
				.map(m -> groupRepository.findById(m.groupId()).orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}
}
