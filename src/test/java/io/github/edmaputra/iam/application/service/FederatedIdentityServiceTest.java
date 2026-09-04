package io.github.edmaputra.iam.application.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedIdentityServiceTest {

	private UserRepository userRepository;
	private UserIdentityRepository userIdentityRepository;
	private GroupRepository groupRepository;
	private UserGroupMembershipRepository userGroupMembershipRepository;
	private FederatedIdentityService service;

	@BeforeEach
	void setUp() {
		userRepository = Mockito.mock(UserRepository.class);
		userIdentityRepository = Mockito.mock(UserIdentityRepository.class);
		groupRepository = Mockito.mock(GroupRepository.class);
		userGroupMembershipRepository = Mockito.mock(UserGroupMembershipRepository.class);

		service = new FederatedIdentityService(
				userRepository,
				userIdentityRepository,
				groupRepository,
				userGroupMembershipRepository);
	}

	@Test
	@DisplayName("Should authenticate existing linked identity")
	void shouldAuthenticateExistingLinkedUser() {
		User user = User.createExternal("dr.alice@hospital.org", "Dr. Alice", false);
		UserIdentity identity = UserIdentity.create(user.getId(), ProviderType.OIDC_GENERIC, "sub-123", "https://keycloak");

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-123"))
				.thenReturn(Optional.of(identity));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		AuthenticatedIdentity authIdentity = service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-123",
				"dr.alice@hospital.org",
				"Dr. Alice",
				"https://keycloak",
				List.of(),
				null);

		assertThat(authIdentity.userId()).isEqualTo(user.getId());
		assertThat(authIdentity.email()).isEqualTo("dr.alice@hospital.org");
	}

	@Test
	@DisplayName("Should JIT provision user when no identity or user exists")
	void shouldJitProvisionNewUser() {
		TenantId tenantId = TenantId.generate();
		Group cardioGroup = Group.create(tenantId, "CARDIO", "Cardiology", null, "ext_cardio_role");

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-new"))
				.thenReturn(Optional.empty());
		when(userRepository.findByEmail("new.doctor@hospital.org")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(groupRepository.findByTenantIdAndExternalIdpGroupName(tenantId, "ext_cardio_role"))
				.thenReturn(Optional.of(cardioGroup));
		when(userGroupMembershipRepository.existsByGroupIdAndUserId(any(), any())).thenReturn(false);

		AuthenticatedIdentity authIdentity = service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-new",
				"new.doctor@hospital.org",
				"New Doctor",
				"https://auth0.com",
				List.of("ext_cardio_role"),
				tenantId);

		assertThat(authIdentity).isNotNull();
		assertThat(authIdentity.email()).isEqualTo("new.doctor@hospital.org");
		assertThat(authIdentity.fullName()).isEqualTo("New Doctor");

		verify(userRepository).save(any(User.class));
		verify(userIdentityRepository).save(any(UserIdentity.class));
		verify(userGroupMembershipRepository).save(any(UserGroupMembership.class));
	}

	@Test
	@DisplayName("Should reject suspended federated user")
	void shouldRejectSuspendedUser() {
		User user = User.createExternal("dr.alice@hospital.org", "Dr. Alice", false);
		user.suspend();
		UserIdentity identity = UserIdentity.create(user.getId(), ProviderType.OIDC_GENERIC, "sub-123", "https://keycloak");

		when(userIdentityRepository.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-123"))
				.thenReturn(Optional.of(identity));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.linkOrProvisionUser(
				ProviderType.OIDC_GENERIC,
				"sub-123",
				"dr.alice@hospital.org",
				"Dr. Alice",
				"https://keycloak",
				List.of(),
				null))
				.isInstanceOf(AuthenticationException.class)
				.hasMessageContaining("suspended");
	}
}
