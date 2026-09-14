package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FederatedIdentityService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class FederatedIdentityServiceTest {

	private UserRepository userRepository;
	private UserIdentityRepository userIdentityRepository;
	private GroupRepository groupRepository;
	private UserGroupMembershipRepository userGroupMembershipRepository;

	private FederatedIdentityService service;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		userIdentityRepository = mock(UserIdentityRepository.class);
		groupRepository = mock(GroupRepository.class);
		userGroupMembershipRepository = mock(UserGroupMembershipRepository.class);

		service = new FederatedIdentityService(
				userRepository,
				userIdentityRepository,
				groupRepository,
				userGroupMembershipRepository);
	}

	@Test
	@DisplayName("Should return authenticated identity when identity is already linked")
	void shouldAuthenticateWhenAlreadyLinked() {
		UserId userId = UserId.generate();
		User user = User.create("user@test.org", "secret", "Test User", false);
		UserIdentity identity = UserIdentity.create(userId, ProviderType.OIDC_GENERIC, "sub-123", "https://accounts.google.com");

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-123"))
				.thenReturn(Optional.of(identity));
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		AuthenticatedIdentity result = service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC, "sub-123", "user@test.org", "Test User", "https://accounts.google.com", null, null);

		assertThat(result).isNotNull();
		assertThat(result.email()).isEqualTo("user@test.org");
	}

	@Test
	@DisplayName("Should throw AuthenticationException when linked user account is not found")
	void shouldThrowWhenLinkedUserNotFound() {
		UserId userId = UserId.generate();
		UserIdentity identity = UserIdentity.create(userId, ProviderType.OIDC_GENERIC, "sub-123", null);

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-123"))
				.thenReturn(Optional.of(identity));
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC, "sub-123", "user@test.org", null, null, null, null))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("Linked user account not found");
	}

	@Test
	@DisplayName("Should link existing user by email when identity link does not exist")
	void shouldLinkExistingUserByEmail() {
		User user = User.create("existing@test.org", "secret", "Existing User", false);

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_KEYCLOAK, "kc-456"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("existing@test.org")).thenReturn(Optional.of(user));

		AuthenticatedIdentity result = service.linkOrProvisionUser(
				ProviderType.OIDC_KEYCLOAK, "kc-456", "existing@test.org", "Existing User", null, null, null);

		assertThat(result.email()).isEqualTo("existing@test.org");
		verify(userIdentityRepository).save(any(UserIdentity.class));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("Should JIT provision user when user does not exist, falling back to email when name is blank")
	void shouldJitProvisionUserWithFallbackName() {
		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_KEYCLOAK, "kc-789"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("new@test.org")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		AuthenticatedIdentity result = service.linkOrProvisionUser(
				ProviderType.OIDC_KEYCLOAK, "kc-789", "new@test.org", "   ", null, null, null);

		assertThat(result.email()).isEqualTo("new@test.org");
		assertThat(result.fullName()).isEqualTo("new@test.org");
		verify(userRepository).save(any(User.class));
		verify(userIdentityRepository).save(any(UserIdentity.class));
	}

	@Test
	@DisplayName("Should throw AuthenticationException when user is suspended or deactivated")
	void shouldThrowWhenSuspendedOrDeactivated() {
		User suspendedUser = User.create("suspended@test.org", "secret", "Suspended", false);
		suspendedUser.suspend();

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_KEYCLOAK, "kc-susp"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("suspended@test.org")).thenReturn(Optional.of(suspendedUser));

		assertThatThrownBy(() -> service.linkOrProvisionUser(
				ProviderType.OIDC_KEYCLOAK, "kc-susp", "suspended@test.org", "Suspended", null, null, null))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");

		User deactivatedUser = User.create("deact@test.org", "secret", "Deactivated", false);
		deactivatedUser.deactivate();

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_KEYCLOAK, "kc-deact"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("deact@test.org")).thenReturn(Optional.of(deactivatedUser));

		assertThatThrownBy(() -> service.linkOrProvisionUser(
				ProviderType.OIDC_KEYCLOAK, "kc-deact", "deact@test.org", "Deactivated", null, null, null))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("deactivated");
	}

	@Test
	@DisplayName("Should synchronize external groups when mapped groups match tenant")
	void shouldSynchronizeExternalGroups() {
		TenantId tenantId = TenantId.generate();
		User user = User.create("sync@test.org", "secret", "Sync User", false);
		Group group1 = Group.create(tenantId, "ENG", "Engineering", "Desc", "idp-eng");
		Group group2 = Group.create(tenantId, "MGR", "Managers", "Desc", "idp-mgr");

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-sync"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("sync@test.org")).thenReturn(Optional.of(user));

		when(groupRepository.findByTenantIdAndExternalIdpGroupName(tenantId, "idp-eng"))
				.thenReturn(Optional.of(group1));
		when(groupRepository.findByTenantIdAndExternalIdpGroupName(tenantId, "idp-mgr"))
				.thenReturn(Optional.of(group2));
		when(groupRepository.findByTenantIdAndExternalIdpGroupName(tenantId, "unmatched"))
				.thenReturn(Optional.empty());

		// group1 is new membership, group2 is already member
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(group1.getId(), user.getId())).thenReturn(false);
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(group2.getId(), user.getId())).thenReturn(true);

		AuthenticatedIdentity result = service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-sync",
				"sync@test.org",
				"Sync User",
				"https://accounts.google.com",
				List.of("idp-eng", "idp-mgr", "unmatched", "  "),
				tenantId);

		assertThat(result).isNotNull();
		verify(userGroupMembershipRepository).save(any());
	}
}
