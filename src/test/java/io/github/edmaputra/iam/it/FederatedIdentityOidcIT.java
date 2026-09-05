package io.github.edmaputra.iam.it;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.auth.OidcAuthCredentials;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying OpenID Connect (OIDC) federated authentication,
 * automated Just-In-Time (JIT) user provisioning, external identity mapping,
 * and IdP group synchronization.
 *
 * @author edmaputra
 */
class FederatedIdentityOidcIT extends AbstractIntegrationTest {

	@Autowired
	private AuthenticationProviderRouter authRouter;

	@Test
	@DisplayName("Should provision new user account and map identity on first OIDC authentication")
	void shouldProvisionNewUserOnFirstOidcAuthentication() {
		TenantId tenantId = TenantId.generate();
		String subject = "google-sub-" + UUID.randomUUID();
		String email = "federated-" + UUID.randomUUID() + "@company.com";
		String fullName = "Alice Doe";

		OidcAuthCredentials credentials = new OidcAuthCredentials(
				"mock-id-token",
				email,
				subject,
				fullName,
				"https://accounts.google.com",
				List.of(),
				tenantId);

		// 1. Authenticate via provider router
		AuthenticatedIdentity identity = authRouter.authenticate(credentials);
		assertThat(identity).isNotNull();
		assertThat(identity.userId()).isNotNull();

		// 2. Verify user record exists in database
		User user = userRepository.findById(identity.userId()).orElseThrow();
		assertThat(user.getEmail()).isEqualTo(email);
		assertThat(user.getFullName()).isEqualTo(fullName);
		assertThat(user.getPasswordHash()).isNull(); // External user has no local password
		assertThat(user.isActive()).isTrue();

		// 3. Verify user identity link exists in database
		UserIdentity mappedIdentity = userIdentityRepository
				.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, subject)
				.orElseThrow();
		assertThat(mappedIdentity.getUserId()).isEqualTo(user.getId());

		// 4. Authenticate again with same credentials -> should reuse existing user
		AuthenticatedIdentity secondLogin = authRouter.authenticate(credentials);
		assertThat(secondLogin.userId()).isEqualTo(identity.userId());
	}

	@Test
	@DisplayName("Should automatically synchronize external IdP groups with tenant groups")
	void shouldSynchronizeExternalGroupsOnOidcLogin() {
		TenantId tenantId = TenantId.generate();
		String groupClaimName = "hospital-surgeons";

		// 1. Create tenant group matching externalIdpGroupName
		Group surgeonGroup = Group.create(tenantId, "SURGEONS", "Surgeons", "Surgical team", groupClaimName);
		groupRepository.save(surgeonGroup);

		String subject = "oidc-surgeon-" + UUID.randomUUID();
		String email = "surgeon-" + UUID.randomUUID() + "@hospital.org";

		OidcAuthCredentials credentials = new OidcAuthCredentials(
				"mock-token",
				email,
				subject,
				"Dr. Stephen Strange",
				"https://idp.hospital.org",
				List.of(groupClaimName),
				tenantId);

		// 2. Authenticate
		AuthenticatedIdentity identity = authRouter.authenticate(credentials);

		// 3. Verify user was automatically enrolled in the surgeon group
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(identity.userId());
		assertThat(memberships).extracting(UserGroupMembership::groupId).contains(surgeonGroup.getId());
	}

	@Test
	@DisplayName("Should provision user with fallback to email when fullName is missing or blank")
	void shouldProvisionUserWithFallbackToEmailWhenFullNameIsMissingOrBlank() {
		String subject = "oidc-nameless-" + UUID.randomUUID();
		String email = "nameless-" + UUID.randomUUID() + "@hospital.org";

		OidcAuthCredentials credentials = new OidcAuthCredentials(
				null,
				email,
				subject,
				"   ", // blank full name -> fallback to email
				null,
				null, // null external groups
				null);

		AuthenticatedIdentity identity = authRouter.authenticate(credentials);
		assertThat(identity.fullName()).isEqualTo(email);

		User user = userRepository.findById(identity.userId()).orElseThrow();
		assertThat(user.getFullName()).isEqualTo(email);
	}

	@Test
	@DisplayName("Should handle OIDC login with empty external groups list")
	void shouldHandleOidcLoginWithEmptyExternalGroups() {
		TenantId tenantId = TenantId.generate();
		String subject = "oidc-nogroup-" + UUID.randomUUID();
		String email = "nogroup-" + UUID.randomUUID() + "@hospital.org";

		OidcAuthCredentials credentials = new OidcAuthCredentials(
				"token",
				email,
				subject,
				"No Group User",
				"https://idp.hospital.org",
				List.of(), // empty external groups
				tenantId);

		AuthenticatedIdentity identity = authRouter.authenticate(credentials);
		assertThat(identity).isNotNull();

		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(identity.userId());
		assertThat(memberships).isEmpty();
	}
}

