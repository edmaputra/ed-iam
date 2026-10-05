package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Composite application service facade implementing {@link ManageGroupUseCase}.
 * Coordinates group lifecycle, role assignments, and member listings
 * by delegating to specialized collaborator services.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class GroupManagementService implements ManageGroupUseCase {

	private final GroupLifecycleService groupLifecycleService;
	private final GroupRoleAssignmentService groupRoleAssignmentService;
	private final UserGroupMembershipService groupMembershipService;

	/**
	 * Canonical constructor with specialized collaborator services.
	 *
	 * @param groupLifecycleService      the group entity lifecycle service
	 * @param groupRoleAssignmentService the group role assignment service
	 * @param groupMembershipService     the group membership service
	 */
	public GroupManagementService(
			GroupLifecycleService groupLifecycleService,
			GroupRoleAssignmentService groupRoleAssignmentService,
			UserGroupMembershipService groupMembershipService) {
		this.groupLifecycleService = Objects.requireNonNull(groupLifecycleService, "GroupLifecycleService must not be null.");
		this.groupRoleAssignmentService = Objects.requireNonNull(groupRoleAssignmentService, "GroupRoleAssignmentService must not be null.");
		this.groupMembershipService = Objects.requireNonNull(groupMembershipService, "UserGroupMembershipService must not be null.");
	}

	/**
	 * Backwards-compatible convenience constructor instantiating collaborator services internally.
	 */
	public GroupManagementService(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this(
				new GroupLifecycleService(groupRepository, currentActorProvider, eventPublisher),
				new GroupRoleAssignmentService(groupRepository, roleRepository, groupRoleAssignmentRepository, currentActorProvider, eventPublisher),
				new UserGroupMembershipService(userRepository, groupRepository, userGroupMembershipRepository, currentActorProvider)
		);
	}

	public GroupManagementService(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository,
			CurrentActorProvider currentActorProvider) {
		this(groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, currentActorProvider, null);
	}

	public GroupManagementService(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository) {
		this(groupRepository, groupRoleAssignmentRepository, userGroupMembershipRepository, roleRepository, userRepository, null, null);
	}

	@Override
	@Transactional
	public Group createGroup(CreateGroupCommand command) {
		return groupLifecycleService.createGroup(command);
	}

	@Override
	@Transactional(readOnly = true)
	public Group getGroupById(GroupId id) {
		return groupLifecycleService.getGroupById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Group> getGroupsByTenant(TenantId tenantId) {
		return groupLifecycleService.getGroupsByTenant(tenantId);
	}

	@Override
	@Transactional
	public Group updateGroup(UpdateGroupCommand command) {
		return groupLifecycleService.updateGroup(command);
	}

	@Override
	@Transactional
	public void deleteGroup(GroupId id) {
		groupLifecycleService.deleteGroup(id);
	}

	@Override
	@Transactional
	public GroupRoleAssignment assignRole(AssignGroupRoleCommand command) {
		return groupRoleAssignmentService.assignRole(command);
	}

	@Override
	@Transactional
	public void revokeRole(UUID assignmentId) {
		groupRoleAssignmentService.revokeRole(assignmentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<GroupRoleAssignment> getRoleAssignments(GroupId groupId) {
		return groupRoleAssignmentService.getRoleAssignments(groupId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<User> getGroupMembers(GroupId groupId) {
		return groupMembershipService.getGroupMembers(groupId);
	}
}
