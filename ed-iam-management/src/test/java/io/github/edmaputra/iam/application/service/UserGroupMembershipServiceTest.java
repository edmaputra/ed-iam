package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link UserGroupMembershipService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class UserGroupMembershipServiceTest {

	private UserRepository userRepository;
	private GroupRepository groupRepository;
	private UserGroupMembershipRepository userGroupMembershipRepository;
	private CurrentActorProvider currentActorProvider;

	private UserGroupMembershipService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		groupRepository = mock(GroupRepository.class);
		userGroupMembershipRepository = mock(UserGroupMembershipRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);

		service = new UserGroupMembershipService(
				userRepository,
				groupRepository,
				userGroupMembershipRepository,
				currentActorProvider);
	}

	@Test
	@DisplayName("Should add user to group if not already member")
	void shouldAddUserToGroup() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		Group group = Group.create(TenantId.generate(), "CLINICIANS", "Clinicians", "Clinical staff", null);

		when(userRepository.findById(userId)).thenReturn(Optional.of(User.create("u@test.org", "hash", "U", false)));
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(false);

		service.addUserToGroup(groupId, userId);

		verify(userGroupMembershipRepository).save(any(UserGroupMembership.class));
	}

	@Test
	@DisplayName("Should not duplicate user group membership if already exists")
	void shouldNotDuplicateMembership() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		Group group = Group.create(TenantId.generate(), "CLINICIANS", "Clinicians", "Clinical staff", null);

		when(userRepository.findById(userId)).thenReturn(Optional.of(User.create("u@test.org", "hash", "U", false)));
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);

		service.addUserToGroup(groupId, userId);

		verify(userGroupMembershipRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should reject adding user to group when user not found")
	void shouldRejectWhenUserNotFound() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.addUserToGroup(groupId, userId))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should reject adding user to group when group not found")
	void shouldRejectWhenGroupNotFound() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		when(userRepository.findById(userId)).thenReturn(Optional.of(User.create("u@test.org", "hash", "U", false)));
		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.addUserToGroup(groupId, userId))
				.isInstanceOf(GroupNotFoundException.class);
	}

	@Test
	@DisplayName("Should reject adding user to group of different tenant when not superadmin")
	void shouldRejectDifferentTenant() {
		TenantId groupTenant = TenantId.generate();
		TenantId actorTenant = TenantId.generate();
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		Group group = Group.create(groupTenant, "CODE", "Name", "Desc", null);

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.tenantId()).thenReturn(actorTenant.value());
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(userRepository.findById(userId)).thenReturn(Optional.of(User.create("u@test.org", "hash", "U", false)));
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> service.addUserToGroup(groupId, userId))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("Should remove user from group")
	void shouldRemoveUserFromGroup() {
		GroupId groupId = GroupId.generate();
		UserId userId = UserId.generate();
		Group group = Group.create(TenantId.generate(), "CLINICIANS", "Clinicians", "Clinical staff", null);

		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		service.removeUserFromGroup(groupId, userId);

		verify(userGroupMembershipRepository).delete(groupId, userId);
	}

	@Test
	@DisplayName("Should return user groups")
	void shouldGetUserGroups() {
		UserId userId = UserId.generate();
		GroupId g1 = GroupId.generate();
		GroupId g2 = GroupId.generate();
		Group group1 = Group.create(TenantId.generate(), "G1", "Group 1", "Desc", null);

		when(userGroupMembershipRepository.findAllByUserId(userId)).thenReturn(List.of(
				UserGroupMembership.of(g1, userId),
				UserGroupMembership.of(g2, userId)));
		when(groupRepository.findById(g1)).thenReturn(Optional.of(group1));
		when(groupRepository.findById(g2)).thenReturn(Optional.empty());

		List<Group> groups = service.getUserGroups(userId);
		assertThat(groups).containsExactly(group1);
	}
}
