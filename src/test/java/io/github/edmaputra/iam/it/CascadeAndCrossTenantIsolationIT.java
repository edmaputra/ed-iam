package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying database constraint cascades (PostgreSQL foreign keys)
 * and cross-tenant duplicate code isolation using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class CascadeAndCrossTenantIsolationIT extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserIdentityRepository userIdentityRepository;

	@Autowired
	private UserGroupMembershipRepository userGroupMembershipRepository;

	@Autowired
	private UserRoleAssignmentRepository userRoleAssignmentRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;

	@Test
	@DisplayName("Should allow identical codes across different tenants but reject duplicates within same tenant via REST")
	void shouldIsolateCodesAcrossTenants() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		String code = "SHARED_CODE";

		// 1. Scope Node: Can exist in both tenants
		String scopeJsonA = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Scope in Tenant A"
				}
				""".formatted(tenantA, code);

		String expectedScopeJsonA = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Scope in Tenant A"
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(scopeJsonA)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.json(expectedScopeJsonA, JsonCompareMode.LENIENT);

		String scopeJsonB = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Scope in Tenant B"
				}
				""".formatted(tenantB, code);

		String expectedScopeJsonB = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Scope in Tenant B"
				}
				""".formatted(tenantB, code);

		webTestClient.post()
				.uri("/api/test/admin/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(scopeJsonB)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.json(expectedScopeJsonB, JsonCompareMode.LENIENT);

		// Duplicate scope code in same tenant (Tenant A) -> 400 Bad Request
		String duplicateScopeJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Duplicate Scope"
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(duplicateScopeJson)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("already exists for this tenant"));

		// 2. Group: Can exist in both tenants
		String groupJsonA = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Group A",
				    "description": "Desc A"
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(groupJsonA)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code);

		String groupJsonB = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Group B",
				    "description": "Desc B"
				}
				""".formatted(tenantB, code);

		webTestClient.post()
				.uri("/api/test/admin/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(groupJsonB)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code);

		// Duplicate group in same tenant -> 400 Bad Request
		String duplicateGroupJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Duplicate Group",
				    "description": "Desc Duplicate"
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(duplicateGroupJson)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("Duplicate group code"));

		// 3. Role: Can exist in both tenants
		String roleJsonA = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Role A",
				    "description": "Desc A",
				    "permissions": ["READ"]
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJsonA)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code);

		String roleJsonB = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Role B",
				    "description": "Desc B",
				    "permissions": ["WRITE"]
				}
				""".formatted(tenantB, code);

		webTestClient.post()
				.uri("/api/test/admin/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJsonB)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code);

		// Duplicate role in same tenant -> 400 Bad Request
		String duplicateRoleJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Duplicate Role",
				    "description": "Desc Duplicate",
				    "permissions": ["EXEC"]
				}
				""".formatted(tenantA, code);

		webTestClient.post()
				.uri("/api/test/admin/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(duplicateRoleJson)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("Duplicate role code"));
	}

	@Test
	@DisplayName("Should cascade delete user identities, group memberships, and role assignments when user is deleted via REST")
	void shouldCascadeDeleteOnUserRemoval() {
		TenantId tenantId = TenantId.generate();

		// 1. Seed user, role, group
		User user = User.create("cascade-" + UUID.randomUUID() + "@clinic.org", "hash", "Cascade User", false);
		user = userRepository.save(user);

		Role role = Role.createCustom(tenantId, "TEMP_ROLE", "Temp Role", "Desc", Set.of("ACTION"));
		role = roleRepository.save(role);

		Group group = Group.create(tenantId, "TEMP_GRP", "Temp Group", "Desc", null);
		group = groupRepository.save(group);

		// 2. Add identity, membership, and role assignment
		UserIdentity identity = UserIdentity.create(user.getId(), ProviderType.OIDC_GENERIC, "sub-" + UUID.randomUUID(), "https://idp.com");
		userIdentityRepository.save(identity);

		userGroupMembershipRepository.save(UserGroupMembership.of(group.getId(), user.getId()));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId));

		// Verify initial cascade status via REST
		String expectedInitialStatus = """
				{
				    "userExists": true,
				    "identitiesCount": 1,
				    "membershipsCount": 1,
				    "roleAssignmentsCount": 1
				}
				""";

		webTestClient.get()
				.uri("/api/test/admin/users/" + user.getId().value() + "/cascade-status")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedInitialStatus, JsonCompareMode.LENIENT);

		// 3. Delete user via REST
		webTestClient.delete()
				.uri("/api/test/admin/users/" + user.getId().value())
				.exchange()
				.expectStatus().isNoContent();

		// 4. Verify cascade deletions in child records via REST
		String expectedDeletedStatus = """
				{
				    "userExists": false,
				    "identitiesCount": 0,
				    "membershipsCount": 0,
				    "roleAssignmentsCount": 0
				}
				""";

		webTestClient.get()
				.uri("/api/test/admin/users/" + user.getId().value() + "/cascade-status")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedDeletedStatus, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should cascade delete group memberships and group role assignments when group is deleted via REST")
	void shouldCascadeDeleteOnGroupRemoval() {
		TenantId tenantId = TenantId.generate();

		User user = User.create("grp-cascade-" + UUID.randomUUID() + "@clinic.org", "hash", "User", false);
		user = userRepository.save(user);

		Role role = Role.createCustom(tenantId, "GRP_ROLE", "Group Role", "Desc", Set.of("OP"));
		role = roleRepository.save(role);

		Group group = Group.create(tenantId, "TO_DELETE", "To Delete", "Desc", null);
		group = groupRepository.save(group);

		userGroupMembershipRepository.save(UserGroupMembership.of(group.getId(), user.getId()));
		groupRoleAssignmentRepository.save(GroupRoleAssignment.createTenantWide(group.getId(), role.getId(), tenantId));

		// Verify initial cascade status via REST
		String expectedInitialStatus = """
				{
				    "groupExists": true,
				    "membershipsCount": 1,
				    "assignmentsCount": 1
				}
				""";

		webTestClient.get()
				.uri("/api/test/admin/groups/" + group.getId().value() + "/cascade-status")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedInitialStatus, JsonCompareMode.LENIENT);

		// Delete group via REST
		webTestClient.delete()
				.uri("/api/test/admin/groups/" + group.getId().value())
				.exchange()
				.expectStatus().isNoContent();

		// Verify cascade deletions via REST
		String expectedDeletedStatus = """
				{
				    "groupExists": false,
				    "membershipsCount": 0,
				    "assignmentsCount": 0
				}
				""";

		webTestClient.get()
				.uri("/api/test/admin/groups/" + group.getId().value() + "/cascade-status")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedDeletedStatus, JsonCompareMode.LENIENT);
	}
}
