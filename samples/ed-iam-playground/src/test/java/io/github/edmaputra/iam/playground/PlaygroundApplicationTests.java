package io.github.edmaputra.iam.playground;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.playground.seeder.PlaygroundDataSeeder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying the ed-iam Playground sample application,
 * UI serving, tenant auto-resolution, scope authorization, and tenant switching.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaygroundApplicationTests {

	@LocalServerPort
	private int port;

	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		this.webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.responseTimeout(Duration.ofSeconds(10))
				.build();
	}

	@Test
	@DisplayName("Thymeleaf UI dashboard should load successfully on GET /")
	void shouldServeThymeleafDashboard() {
		webTestClient.get()
				.uri("/")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
				.expectBody(String.class)
				.value(html -> {
					assertThat(html).contains("ed-iam");
					assertThat(html).contains("Interactive Playground");
					assertThat(html).contains("Dr. Gregory House");
					assertThat(html).contains("Custom Credentials Login");
					assertThat(html).contains("customLoginForm");
				});
	}

	@Test
	@DisplayName("Should login as Dr. House, access Cardiology (in-scope), and be rejected in Pediatrics (out-of-scope)")
	void shouldEnforceHierarchicalScopeAuthorization() {
		// 1. Zero-config login as Dr. Gregory House (Single-Tenant auto-resolution)
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		byte[] loginResponseBody = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");
		assertThat(token).isNotBlank();

		// 2. Query Cardiology Department (In-Scope -> 200 OK)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].departmentName").isEqualTo("Cardiology Department");

		// 3. Query ICU Ward (Subchild Scope of Cardiology -> 200 OK)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.ICU_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].departmentName").isEqualTo("Cardiology ICU Ward");

		// 4. Query Pediatrics Department (Outside Scope -> 403 Forbidden)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.PEDIATRICS_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	@DisplayName("Should login as multi-tenant consultant and switch tenant context")
	void shouldSupportMultiTenantLoginAndSwitching() {
		// 1. Login as Dr. Allison Cameron
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.CONSULTANT_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		byte[] loginResponseBody = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.user.availableTenantIds.length()").isEqualTo(2)
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");

		// 2. Switch tenant to St. Jude Medical Center
		String switchJson = """
				{
				    "tenantId": "%s"
				}
				""".formatted(PlaygroundDataSeeder.ST_JUDE_TENANT_ID);

		byte[] switchResponseBody = webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(switchJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.user.tenantId").isEqualTo(PlaygroundDataSeeder.ST_JUDE_TENANT_ID.toString())
				.returnResult().getResponseBody();

		String switchedToken = JsonPath.read(new String(switchResponseBody), "$.accessToken");

		// 3. Verify Actor Context on switched tenant
		webTestClient.get()
				.uri("/api/v1/playground/actor-context")
				.headers(headers -> headers.setBearerAuth(switchedToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.iamTenantId").isEqualTo(PlaygroundDataSeeder.ST_JUDE_TENANT_ID.toString())
				.jsonPath("$.hostBridgeTenantId").isEqualTo(PlaygroundDataSeeder.ST_JUDE_TENANT_ID.toString())
				.jsonPath("$.roles[0]").isEqualTo("AUDITOR");
	}

	@Test
	@DisplayName("Should reject login for suspended user account with HTTP 401")
	void shouldEnforceUserSuspensionOnLogin() {
		String suspendedLoginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.SUSPENDED_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(suspendedLoginJson)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectBody()
				.jsonPath("$.detail").value(detail -> assertThat(detail.toString().toLowerCase()).contains("suspended"));
	}

	@Test
	@DisplayName("Should resolve effective permissions and scopes via Group Role Inheritance (Nurse Jackie)")
	void shouldSupportGroupRoleInheritance() {
		// 1. Login as Nurse Jackie (Member of SURGICAL_TEAM, zero direct roles)
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.NURSE_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		byte[] loginResponseBody = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");

		// 2. Verify Actor Context demonstrates group-inherited role and permissions
		webTestClient.get()
				.uri("/api/v1/playground/actor-context")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.roles[0]").isEqualTo("CLINICIAN")
				.jsonPath("$.permissions").value(perms -> assertThat(perms.toString()).contains("PATIENT_READ"));

		// 3. Query Cardiology patients (Inherited CLINICIAN has PATIENT_READ across Metro Root subtree)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].departmentName").isEqualTo("Cardiology Department");
	}

	@Test
	@DisplayName("Should support IAM management endpoints: create role, change user status, and query scope tree")
	void shouldSupportManagementEndpoints() {
		// 1. Login as Dr. Cuddy (Admin)
		String adminLoginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.ADMIN_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		byte[] adminResponseBody = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(adminLoginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult().getResponseBody();

		String adminToken = JsonPath.read(new String(adminResponseBody), "$.accessToken");

		// 2. Create custom role via POST /api/v1/roles
		String createRoleJson = """
				{
				    "tenantId": "%s",
				    "code": "TEST_ROLE",
				    "name": "Test Role",
				    "description": "Integration test role",
				    "permissions": ["PATIENT_READ"]
				}
				""".formatted(PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID);

		webTestClient.post()
				.uri("/api/v1/roles")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createRoleJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo("TEST_ROLE");

		// 3. Lookup suspended user ID via GET /api/v1/users?email=...
		byte[] userResponseBody = webTestClient.get()
				.uri("/api/v1/users?email=" + PlaygroundDataSeeder.SUSPENDED_EMAIL)
				.headers(headers -> headers.setBearerAuth(adminToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("SUSPENDED")
				.returnResult().getResponseBody();

		String userId = JsonPath.read(new String(userResponseBody), "$.id");

		// 4. Activate user via PUT /api/v1/users/{id}/status
		webTestClient.put()
				.uri("/api/v1/users/" + userId + "/status")
				.headers(headers -> headers.setBearerAuth(adminToken))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "status": "ACTIVE"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("ACTIVE");

		// 5. Verify user can now log in
		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(PlaygroundDataSeeder.SUSPENDED_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD))
				.exchange()
				.expectStatus().isOk();

		// 6. Query dynamic scope tree via GET /api/v1/scopes/tree
		webTestClient.get()
				.uri("/api/v1/scopes/tree")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].name").isEqualTo("Metro General Hospital")
				.jsonPath("$[0].children").isNotEmpty();
	}

	@Test
	@DisplayName("Should create user manually, assign scoped clinician role, log in with custom credentials, and enforce permissions/scopes")
	void shouldSupportCustomCredentialsLoginAndVerifyPermissions() {
		// 1. Authenticate as Admin
		byte[] adminLoginResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(PlaygroundDataSeeder.ADMIN_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String adminToken = JsonPath.read(new String(adminLoginResponse), "$.accessToken");

		// 2. Fetch Metro roles to retrieve CLINICIAN role ID
		byte[] rolesResponse = webTestClient.get()
				.uri("/api/v1/roles")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult().getResponseBody();

		List<String> clinicianRoles = JsonPath.read(new String(rolesResponse), "$[?(@.code == 'CLINICIAN')].id");
		assertThat(clinicianRoles).isNotEmpty();
		String clinicianRoleId = clinicianRoles.get(0);

		// 3. Admin creates a new custom user
		String customEmail = "custom-cardiologist@metro.org";
		String customPassword = "CustomSecretPass123!";
		byte[] createUserResponse = webTestClient.post()
				.uri("/api/v1/users")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s",
						    "fullName": "Dr. Custom Cardiologist",
						    "platformSuperAdmin": false
						}
						""".formatted(customEmail, customPassword))
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.email").isEqualTo(customEmail)
				.returnResult().getResponseBody();

		String customUserId = JsonPath.read(new String(createUserResponse), "$.id");

		// 4. Admin assigns CLINICIAN role scoped to Cardiology Department
		webTestClient.post()
				.uri("/api/v1/users/" + customUserId + "/roles")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "roleId": "%s",
						    "tenantId": "%s",
						    "scopeNodeId": "%s"
						}
						""".formatted(clinicianRoleId, PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID, PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID))
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.roleId").isEqualTo(clinicianRoleId)
				.jsonPath("$.scopeNodeId").isEqualTo(PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID.toString());

		// 5. Test invalid password rejection
		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "WrongPassword123!"
						}
						""".formatted(customEmail))
				.exchange()
				.expectStatus().isUnauthorized();

		// 6. Test successful login with custom credentials
		byte[] loginResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(customEmail, customPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String customUserToken = JsonPath.read(new String(loginResponse), "$.accessToken");
		assertThat(customUserToken).isNotBlank();

		// 7. Verify Cardiology scope access (In-Scope -> 200 OK)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(customUserToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].departmentName").isEqualTo("Cardiology Department");

		// 8. Verify ICU scope access (Hierarchical Subchild of Cardiology -> 200 OK)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.ICU_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(customUserToken))
				.exchange()
				.expectStatus().isOk();

		// 9. Verify Pediatrics scope rejection (Out-of-Scope -> 403 Forbidden)
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.PEDIATRICS_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(customUserToken))
				.exchange()
				.expectStatus().isForbidden();

		// 10. Verify endpoint-level permission check: cannot manage roles (403 Forbidden)
		webTestClient.post()
				.uri("/api/v1/roles")
				.headers(headers -> {
					headers.setBearerAuth(customUserToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "tenantId": "%s",
						    "code": "TEST_ROLE",
						    "name": "Test Role",
						    "description": "Test",
						    "permissions": ["PATIENT_READ"]
						}
						""".formatted(PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID))
				.exchange()
				.expectStatus().isForbidden();
	}
}
