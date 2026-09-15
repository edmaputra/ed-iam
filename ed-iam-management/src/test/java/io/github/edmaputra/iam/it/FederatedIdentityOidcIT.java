package io.github.edmaputra.iam.it;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying OpenID Connect (OIDC) federated authentication,
 * automated Just-In-Time (JIT) user provisioning, external identity mapping,
 * and IdP group synchronization using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class FederatedIdentityOidcIT extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserIdentityRepository userIdentityRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private UserGroupMembershipRepository userGroupMembershipRepository;

	@Test
	@DisplayName("Should provision new user account and map identity on first OIDC authentication via REST")
	void shouldProvisionNewUserOnFirstOidcAuthentication() {
		UUID tenantUuid = UUID.randomUUID();
		String subject = "google-sub-" + UUID.randomUUID();
		String email = "federated-" + UUID.randomUUID() + "@company.com";
		String fullName = "Alice Doe";

		String authenticateJson = """
				{
				    "token": "mock-id-token",
				    "email": "%s",
				    "subject": "%s",
				    "fullName": "%s",
				    "issuer": "https://accounts.google.com",
				    "groups": [],
				    "tenantId": "%s"
				}
				""".formatted(email, subject, fullName, tenantUuid);

		String expectedIdentityJson = """
				{
				    "email": "%s",
				    "fullName": "%s",
				    "providerType": "OIDC_GENERIC",
				    "platformSuperAdmin": false
				}
				""".formatted(email, fullName);

		// 1. Authenticate via REST endpoint
		byte[] responseBytes = webTestClient.post()
				.uri("/api/test/oidc/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authenticateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedIdentityJson, JsonCompareMode.LENIENT)
				.jsonPath("$.userId").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(responseBytes).isNotNull();
		String userIdStr = JsonPath.read(new String(responseBytes, StandardCharsets.UTF_8), "$.userId");
		UserId userId = new UserId(UUID.fromString(userIdStr));

		// 2. Verify user record exists in database
		User user = userRepository.findById(userId).orElseThrow();
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
		webTestClient.post()
				.uri("/api/test/oidc/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authenticateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.userId").isEqualTo(userIdStr);
	}

	@Test
	@DisplayName("Should automatically synchronize external IdP groups with tenant groups via REST")
	void shouldSynchronizeExternalGroupsOnOidcLogin() {
		TenantId tenantId = TenantId.generate();
		String groupClaimName = "hospital-surgeons";

		// 1. Create tenant group matching externalIdpGroupName
		Group surgeonGroup = Group.create(tenantId, "SURGEONS", "Surgeons", "Surgical team", groupClaimName);
		groupRepository.save(surgeonGroup);

		String subject = "oidc-surgeon-" + UUID.randomUUID();
		String email = "surgeon-" + UUID.randomUUID() + "@hospital.org";

		String authenticateJson = """
				{
				    "token": "mock-token",
				    "email": "%s",
				    "subject": "%s",
				    "fullName": "Dr. Stephen Strange",
				    "issuer": "https://idp.hospital.org",
				    "groups": ["%s"],
				    "tenantId": "%s"
				}
				""".formatted(email, subject, groupClaimName, tenantId.value());

		// 2. Authenticate via REST
		byte[] responseBytes = webTestClient.post()
				.uri("/api/test/oidc/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authenticateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(email)
				.returnResult()
				.getResponseBody();

		assertThat(responseBytes).isNotNull();
		String userIdStr = JsonPath.read(new String(responseBytes, StandardCharsets.UTF_8), "$.userId");
		UserId userId = new UserId(UUID.fromString(userIdStr));

		// 3. Verify user was automatically enrolled in the surgeon group
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(userId);
		assertThat(memberships).extracting(UserGroupMembership::groupId).contains(surgeonGroup.getId());
	}

	@Test
	@DisplayName("Should provision user with fallback to email when fullName is missing or blank via REST")
	void shouldProvisionUserWithFallbackToEmailWhenFullNameIsMissingOrBlank() {
		String subject = "oidc-nameless-" + UUID.randomUUID();
		String email = "nameless-" + UUID.randomUUID() + "@hospital.org";

		String authenticateJson = """
				{
				    "email": "%s",
				    "subject": "%s",
				    "fullName": "   ",
				    "groups": []
				}
				""".formatted(email, subject);

		String expectedIdentityJson = """
				{
				    "email": "%s",
				    "fullName": "%s",
				    "providerType": "OIDC_GENERIC"
				}
				""".formatted(email, email);

		byte[] responseBytes = webTestClient.post()
				.uri("/api/test/oidc/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authenticateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedIdentityJson, JsonCompareMode.LENIENT)
				.returnResult()
				.getResponseBody();

		assertThat(responseBytes).isNotNull();
		String userIdStr = JsonPath.read(new String(responseBytes, StandardCharsets.UTF_8), "$.userId");
		User user = userRepository.findById(new UserId(UUID.fromString(userIdStr))).orElseThrow();
		assertThat(user.getFullName()).isEqualTo(email);
	}

	@Test
	@DisplayName("Should handle OIDC login with empty external groups list via REST")
	void shouldHandleOidcLoginWithEmptyExternalGroups() {
		TenantId tenantId = TenantId.generate();
		String subject = "oidc-nogroup-" + UUID.randomUUID();
		String email = "nogroup-" + UUID.randomUUID() + "@hospital.org";

		String authenticateJson = """
				{
				    "token": "token",
				    "email": "%s",
				    "subject": "%s",
				    "fullName": "No Group User",
				    "issuer": "https://idp.hospital.org",
				    "groups": [],
				    "tenantId": "%s"
				}
				""".formatted(email, subject, tenantId.value());

		byte[] responseBytes = webTestClient.post()
				.uri("/api/test/oidc/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(authenticateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		assertThat(responseBytes).isNotNull();
		String userIdStr = JsonPath.read(new String(responseBytes, StandardCharsets.UTF_8), "$.userId");
		List<UserGroupMembership> memberships = userGroupMembershipRepository.findAllByUserId(new UserId(UUID.fromString(userIdStr)));
		assertThat(memberships).isEmpty();
	}
}
