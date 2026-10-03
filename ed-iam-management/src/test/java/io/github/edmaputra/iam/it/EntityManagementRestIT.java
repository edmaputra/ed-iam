package io.github.edmaputra.iam.it;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying administrative entity management REST endpoints
 * (Users, Roles, Groups, Scopes) using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class EntityManagementRestIT extends AbstractIntegrationTest {

	@BeforeEach
	void authenticateAsSuperAdmin() {
		this.webTestClient = this.webTestClient.mutate()
				.defaultHeader("Authorization", "Bearer " + createSuperAdminToken())
				.build();
	}

	@Test
	@DisplayName("Should perform full User lifecycle, status transitions, role assignment, and group membership via /api/v1/users")
	void shouldPerformUserLifecycleAndAssignments() {
		String email = "admin-user-" + UUID.randomUUID() + "@clinic.org";
		String password = "StrongPassword123!";
		String fullName = "Alice Admin";
		UUID tenantId = UUID.randomUUID();

		// 1. Create User
		String createJson = """
				{
				    "email": "%s",
				    "password": "%s",
				    "fullName": "%s",
				    "platformSuperAdmin": false
				}
				""".formatted(email, password, fullName);

		String expectedCreatedJson = """
				{
				    "email": "%s",
				    "fullName": "%s",
				    "status": "ACTIVE",
				    "platformSuperAdmin": false
				}
				""".formatted(email, fullName);

		byte[] response = webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.json(expectedCreatedJson, JsonCompareMode.LENIENT)
				.jsonPath("$.id").isNotEmpty()
				.returnResult()
				.getResponseBody();

		assertThat(response).isNotNull();
		String userId = com.jayway.jsonpath.JsonPath.read(new String(response), "$.id");

		// 2. Get User by ID
		webTestClient.get()
				.uri("/api/v1/users/" + userId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(userId)
				.jsonPath("$.email").isEqualTo(email);

		// 3. Lookup User by Email
		webTestClient.get()
				.uri("/api/v1/users/lookup?email=" + email)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(userId);

		// 3b. List Users with Pagination (default page & size)
		webTestClient.get()
				.uri("/api/v1/users")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray()
				.jsonPath("$.page").isEqualTo(0)
				.jsonPath("$.size").isEqualTo(20)
				.jsonPath("$.totalElements").isNumber()
				.jsonPath("$.totalPages").isNumber();

		// 3c. List Users with Custom Pagination
		webTestClient.get()
				.uri("/api/v1/users?page=0&size=5")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray()
				.jsonPath("$.page").isEqualTo(0)
				.jsonPath("$.size").isEqualTo(5);

		// 3d. List Users with invalid pagination parameters
		webTestClient.get()
				.uri("/api/v1/users?page=-1&size=10")
				.exchange()
				.expectStatus().isBadRequest();

		// 3e. Filter users by username (email substring)
		webTestClient.get()
				.uri("/api/v1/users?username=" + email)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content[?(@.email == '" + email + "')].email").isEqualTo(email);

		// 3f. Filter users by name
		webTestClient.get()
				.uri("/api/v1/users?name=Alice")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content[?(@.email == '" + email + "')].email").isEqualTo(email);

		// 3g. Filter users by status
		webTestClient.get()
				.uri("/api/v1/users?status=ACTIVE")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray();

		// 3h. Global search matching email
		webTestClient.get()
				.uri("/api/v1/users?search=" + email.substring(0, 15))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content[?(@.email == '" + email + "')].email").isEqualTo(email);

		// 3i. Filter users by collection of statuses
		webTestClient.get()
				.uri("/api/v1/users?status=ACTIVE&status=SUSPENDED")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray();

		// 4. Update Profile
		String updateJson = """
				{
				    "fullName": "Alice Updated"
				}
				""";

		webTestClient.put()
				.uri("/api/v1/users/" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.fullName").isEqualTo("Alice Updated");

		// 5. Suspend User
		String suspendJson = """
				{
				    "status": "SUSPENDED"
				}
				""";

		webTestClient.put()
				.uri("/api/v1/users/" + userId + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(suspendJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("SUSPENDED");

		// Suspended user cannot login
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isUnauthorized();

		// Reactivate user
		String activateJson = """
				{
				    "status": "ACTIVE"
				}
				""";

		webTestClient.put()
				.uri("/api/v1/users/" + userId + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(activateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("ACTIVE");

		// 6. Create a role to assign
		String roleJson = """
				{
				    "tenantId": "%s",
				    "code": "DOCTOR_%s",
				    "name": "Doctor Role",
				    "description": "Desc",
				    "permissions": ["PATIENT_READ"]
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 8));

		byte[] roleResp = webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		String roleId = com.jayway.jsonpath.JsonPath.read(new String(roleResp), "$.id");

		// Assign role to user
		String assignRoleJson = """
				{
				    "roleId": "%s",
				    "tenantId": "%s"
				}
				""".formatted(roleId, tenantId);

		byte[] assignResp = webTestClient.post()
				.uri("/api/v1/users/" + userId + "/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(assignRoleJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.userId").isEqualTo(userId)
				.jsonPath("$.roleId").isEqualTo(roleId)
				.returnResult()
				.getResponseBody();

		String assignmentId = com.jayway.jsonpath.JsonPath.read(new String(assignResp), "$.id");

		// List user roles
		webTestClient.get()
				.uri("/api/v1/users/" + userId + "/roles")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(assignmentId);

		// 6b. Filter users by role
		webTestClient.get()
				.uri("/api/v1/users?role=" + roleId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray();

		// 7. Create group and add user to group
		String groupJson = """
				{
				    "tenantId": "%s",
				    "code": "GRP_%s",
				    "name": "Clinical Team",
				    "description": "Desc"
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 8));

		byte[] groupResp = webTestClient.post()
				.uri("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(groupJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		String groupId = com.jayway.jsonpath.JsonPath.read(new String(groupResp), "$.id");

		// Add user to group
		webTestClient.post()
				.uri("/api/v1/users/" + userId + "/groups/" + groupId)
				.exchange()
				.expectStatus().isNoContent();

		// List user groups
		webTestClient.get()
				.uri("/api/v1/users/" + userId + "/groups")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(groupId);

		// 7b. Filter users by group
		webTestClient.get()
				.uri("/api/v1/users?group=" + groupId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content").isArray();

		// Remove user from group
		webTestClient.delete()
				.uri("/api/v1/users/" + userId + "/groups/" + groupId)
				.exchange()
				.expectStatus().isNoContent();

		// Revoke role
		webTestClient.delete()
				.uri("/api/v1/users/" + userId + "/roles/" + assignmentId)
				.exchange()
				.expectStatus().isNoContent();

		// Delete user
		webTestClient.delete()
				.uri("/api/v1/users/" + userId)
				.exchange()
				.expectStatus().isNoContent();

		// Lookup deleted user -> 404
		webTestClient.get()
				.uri("/api/v1/users/" + userId)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	@DisplayName("Should filter users with pagination by multiple values and composite criteria via /api/v1/users")
	void shouldFilterUsersByMultipleValues() {
		String runId = UUID.randomUUID().toString().substring(0, 8);
		UUID tenantId = UUID.randomUUID();

		String email1 = "multi-alpha-" + runId + "@clinic.org";
		String email2 = "multi-beta-" + runId + "@clinic.org";
		String email3 = "multi-gamma-" + runId + "@clinic.org";

		// 1. Create three users
		String u1Id = createUser(email1, "AlphaMulti " + runId);
		String u2Id = createUser(email2, "BetaMulti " + runId);
		String u3Id = createUser(email3, "GammaOther " + runId);

		try {
			// Update status of u2 -> SUSPENDED, u3 -> DEACTIVATED
			webTestClient.put()
					.uri("/api/v1/users/" + u2Id + "/status")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{\"status\": \"SUSPENDED\"}")
					.exchange()
					.expectStatus().isOk();

			webTestClient.put()
					.uri("/api/v1/users/" + u3Id + "/status")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue("{\"status\": \"DEACTIVATED\"}")
					.exchange()
					.expectStatus().isOk();

			// 2. Create Roles and assign
			String role1Code = ("R_ALPHA_" + runId).toUpperCase();
			String role2Code = ("R_BETA_" + runId).toUpperCase();
			String role1Id = createRole(tenantId, role1Code, "Role Alpha " + runId);
			String role2Id = createRole(tenantId, role2Code, "Role Beta " + runId);
			assignRole(u1Id, role1Id, tenantId);
			assignRole(u2Id, role2Id, tenantId);

			// Shared role assigned to both u1 and u2 (u3 does NOT have it)
			String sharedRoleCode = ("R_SHARED_" + runId).toUpperCase();
			String sharedRoleName = "PediatricSpecialist " + runId;
			String sharedRoleId = createRole(tenantId, sharedRoleCode, sharedRoleName);
			assignRole(u1Id, sharedRoleId, tenantId);
			assignRole(u2Id, sharedRoleId, tenantId);

			// 3. Create Groups and add members
			String group1Code = ("G_ALPHA_" + runId).toUpperCase();
			String group2Code = ("G_BETA_" + runId).toUpperCase();
			String group1Id = createGroup(tenantId, group1Code, "Group Alpha " + runId);
			String group2Id = createGroup(tenantId, group2Code, "Group Beta " + runId);
			addUserToGroup(u1Id, group1Id);
			addUserToGroup(u2Id, group2Id);

			// Shared group containing both u1 and u2 (u3 is NOT in it)
			String sharedGroupCode = ("G_SHARED_" + runId).toUpperCase();
			String sharedGroupName = "PediatricWard " + runId;
			String sharedGroupId = createGroup(tenantId, sharedGroupCode, sharedGroupName);
			addUserToGroup(u1Id, sharedGroupId);
			addUserToGroup(u2Id, sharedGroupId);

			// --- Scenario A: Filter by multiple usernames (repeat parameter) ---
			webTestClient.get()
					.uri("/api/v1/users?username=multi-alpha-" + runId + "&username=multi-beta-" + runId)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario B: Filter by multiple usernames (comma-separated) ---
			webTestClient.get()
					.uri("/api/v1/users?username=multi-alpha-" + runId + ",multi-beta-" + runId)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario C: Filter by multiple names ---
			webTestClient.get()
					.uri("/api/v1/users?name=AlphaMulti&name=BetaMulti")
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario D: Filter by multiple statuses (scoped by search to this test run) ---
			webTestClient.get()
					.uri("/api/v1/users?search=" + runId + "&status=ACTIVE&status=SUSPENDED")
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.totalElements").isEqualTo(2)
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario E: Filter by multiple statuses (comma-separated) ---
			webTestClient.get()
					.uri("/api/v1/users?search=" + runId + "&status=ACTIVE,SUSPENDED")
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.totalElements").isEqualTo(2)
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario F: Filter by multiple roles ---
			webTestClient.get()
					.uri("/api/v1/users?role=" + role1Code + "&role=" + role2Code)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario G: Filter by multiple groups ---
			webTestClient.get()
					.uri("/api/v1/users?group=" + group1Code + "&group=" + group2Code)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario H: Composite filtering (multiple criteria combined) ---
			webTestClient.get()
					.uri("/api/v1/users?status=ACTIVE&name=AlphaMulti&role=" + role1Code)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content.length()").isEqualTo(1)
					.jsonPath("$.content[0].email").isEqualTo(email1);

			// --- Scenario I: Multiple status values + single group filter ---
			webTestClient.get()
					.uri("/api/v1/users?status=ACTIVE&status=SUSPENDED&group=" + group1Code)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content.length()").isEqualTo(1)
					.jsonPath("$.content[0].email").isEqualTo(email1);

			// --- Scenario J: Search / filter by role name returns all users with that role ---
			webTestClient.get()
					.uri("/api/v1/users?role=" + sharedRoleName)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			webTestClient.get()
					.uri("/api/v1/users?search=" + sharedRoleName)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.totalElements").isEqualTo(2)
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			// --- Scenario K: Search / filter by group name returns all users within that group ---
			webTestClient.get()
					.uri("/api/v1/users?group=" + sharedGroupName)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();

			webTestClient.get()
					.uri("/api/v1/users?search=" + sharedGroupName)
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.totalElements").isEqualTo(2)
					.jsonPath("$.content[?(@.email == '" + email1 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email2 + "')]").isNotEmpty()
					.jsonPath("$.content[?(@.email == '" + email3 + "')]").doesNotExist();
		}
		finally {
			deleteUserIfExists(u1Id);
			deleteUserIfExists(u2Id);
			deleteUserIfExists(u3Id);
		}
	}

	private String createUser(String email, String fullName) {
		String json = """
				{
				    "email": "%s",
				    "password": "Password123!",
				    "fullName": "%s",
				    "platformSuperAdmin": false
				}
				""".formatted(email, fullName);

		byte[] response = webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(json)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		return com.jayway.jsonpath.JsonPath.read(new String(response), "$.id");
	}

	private String createRole(UUID tenantId, String code, String name) {
		String json = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "%s",
				    "description": "Desc",
				    "permissions": ["USER_READ"]
				}
				""".formatted(tenantId, code, name);

		byte[] response = webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(json)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		return com.jayway.jsonpath.JsonPath.read(new String(response), "$.id");
	}

	private void assignRole(String userId, String roleId, UUID tenantId) {
		String json = """
				{
				    "roleId": "%s",
				    "tenantId": "%s"
				}
				""".formatted(roleId, tenantId);

		webTestClient.post()
				.uri("/api/v1/users/" + userId + "/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(json)
				.exchange()
				.expectStatus().isCreated();
	}

	private String createGroup(UUID tenantId, String code, String name) {
		String json = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "%s",
				    "description": "Desc"
				}
				""".formatted(tenantId, code, name);

		byte[] response = webTestClient.post()
				.uri("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(json)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		return com.jayway.jsonpath.JsonPath.read(new String(response), "$.id");
	}

	private void addUserToGroup(String userId, String groupId) {
		webTestClient.post()
				.uri("/api/v1/users/" + userId + "/groups/" + groupId)
				.exchange()
				.expectStatus().isNoContent();
	}

	private void deleteUserIfExists(String userId) {
		try {
			webTestClient.delete()
					.uri("/api/v1/users/" + userId)
					.exchange();
		}
		catch (Exception ignored) {
		}
	}

	@Test
	@DisplayName("Should perform Role CRUD and permission updates via /api/v1/roles")
	void shouldPerformRoleCrud() {
		UUID tenantId = UUID.randomUUID();
		String code = ("NURSE_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();

		// 1. Create Role
		String createJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Nurse",
				    "description": "Nursing Staff",
				    "permissions": ["CHART_READ"]
				}
				""".formatted(tenantId, code);

		byte[] resp = webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code)
				.jsonPath("$.permissions[0]").isEqualTo("CHART_READ")
				.returnResult()
				.getResponseBody();

		String roleId = com.jayway.jsonpath.JsonPath.read(new String(resp), "$.id");

		// 2. Get Role by ID
		webTestClient.get()
				.uri("/api/v1/roles/" + roleId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(roleId);

		// 3. List Roles by Tenant
		webTestClient.get()
				.uri("/api/v1/roles?tenantId=" + tenantId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[?(@.id == '" + roleId + "')]").isNotEmpty();

		// 4. Update Role
		String updateJson = """
				{
				    "name": "Senior Nurse",
				    "description": "Senior Nursing Staff",
				    "permissions": ["CHART_READ", "CHART_WRITE"]
				}
				""";

		webTestClient.put()
				.uri("/api/v1/roles/" + roleId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Senior Nurse")
				.jsonPath("$.permissions").isArray();

		// 5. Delete Role
		webTestClient.delete()
				.uri("/api/v1/roles/" + roleId)
				.exchange()
				.expectStatus().isNoContent();

		// Verify 404
		webTestClient.get()
				.uri("/api/v1/roles/" + roleId)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	@DisplayName("Should perform Group CRUD, group role assignments, and member listings via /api/v1/groups")
	void shouldPerformGroupCrudAndAssignments() {
		UUID tenantId = UUID.randomUUID();
		String code = ("SURGERY_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();

		// 1. Create Group
		String createJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Surgery Department",
				    "description": "Surgical Team",
				    "externalIdpGroupName": "surgeons"
				}
				""".formatted(tenantId, code);

		byte[] resp = webTestClient.post()
				.uri("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(code)
				.jsonPath("$.externalIdpGroupName").isEqualTo("surgeons")
				.returnResult()
				.getResponseBody();

		String groupId = com.jayway.jsonpath.JsonPath.read(new String(resp), "$.id");

		// 2. Get Group by ID
		webTestClient.get()
				.uri("/api/v1/groups/" + groupId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(groupId);

		// 3. List Groups by Tenant
		webTestClient.get()
				.uri("/api/v1/groups?tenantId=" + tenantId)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(groupId);

		// 4. Update Group
		String updateJson = """
				{
				    "name": "General Surgery",
				    "description": "Updated Surgical Team",
				    "externalIdpGroupName": "gen-surgeons"
				}
				""";

		webTestClient.put()
				.uri("/api/v1/groups/" + groupId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("General Surgery");

		// 5. Create a role and assign to group
		String roleJson = """
				{
				    "tenantId": "%s",
				    "code": "SURGEON_%s",
				    "name": "Surgeon",
				    "description": "Desc",
				    "permissions": ["SURGERY_EXECUTE"]
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 8));

		byte[] roleResp = webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();

		String roleId = com.jayway.jsonpath.JsonPath.read(new String(roleResp), "$.id");

		String assignJson = """
				{
				    "roleId": "%s",
				    "tenantId": "%s"
				}
				""".formatted(roleId, tenantId);

		byte[] assignResp = webTestClient.post()
				.uri("/api/v1/groups/" + groupId + "/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(assignJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.groupId").isEqualTo(groupId)
				.returnResult()
				.getResponseBody();

		String assignmentId = com.jayway.jsonpath.JsonPath.read(new String(assignResp), "$.id");

		// List group roles
		webTestClient.get()
				.uri("/api/v1/groups/" + groupId + "/roles")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(assignmentId);

		// Revoke role
		webTestClient.delete()
				.uri("/api/v1/groups/" + groupId + "/roles/" + assignmentId)
				.exchange()
				.expectStatus().isNoContent();

		// Delete group
		webTestClient.delete()
				.uri("/api/v1/groups/" + groupId)
				.exchange()
				.expectStatus().isNoContent();

		// Verify 404
		webTestClient.get()
				.uri("/api/v1/groups/" + groupId)
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	@DisplayName("Should perform Scope tree operations, child node creation, and move via /api/v1/scopes")
	void shouldPerformScopeHierarchyOperations() {
		UUID tenantId = UUID.randomUUID();

		// 1. Create Root Scope
		String rootJson = """
				{
				    "tenantId": "%s",
				    "code": "HOSPITAL_%s",
				    "name": "Main Hospital"
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		byte[] rootResp = webTestClient.post()
				.uri("/api/v1/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(rootJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.level").isEqualTo(1)
				.returnResult()
				.getResponseBody();

		String rootId = com.jayway.jsonpath.JsonPath.read(new String(rootResp), "$.id");

		// 2. Create Child Scope
		String childJson = """
				{
				    "tenantId": "%s",
				    "parentId": "%s",
				    "code": "ICU_%s",
				    "name": "Intensive Care Unit"
				}
				""".formatted(tenantId, rootId, UUID.randomUUID().toString().substring(0, 6));

		byte[] childResp = webTestClient.post()
				.uri("/api/v1/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(childJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.level").isEqualTo(2)
				.jsonPath("$.parentId").isEqualTo(rootId)
				.returnResult()
				.getResponseBody();

		String childId = com.jayway.jsonpath.JsonPath.read(new String(childResp), "$.id");

		// 3. Get Scope Tree via X-Tenant-ID header
		webTestClient.get()
				.uri("/api/v1/scopes/tree")
				.header("X-Tenant-ID", tenantId.toString())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isEqualTo(rootId)
				.jsonPath("$[0].children[0].id").isEqualTo(childId);

		// Get Flat Scope List
		webTestClient.get()
				.uri("/api/v1/scopes")
				.header("X-Tenant-ID", tenantId.toString())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].id").isNotEmpty();

		// Get Scope By ID
		webTestClient.get()
				.uri("/api/v1/scopes/" + childId)
				.header("X-Tenant-ID", tenantId.toString())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(childId);

		// 4. Update Scope Metadata
		String updateJson = """
				{
				    "code": "ICU_UPDATED",
				    "name": "Intensive Care Unit (Renovated)"
				}
				""";

		webTestClient.put()
				.uri("/api/v1/scopes/" + childId + "?tenantId=" + tenantId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.code").isEqualTo("ICU_UPDATED");

		// Move Scope Node to another Root
		String secondRootJson = """
				{
				    "tenantId": "%s",
				    "code": "OUTPATIENT_%s",
				    "name": "Outpatient Clinic"
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		byte[] secondRootResp = webTestClient.post()
				.uri("/api/v1/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(secondRootJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.returnResult()
				.getResponseBody();
		String secondRootId = com.jayway.jsonpath.JsonPath.read(new String(secondRootResp), "$.id");

		String moveJson = """
				{
				    "newParentId": "%s"
				}
				""".formatted(secondRootId);

		webTestClient.post()
				.uri("/api/v1/scopes/" + childId + "/move?tenantId=" + tenantId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(moveJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.parentId").isEqualTo(secondRootId);

		// 5. Delete child then roots
		webTestClient.delete()
				.uri("/api/v1/scopes/" + childId + "?tenantId=" + tenantId)
				.exchange()
				.expectStatus().isNoContent();

		webTestClient.delete()
				.uri("/api/v1/scopes/" + secondRootId + "?tenantId=" + tenantId)
				.exchange()
				.expectStatus().isNoContent();

		webTestClient.delete()
				.uri("/api/v1/scopes/" + rootId + "?tenantId=" + tenantId)
				.exchange()
				.expectStatus().isNoContent();
	}

	@Test
	@DisplayName("Should reject invalid User requests with 422 Unprocessable Entity and RFC 9457 structured errors")
	void shouldRejectInvalidUserRequestsWithValidationErrors() {
		// 1. Create User with missing/blank fields
		String emptyUserJson = """
				{
				    "email": "",
				    "password": "",
				    "fullName": ""
				}
				""";

		webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(emptyUserJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("https://api.edmaputra.github.io/problems/validation-error")
				.jsonPath("$.title").isEqualTo("Validation Failed")
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.instance").isEqualTo("/api/v1/users")
				.jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'password')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'fullName')]").isNotEmpty();

		// 2. Create User with invalid email format
		String invalidEmailJson = """
				{
				    "email": "not-an-email",
				    "password": "ValidPassword123!",
				    "fullName": "Alice Valid"
				}
				""";

		webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(invalidEmailJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[0].field").isEqualTo("email")
				.jsonPath("$.errors[0].message").value(String.class, msg ->
						assertThat(msg).contains("valid email address"));

		// 3. Create User with short password (< 8 chars)
		String shortPasswordJson = """
				{
				    "email": "%s",
				    "password": "short",
				    "fullName": "Alice Valid"
				}
				""".formatted("valid-" + UUID.randomUUID() + "@clinic.org");

		webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(shortPasswordJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[0].field").isEqualTo("password")
				.jsonPath("$.errors[0].message").value(String.class, msg ->
						assertThat(msg).contains("at least 8 characters"));

		// 4. Update Profile with blank full name
		String blankUpdateJson = """
				{
				    "fullName": "   "
				}
				""";

		webTestClient.put()
				.uri("/api/v1/users/" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankUpdateJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'fullName')]").isNotEmpty();

		// 5. Change User Status with null status
		webTestClient.put()
				.uri("/api/v1/users/" + UUID.randomUUID() + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'status')]").isNotEmpty();

		// 6. Assign Role with null roleId and null tenantId
		webTestClient.post()
				.uri("/api/v1/users/" + UUID.randomUUID() + "/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'roleId')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'tenantId')]").isNotEmpty();
	}

	@Test
	@DisplayName("Should reject invalid Role requests with 422 Unprocessable Entity or 400 Bad Request")
	void shouldRejectInvalidRoleRequestsWithValidationErrors() {
		// 1. Create Role with blank code and blank name
		String invalidRoleJson = """
				{
				    "tenantId": "%s",
				    "code": "  ",
				    "name": ""
				}
				""".formatted(UUID.randomUUID());

		webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(invalidRoleJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("https://api.edmaputra.github.io/problems/validation-error")
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'code')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();

		// 2. Create Role without tenant ID (missing in header and body)
		String noTenantRoleJson = """
				{
				    "code": "TEST_ROLE",
				    "name": "Test Role"
				}
				""";

		webTestClient.post()
				.uri("/api/v1/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(noTenantRoleJson)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").value(String.class, detail ->
						assertThat(detail).contains("Tenant ID must be specified"));

		// 3. Update Role with blank name
		String blankRoleNameJson = """
				{
				    "name": " "
				}
				""";

		webTestClient.put()
				.uri("/api/v1/roles/" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankRoleNameJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();
	}

	@Test
	@DisplayName("Should reject invalid Group requests with 422 Unprocessable Entity")
	void shouldRejectInvalidGroupRequestsWithValidationErrors() {
		// 1. Create Group with missing tenantId, blank code, blank name
		webTestClient.post()
				.uri("/api/v1/groups")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'tenantId')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'code')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();

		// 2. Update Group with blank name
		String blankGroupNameJson = """
				{
				    "name": ""
				}
				""";

		webTestClient.put()
				.uri("/api/v1/groups/" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankGroupNameJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();

		// 3. Assign Group Role with null roleId and null tenantId
		webTestClient.post()
				.uri("/api/v1/groups/" + UUID.randomUUID() + "/roles")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'roleId')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'tenantId')]").isNotEmpty();
	}

	@Test
	@DisplayName("Should reject invalid Scope requests with 422 Unprocessable Entity")
	void shouldRejectInvalidScopeRequestsWithValidationErrors() {
		// 1. Create Scope with missing tenantId, blank code, blank name
		webTestClient.post()
				.uri("/api/v1/scopes")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'tenantId')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'code')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();

		// 2. Update Scope with blank name
		String blankScopeNameJson = """
				{
				    "code": "ICU",
				    "name": "   "
				}
				""";

		webTestClient.put()
				.uri("/api/v1/scopes/" + UUID.randomUUID() + "?tenantId=" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankScopeNameJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty();
	}

	@Test
	@DisplayName("Should reject invalid Auth requests with 422 Unprocessable Entity or 400 Bad Request")
	void shouldRejectInvalidAuthRequestsWithValidationErrors() {
		// 1. Login with blank email and blank password
		String blankLoginJson = """
				{
				    "email": "",
				    "password": ""
				}
				""";

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankLoginJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty()
				.jsonPath("$.errors[?(@.field == 'password')]").isNotEmpty();

		// 2. Login with malformed email
		String malformedEmailLoginJson = """
				{
				    "email": "not-a-valid-email",
				    "password": "Password123!"
				}
				""";

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(malformedEmailLoginJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[0].field").isEqualTo("email")
				.jsonPath("$.errors[0].message").value(String.class, msg ->
						assertThat(msg).contains("valid email address"));

		// 3. Refresh Token with blank token
		String blankRefreshJson = """
				{
				    "refreshToken": ""
				}
				""";

		webTestClient.post()
				.uri("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(blankRefreshJson)
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.errors[?(@.field == 'refreshToken')]").isNotEmpty();

		// 4. Switch Tenant without tenant ID
		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").value(String.class, detail ->
						assertThat(detail).contains("Target tenant ID must be provided"));
	}
}
