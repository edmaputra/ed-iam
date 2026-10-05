package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

/**
 * Composite application service facade implementing {@link ManageUserUseCase}.
 * Coordinates user account lifecycle, role assignments, and group memberships
 * by delegating to specialized collaborator services.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class UserManagementService implements ManageUserUseCase {

	private final UserAccountService userAccountService;
	private final UserRoleAssignmentService userRoleAssignmentService;
	private final UserGroupMembershipService userGroupMembershipService;

	/**
	 * Canonical constructor with specialized collaborator services.
	 *
	 * @param userAccountService         the user account lifecycle service
	 * @param userRoleAssignmentService  the user role assignment service
	 * @param userGroupMembershipService the user group membership service
	 */
	public UserManagementService(
			UserAccountService userAccountService,
			UserRoleAssignmentService userRoleAssignmentService,
			UserGroupMembershipService userGroupMembershipService) {
		this.userAccountService = Objects.requireNonNull(userAccountService, "UserAccountService must not be null.");
		this.userRoleAssignmentService = Objects.requireNonNull(userRoleAssignmentService, "UserRoleAssignmentService must not be null.");
		this.userGroupMembershipService = Objects.requireNonNull(userGroupMembershipService, "UserGroupMembershipService must not be null.");
	}

	/**
	 * Backwards-compatible convenience constructor instantiating collaborator services internally.
	 */
	public UserManagementService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			UserMfaRepository userMfaRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this(
				new UserAccountService(userRepository, passwordEncoder, sessionRegistry, tokenRevocationPort, userMfaRepository, currentActorProvider, eventPublisher),
				new UserRoleAssignmentService(userRepository, roleRepository, userRoleAssignmentRepository, currentActorProvider, eventPublisher),
				new UserGroupMembershipService(userRepository, groupRepository, userGroupMembershipRepository, currentActorProvider)
		);
	}

	public UserManagementService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			UserMfaRepository userMfaRepository,
			CurrentActorProvider currentActorProvider) {
		this(userRepository, passwordEncoder, userRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, groupRepository, sessionRegistry, tokenRevocationPort, userMfaRepository, currentActorProvider, null);
	}

	public UserManagementService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort,
			UserMfaRepository userMfaRepository) {
		this(userRepository, passwordEncoder, userRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, groupRepository, sessionRegistry, tokenRevocationPort, userMfaRepository, null, null);
	}

	public UserManagementService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository,
			SessionRegistryPort sessionRegistry,
			TokenRevocationPort tokenRevocationPort) {
		this(userRepository, passwordEncoder, userRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, groupRepository, sessionRegistry, tokenRevocationPort, null, null);
	}

	public UserManagementService(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository) {
		this(userRepository, passwordEncoder, userRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, groupRepository, null, null, null, null);
	}

	@Override
	@Transactional
	public User createUser(CreateUserCommand command) {
		return userAccountService.createUser(command);
	}

	@Override
	@Transactional(readOnly = true)
	public User getUserById(UserId id) {
		return userAccountService.getUserById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public User getUserByEmail(String email) {
		return userAccountService.getUserByEmail(email);
	}

	@Override
	@Transactional
	public User updateUser(UpdateUserCommand command) {
		return userAccountService.updateUser(command);
	}

	@Override
	@Transactional
	public User changeUserStatus(ChangeUserStatusCommand command) {
		return userAccountService.changeUserStatus(command);
	}

	@Override
	@Transactional
	public void deleteUser(UserId id) {
		userAccountService.deleteUser(id);
	}

	@Override
	@Transactional
	public UserRoleAssignment assignRole(AssignUserRoleCommand command) {
		return userRoleAssignmentService.assignRole(command);
	}

	@Override
	@Transactional
	public void revokeRole(UUID assignmentId) {
		userRoleAssignmentService.revokeRole(assignmentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserRoleAssignment> getRoleAssignments(UserId userId) {
		return userRoleAssignmentService.getRoleAssignments(userId);
	}

	@Override
	@Transactional
	public void addUserToGroup(GroupId groupId, UserId userId) {
		userGroupMembershipService.addUserToGroup(groupId, userId);
	}

	@Override
	@Transactional
	public void removeUserFromGroup(GroupId groupId, UserId userId) {
		userGroupMembershipService.removeUserFromGroup(groupId, userId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Group> getUserGroups(UserId userId) {
		return userGroupMembershipService.getUserGroups(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public PagedResult<User> getUsers(UserFilter filter, PageQuery pageQuery) {
		return userAccountService.getUsers(filter, pageQuery);
	}
}
