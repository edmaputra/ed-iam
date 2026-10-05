package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for user role assignments and revocations.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserRoleAssignmentService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public UserRoleAssignmentService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.userRoleAssignmentRepository = Objects.requireNonNull(userRoleAssignmentRepository, "UserRoleAssignmentRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public UserRoleAssignmentService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository) {
		this(userRepository, roleRepository, userRoleAssignmentRepository, null, null);
	}

	@Transactional
	public UserRoleAssignment assignRole(AssignUserRoleCommand command) {
		Objects.requireNonNull(command, "AssignUserRoleCommand must not be null.");
		checkTenantAccess(command.tenantId());

		userRepository.findById(command.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.userId().value()));
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

	@Transactional(readOnly = true)
	public List<UserRoleAssignment> getRoleAssignments(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return userRoleAssignmentRepository.findAllByUserId(userId);
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

	private String resolveActor() {
		return currentActorProvider != null && currentActorProvider.currentActor().isPresent() &&
				currentActorProvider.currentActor().get().userId() != null
				? currentActorProvider.currentActor().get().userId().toString()
				: "system";
	}
}
