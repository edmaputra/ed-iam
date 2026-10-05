package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Focused application service responsible for user group memberships.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class UserGroupMembershipService {

	private final UserRepository userRepository;
	private final GroupRepository groupRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final CurrentActorProvider currentActorProvider;

	public UserGroupMembershipService(
			UserRepository userRepository,
			GroupRepository groupRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			CurrentActorProvider currentActorProvider) {
		this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null.");
		this.groupRepository = Objects.requireNonNull(groupRepository, "GroupRepository must not be null.");
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository, "UserGroupMembershipRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
	}

	public UserGroupMembershipService(
			UserRepository userRepository,
			GroupRepository groupRepository,
			UserGroupMembershipRepository userGroupMembershipRepository) {
		this(userRepository, groupRepository, userGroupMembershipRepository, null);
	}

	@Transactional
	public void addUserToGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");

		userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());

		if (!userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
			userGroupMembershipRepository.save(UserGroupMembership.of(groupId, userId));
		}
	}

	@Transactional
	public void removeUserFromGroup(GroupId groupId, UserId userId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Objects.requireNonNull(userId, "UserId must not be null.");

		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());

		userGroupMembershipRepository.delete(groupId, userId);
	}

	@Transactional(readOnly = true)
	public List<Group> getUserGroups(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(userId);
		return memberships.stream()
				.map(m -> groupRepository.findById(m.groupId()).orElse(null))
				.filter(Objects::nonNull)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<User> getGroupMembers(GroupId groupId) {
		Objects.requireNonNull(groupId, "GroupId must not be null.");
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + groupId.value()));
		checkTenantAccess(group.getTenantId());
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
