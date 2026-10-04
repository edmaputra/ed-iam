package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignmentId;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManageGroupUseCase} for tenant group management.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class GroupManagementService implements ManageGroupUseCase {

	private final GroupRepository groupRepository;
	private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final CurrentActorProvider currentActorProvider;

	public GroupManagementService(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository,
			CurrentActorProvider currentActorProvider) {
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.groupRoleAssignmentRepository = Objects.requireNonNull(groupRoleAssignmentRepository, "GroupRoleAssignmentRepository must not be null.");
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository, "UserGroupMembershipRepository must not be null.");
		this.roleRepository = Objects.requireNonNull(roleRepository, "RoleRepository must not be null.");
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
	}

	public GroupManagementService(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository) {
		this(groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, null);
	}

	@Override
	@Transactional
	public Group createGroup(CreateGroupCommand command) {
		Objects.requireNonNull(command, "CreateGroupCommand must not be null.");
		checkTenantAccess(command.tenantId());

		if (groupRepository.existsByTenantIdAndCode(command.tenantId(), command.code())) {
			throw new IllegalArgumentException("Group code already exists for tenant: " + command.code());
		}

		Group group = Group.create(
				command.tenantId(),
				command.code(),
				command.name(),
				command.description(),
				command.externalIdpGroupName());

		return groupRepository.save(group);
	}

	@Override
	@Transactional(readOnly = true)
	public Group getGroupById(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		Group group = groupRepository.findById(id)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + id.value()));
		checkTenantAccess(group.getTenantId());
		return group;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Group> getGroupsByTenant(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "TenantId must not be null.");
		checkTenantAccess(tenantId);
		return groupRepository.findAllByTenantId(tenantId);
	}

	@Override
	@Transactional
	public Group updateGroup(UpdateGroupCommand command) {
		Objects.requireNonNull(command, "UpdateGroupCommand must not be null.");
		Group group = getGroupById(command.groupId());
		group.updateDetails(command.name(), command.description(), command.externalIdpGroupName());
		return groupRepository.save(group);
	}

	@Override
	@Transactional
	public void deleteGroup(GroupId id) {
		Objects.requireNonNull(id, "GroupId must not be null.");
		getGroupById(id);
		groupRepository.delete(id);
	}

	@Override
	@Transactional
	public GroupRoleAssignment assignRole(AssignGroupRoleCommand command) {
		Objects.requireNonNull(command, "AssignGroupRoleCommand must not be null.");

		getGroupById(command.groupId());
		checkTenantAccess(command.tenantId());

		Role role = roleRepository.findById(command.roleId())
				.orElseThrow(() -> new RoleNotFoundException("Role not found: " + command.roleId().value()));

		GroupRoleAssignment assignment = command.scopeNodeId() != null
				? GroupRoleAssignment.create(command.groupId(), role.getId(), command.tenantId(), command.scopeNodeId(), true)
				: GroupRoleAssignment.createTenantWide(command.groupId(), role.getId(), command.tenantId());

		return groupRoleAssignmentRepository.save(assignment);
	}

	@Override
	@Transactional
	public void revokeRole(UUID assignmentId) {
		Objects.requireNonNull(assignmentId, "AssignmentId must not be null.");
		GroupRoleAssignmentId id = new GroupRoleAssignmentId(assignmentId);
		groupRoleAssignmentRepository.findById(id).ifPresent(a -> checkTenantAccess(a.getTenantId()));
		groupRoleAssignmentRepository.delete(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<GroupRoleAssignment> getRoleAssignments(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		getGroupById(groupId);
		return groupRoleAssignmentRepository.findAllByGroupIds(Set.of(groupId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<User> getGroupMembers(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		getGroupById(groupId);
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByGroupId(groupId);
		return memberships.stream()
				.map(m -> userRepository.findById(m.userId()).orElse(null))
				.filter(Objects::nonNull)
				.toList();
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
}
