package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.UserSession;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManageUserUseCase} for user provisioning,
 * profile updates, status management, role assignments, and group memberships.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class UserManagementService implements ManageUserUseCase {

	private final UserRepository userRepository;
	private final PasswordEncoderPort passwordEncoder;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final RoleRepository roleRepository;
	private final GroupRepository groupRepository;
	private final SessionRegistryPort sessionRegistry;
	private final TokenRevocationPort tokenRevocationPort;
	private final UserMfaRepository userMfaRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

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
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoderPort must not be null.");
		this.userRoleAssignmentRepository = Objects.requireNonNull(userRoleAssignmentRepository, "UserRoleAssignmentRepository must not be null.");
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository, "UserGroupMembershipRepository must not be null.");
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.sessionRegistry = sessionRegistry;
		this.tokenRevocationPort = tokenRevocationPort;
		this.userMfaRepository = userMfaRepository;
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
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
		Objects.requireNonNull(command, "CreateUserCommand must not be null.");

		if (command.platformSuperAdmin() && currentActorProvider != null) {
			currentActorProvider.currentActor().ifPresent(actor -> {
				if (!actor.isPlatformSuperAdmin()) {
					throw new AccessDeniedException("Only platform superadmins can create platform superadmin accounts.");
				}
			});
		}

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

		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_CREATED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "fullName", saved.getFullName()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public User getUserById(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		return userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id.value()));
	}

	@Override
	@Transactional(readOnly = true)
	public User getUserByEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
	}

	@Override
	@Transactional
	public User updateUser(UpdateUserCommand command) {
		Objects.requireNonNull(command, "UpdateUserCommand must not be null.");
		User user = getUserById(command.userId());
		user.updateProfile(command.fullName());
		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_UPDATED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "fullName", saved.getFullName()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional
	public User changeUserStatus(ChangeUserStatusCommand command) {
		Objects.requireNonNull(command, "ChangeUserStatusCommand must not be null.");
		User user = getUserById(command.userId());

		switch (command.status()) {
			case ACTIVE -> user.activate();
			case SUSPENDED -> {
				user.suspend();
				revokeUserSessions(user.getId());
			}
			case DEACTIVATED -> {
				user.deactivate();
				revokeUserSessions(user.getId());
			}
		}

		User saved = userRepository.save(user);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_STATUS_CHANGED,
					null,
					saved.getId().value(),
					"USER",
					Map.of("email", saved.getEmail(), "status", saved.getStatus().name()),
					actorStr));
			if (saved.isDeactivated()) {
				eventPublisher.publish(IamEvent.of(
						IamEventTypes.USER_DEACTIVATED,
						null,
						saved.getId().value(),
						"USER",
						Map.of("email", saved.getEmail()),
						actorStr));
			}
		}
		return saved;
	}

	@Override
	@Transactional
	public void deleteUser(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		User user = userRepository.findById(id).orElse(null);
		revokeUserSessions(id);
		if (userMfaRepository != null) {
			userMfaRepository.deleteByUserId(id);
		}
		userRepository.delete(id);
		if (eventPublisher != null && user != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.USER_DEACTIVATED,
					null,
					id.value(),
					"USER",
					Map.of("email", user.getEmail()),
					actorStr));
		}
	}

	@Override
	@Transactional
	public UserRoleAssignment assignRole(AssignUserRoleCommand command) {
		Objects.requireNonNull(command, "AssignUserRoleCommand must not be null.");
		checkTenantAccess(command.tenantId());

		// Verify user and role exist
		getUserById(command.userId());
		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId().value()));

		UserRoleAssignment assignment = command.scopeNodeId() != null
				? UserRoleAssignment.create(command.userId(), role.getId(), command.tenantId(), command.scopeNodeId(), true)
				: UserRoleAssignment.createTenantWide(command.userId(), role.getId(), command.tenantId());

		UserRoleAssignment saved = userRoleAssignmentRepository.save(assignment);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_ASSIGNMENT_CREATED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"ROLE_ASSIGNMENT",
					Map.of("userId", saved.getUserId().value().toString(), "roleId", saved.getRoleId().value().toString()),
					actorStr));
		}
		return saved;
	}

	@Override
	@Transactional
	public void revokeRole(UUID assignmentId) {
		Objects.requireNonNull(assignmentId, "AssignmentId must not be null.");
		UserRoleAssignmentId id = new UserRoleAssignmentId(assignmentId);
		UserRoleAssignment assignment = userRoleAssignmentRepository.findById(id).orElse(null);
		if (assignment != null) {
			checkTenantAccess(assignment.getTenantId());
		}
		userRoleAssignmentRepository.delete(id);
		if (eventPublisher != null && assignment != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_ASSIGNMENT_REVOKED,
					assignment.getTenantId() != null ? assignment.getTenantId().value() : null,
					assignment.getId().value(),
					"ROLE_ASSIGNMENT",
					Map.of("userId", assignment.getUserId().value().toString(), "roleId", assignment.getRoleId().value().toString()),
					actorStr));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserRoleAssignment> getRoleAssignments(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return userRoleAssignmentRepository.findAllByUserId(userId);
	}

	@Override
	@Transactional
	public void addUserToGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");

		getUserById(userId);
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());

		if (!userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
			userGroupMembershipRepository.save(UserGroupMembership.of(groupId, userId));
		}
	}

	@Override
	@Transactional
	public void removeUserFromGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");

		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());

		userGroupMembershipRepository.delete(groupId, userId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Group> getUserGroups(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(userId);
		return memberships.stream()
				.map(m -> groupRepository.findById(m.groupId()).orElse(null))
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public PagedResult<User> getUsers(UserFilter filter, PageQuery pageQuery) {
		UserFilter resolvedFilter = filter != null ? filter : UserFilter.empty();
		return userRepository.findAll(resolvedFilter, pageQuery);
	}

	private void checkTenantAccess(TenantId targetTenantId) {
		if (targetTenantId == null || currentActorProvider == null) {
			return;
		}
		currentActorProvider.currentActor().ifPresent(actor -> {
			if (!actor.isPlatformSuperAdmin()) {
				if (actor.tenantId() == null || !actor.tenantId().equals(targetTenantId.value())) {
					throw new AccessDeniedException("Access denied: operation not permitted for a different tenant.");
				}
			}
		});
	}

	private void revokeUserSessions(UserId userId) {
		if (tokenRevocationPort != null) {
			tokenRevocationPort.revokeAllForUser(userId, Instant.now());
		}
		if (sessionRegistry != null) {
			List<UserSession> activeSessions = sessionRegistry.findActiveSessions(userId, null);
			for (UserSession s : activeSessions) {
				sessionRegistry.revokeSession(s.id());
				if (tokenRevocationPort != null && s.tokenIdentifier() != null) {
					tokenRevocationPort.revokeToken(s.tokenIdentifier(), s.expiresAt());
				}
			}
		}
	}

	private String resolveActor() {
		return currentActorProvider != null && currentActorProvider.currentActor().isPresent() &&
				currentActorProvider.currentActor().get().userId() != null
				? currentActorProvider.currentActor().get().userId().toString()
				: "system";
	}
}

