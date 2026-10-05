package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

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
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for group role assignments and revocations.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class GroupRoleAssignmentService {

	private final GroupRepository groupRepository;
	private final RoleRepository roleRepository;
	private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public GroupRoleAssignmentService(
			GroupRepository groupRepository,
			RoleRepository roleRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.groupRoleAssignmentRepository = Objects.requireNonNull(groupRoleAssignmentRepository, "GroupRoleAssignmentRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public GroupRoleAssignmentService(
			GroupRepository groupRepository,
			RoleRepository roleRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository) {
		this(groupRepository, roleRepository, groupRoleAssignmentRepository, null, null);
	}

	@Transactional
	public GroupRoleAssignment assignRole(AssignGroupRoleCommand command) {
		Objects.requireNonNull(command, "AssignGroupRoleCommand must not be null.");

		Group group = groupRepository.findById(command.groupId())
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + command.groupId().value()));
		checkTenantAccess(group.getTenantId());
		checkTenantAccess(command.tenantId());

		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId().value()));

		GroupRoleAssignment assignment = command.scopeNodeId() != null
				? GroupRoleAssignment.create(command.groupId(), role.getId(), command.tenantId(), command.scopeNodeId(), true)
				: GroupRoleAssignment.createTenantWide(command.groupId(), role.getId(), command.tenantId());

		GroupRoleAssignment saved = groupRoleAssignmentRepository.save(assignment);
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_ASSIGNMENT_CREATED,
					saved.getTenantId() != null ? saved.getTenantId().value() : null,
					saved.getId().value(),
					"ROLE_ASSIGNMENT",
					Map.of("groupId", saved.getGroupId().value().toString(), "roleId", saved.getRoleId().value().toString()),
					actorStr));
		}
		return saved;
	}

	@Transactional
	public void revokeRole(UUID assignmentId) {
		Objects.requireNonNull(assignmentId, "AssignmentId must not be null.");
		GroupRoleAssignmentId id = new GroupRoleAssignmentId(assignmentId);
		GroupRoleAssignment assignment = groupRoleAssignmentRepository.findById(id).orElse(null);
		if (assignment != null) {
			checkTenantAccess(assignment.getTenantId());
		}
		groupRoleAssignmentRepository.delete(id);
		if (eventPublisher != null && assignment != null) {
			String actorStr = resolveActor();
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ROLE_ASSIGNMENT_REVOKED,
					assignment.getTenantId() != null ? assignment.getTenantId().value() : null,
					assignment.getId().value(),
					"ROLE_ASSIGNMENT",
					Map.of("groupId", assignment.getGroupId().value().toString(), "roleId", assignment.getRoleId().value().toString()),
					actorStr));
		}
	}

	@Transactional(readOnly = true)
	public List<GroupRoleAssignment> getRoleAssignments(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());
		return groupRoleAssignmentRepository.findAllByGroupIds(Set.of(groupId));
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
