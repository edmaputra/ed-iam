package io.github.edmaputra.iam.playground;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.playground.seeder.PlaygroundDataSeeder;
import io.github.edmaputra.iam.playground.service.PlaygroundSimulatedMailService;

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

	@Autowired
	private PlaygroundSimulatedMailService mailService;

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
					assertThat(html).contains("mfaModal");
					assertThat(html).contains("mfaChallengeModal");
					assertThat(html).contains("Observability &amp; SIEM Audit");
					assertThat(html).contains("panelObservability");
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

		// 3. Lookup suspended user ID via GET /api/v1/users/lookup?email=...
		byte[] userResponseBody = webTestClient.get()
				.uri("/api/v1/users/lookup?email=" + PlaygroundDataSeeder.SUSPENDED_EMAIL)
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

	@Test
	@DisplayName("Should list active sessions and revoke JWT access token upon logout")
	void shouldSupportSessionListingAndRevocationOnLogout() {
		// 1. Zero-config login as Dr. Gregory House
		byte[] loginResponseBody = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");
		assertThat(token).isNotBlank();

		// 2. Query active sessions (GET /api/v1/auth/sessions) -> returns active session matching current token
		webTestClient.get()
				.uri("/api/v1/auth/sessions")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").value(len -> assertThat((Integer) len).isGreaterThanOrEqualTo(1))
				.jsonPath("$[?(@.current == true)]").value(list -> assertThat((List<?>) list).hasSize(1));

		// 3. Confirm token is valid by querying actor context
		webTestClient.get()
				.uri("/api/v1/playground/actor-context")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk();

		// 4. Logout (POST /api/v1/auth/logout) -> 204 No Content
		webTestClient.post()
				.uri("/api/v1/auth/logout")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isNoContent();

		// 5. Query actor context again with the revoked token -> 401 Unauthorized
		webTestClient.get()
				.uri("/api/v1/playground/actor-context")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should support administrative session listing, termination, and lockout remediation")
	void shouldSupportAdministrativeSessionAndLockoutOversight() {
		// 1. Login as Admin Dr. Lisa Cuddy
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
				.returnResult().getResponseBody();

		String adminToken = JsonPath.read(new String(adminLoginResponse), "$.accessToken");
		assertThat(adminToken).isNotBlank();

		// 2. Login as Dr. Gregory House to create an active session
		byte[] doctorLoginResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult().getResponseBody();

		String doctorToken = JsonPath.read(new String(doctorLoginResponse), "$.accessToken");
		String doctorUserId = JsonPath.read(new String(doctorLoginResponse), "$.user.id");
		assertThat(doctorToken).isNotBlank();

		// 3. Admin lists Dr. House's sessions (GET /api/v1/users/{id}/sessions)
		byte[] sessionsResponse = webTestClient.get()
				.uri("/api/v1/users/" + doctorUserId + "/sessions")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").value(len -> assertThat((Integer) len).isGreaterThanOrEqualTo(1))
				.returnResult().getResponseBody();

		String sessionId = JsonPath.read(new String(sessionsResponse), "$[0].sessionId");
		assertThat(sessionId).isNotBlank();

		// 4. Admin terminates Dr. House's session (DELETE /api/v1/users/{id}/sessions/{sessionId})
		webTestClient.delete()
				.uri("/api/v1/users/" + doctorUserId + "/sessions/" + sessionId)
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isNoContent();

		// 5. Doctor tries to access Cardiology with the terminated session token -> 401 Unauthorized
		webTestClient.get()
				.uri("/api/v1/playground/patients?departmentId=" + PlaygroundDataSeeder.CARDIOLOGY_SCOPE_ID)
				.headers(headers -> headers.setBearerAuth(doctorToken))
				.exchange()
				.expectStatus().isUnauthorized();

		// 6. Admin checks lockout status for Dr. House (GET /api/v1/users/{id}/lockout)
		webTestClient.get()
				.uri("/api/v1/users/" + doctorUserId + "/lockout")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.locked").isEqualTo(false);

		// 7. Admin unlocks Dr. House (POST /api/v1/users/{id}/unlock)
		webTestClient.post()
				.uri("/api/v1/users/" + doctorUserId + "/unlock")
				.headers(headers -> {
					headers.setBearerAuth(adminToken);
					headers.add("X-Tenant-ID", PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID.toString());
				})
				.exchange()
				.expectStatus().isNoContent();
	}

	@Test
	@DisplayName("Should complete MFA enrollment, enforce TOTP/backup login challenge, and support disabling MFA")
	void shouldSupportMfaLifecycleAndEnforceLoginChallenge() {
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

		// 2. Admin creates a fresh user for MFA tests
		String mfaEmail = "mfa-test-" + UUID.randomUUID().toString().substring(0, 8) + "@metro.org";
		String mfaPassword = "MfaPassword123!";
		webTestClient.post()
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
						    "fullName": "MFA Test User",
						    "platformSuperAdmin": false
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isCreated();

		// 3. User logs in with password (MFA not yet configured -> returns access token directly)
		byte[] initialLoginResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.mfaRequired").isEqualTo(false)
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String userToken = JsonPath.read(new String(initialLoginResponse), "$.accessToken");

		// 4. Verify initial MFA status is false
		webTestClient.get()
				.uri("/api/v1/auth/mfa/status")
				.headers(headers -> headers.setBearerAuth(userToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.enabled").isEqualTo(false);

		// 5. Initiate MFA setup
		byte[] setupResponse = webTestClient.post()
				.uri("/api/v1/auth/mfa/setup")
				.headers(headers -> headers.setBearerAuth(userToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.secret").isNotEmpty()
				.jsonPath("$.qrCodeUri").isNotEmpty()
				.jsonPath("$.backupCodes.length()").isEqualTo(8)
				.returnResult().getResponseBody();

		String secret = JsonPath.read(new String(setupResponse), "$.secret");
		List<String> backupCodes = JsonPath.read(new String(setupResponse), "$.backupCodes");
		assertThat(secret).isNotBlank();
		assertThat(backupCodes).hasSize(8);

		// 6. Activating with an invalid code should fail
		webTestClient.post()
				.uri("/api/v1/auth/mfa/activate")
				.headers(headers -> headers.setBearerAuth(userToken))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "code": "000000"
						}
						""")
				.exchange()
				.expectStatus().isUnauthorized();

		// 7. Activate MFA with valid TOTP code
		String validTotp = TotpGenerator.generateCurrentTotp(secret);
		webTestClient.post()
				.uri("/api/v1/auth/mfa/activate")
				.headers(headers -> headers.setBearerAuth(userToken))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "code": "%s"
						}
						""".formatted(validTotp))
				.exchange()
				.expectStatus().isNoContent();

		// 8. Verify MFA status is now enabled
		webTestClient.get()
				.uri("/api/v1/auth/mfa/status")
				.headers(headers -> headers.setBearerAuth(userToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.enabled").isEqualTo(true);

		// 9. Next login attempt MUST return MFA challenge instead of access token
		byte[] challengeResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.mfaRequired").isEqualTo(true)
				.jsonPath("$.mfaToken").isNotEmpty()
				.jsonPath("$.accessToken").doesNotExist()
				.returnResult().getResponseBody();

		String mfaToken = JsonPath.read(new String(challengeResponse), "$.mfaToken");

		// 10. Verification with bad code fails
		webTestClient.post()
				.uri("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "mfaToken": "%s",
						    "code": "123456"
						}
						""".formatted(mfaToken))
				.exchange()
				.expectStatus().isUnauthorized();

		// 11. Verification with valid TOTP succeeds and issues full JWT token pair
		String loginTotp = TotpGenerator.generateCurrentTotp(secret);
		byte[] verifiedTokenResponse = webTestClient.post()
				.uri("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "mfaToken": "%s",
						    "code": "%s"
						}
						""".formatted(mfaToken, loginTotp))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.mfaRequired").isEqualTo(false)
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.returnResult().getResponseBody();

		String verifiedAccessToken = JsonPath.read(new String(verifiedTokenResponse), "$.accessToken");

		// 12. Token can access protected endpoints (e.g. GET /api/v1/auth/me)
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.headers(headers -> headers.setBearerAuth(verifiedAccessToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(mfaEmail);

		// 13. Login again to test backup code authentication
		byte[] secondChallengeResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.mfaRequired").isEqualTo(true)
				.returnResult().getResponseBodyContent();

		String secondMfaToken = JsonPath.read(new String(secondChallengeResponse), "$.mfaToken");
		String firstBackupCode = backupCodes.get(0);

		// 14. Verify challenge using backup code
		byte[] backupCodeVerifiedResponse = webTestClient.post()
				.uri("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "mfaToken": "%s",
						    "code": "%s"
						}
						""".formatted(secondMfaToken, firstBackupCode))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.returnResult().getResponseBody();

		String backupAccessToken = JsonPath.read(new String(backupCodeVerifiedResponse), "$.accessToken");
		assertThat(backupAccessToken).isNotBlank();

		// 15. Attempting to reuse the exact same backup code in a subsequent challenge MUST fail (single-use)
		byte[] thirdChallengeResponse = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult().getResponseBodyContent();

		String thirdMfaToken = JsonPath.read(new String(thirdChallengeResponse), "$.mfaToken");

		webTestClient.post()
				.uri("/api/v1/auth/mfa/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "mfaToken": "%s",
						    "code": "%s"
						}
						""".formatted(thirdMfaToken, firstBackupCode))
				.exchange()
				.expectStatus().isUnauthorized();

		// 16. Disable MFA with valid TOTP code
		String disableTotp = TotpGenerator.generateCurrentTotp(secret);
		webTestClient.post()
				.uri("/api/v1/auth/mfa/disable")
				.headers(headers -> headers.setBearerAuth(backupAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "codeOrPassword": "%s"
						}
						""".formatted(disableTotp))
				.exchange()
				.expectStatus().isNoContent();

		// 17. Verify MFA status is disabled
		webTestClient.get()
				.uri("/api/v1/auth/mfa/status")
				.headers(headers -> headers.setBearerAuth(backupAccessToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.enabled").isEqualTo(false);

		// 18. User logs in normally without MFA challenge
		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s",
						    "password": "%s"
						}
						""".formatted(mfaEmail, mfaPassword))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.mfaRequired").isEqualTo(false)
				.jsonPath("$.accessToken").isNotEmpty();
	}

	@Test
	@DisplayName("Should support passwordless magic link request, POST/GET verification, replay defense, and anti-enumeration handling")
	void shouldSupportPasswordlessMagicLinkAuthentication() {
		mailService.clear();

		// 1. Request magic link for Dr. Gregory House
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s"
						}
						""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.message").isNotEmpty()
				.jsonPath("$.token").doesNotExist();

		var simulatedMail = mailService.getLatestEmailFor(PlaygroundDataSeeder.DOCTOR_EMAIL)
				.orElseThrow(() -> new AssertionError("Expected magic link email to be delivered to simulated inbox."));
		String token = simulatedMail.token();
		assertThat(token).isNotBlank();

		// 2. Verify magic link via POST /api/v1/auth/magic-link/verify
		byte[] verifyResponse = webTestClient.post()
				.uri("/api/v1/auth/magic-link/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "token": "%s"
						}
						""".formatted(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.refreshToken").isNotEmpty()
				.jsonPath("$.user.email").isEqualTo(PlaygroundDataSeeder.DOCTOR_EMAIL)
				.returnResult().getResponseBody();

		String accessToken = JsonPath.read(new String(verifyResponse), "$.accessToken");

		// 3. Confirm authenticated access using issued access token
		webTestClient.get()
				.uri("/api/v1/auth/me")
				.headers(headers -> headers.setBearerAuth(accessToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(PlaygroundDataSeeder.DOCTOR_EMAIL);

		// 4. Single-use replay protection: reusing the same magic link token MUST fail with 401 Unauthorized
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "token": "%s"
						}
						""".formatted(token))
				.exchange()
				.expectStatus().isUnauthorized();

		// 5. Request a second magic link and verify via GET endpoint (direct link click)
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "%s"
						}
						""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.message").isNotEmpty()
				.jsonPath("$.token").doesNotExist();

		var secondMail = mailService.getLatestEmailFor(PlaygroundDataSeeder.DOCTOR_EMAIL)
				.orElseThrow();
		String secondToken = secondMail.token();

		webTestClient.get()
				.uri("/api/v1/auth/magic-link/verify?token=" + secondToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.user.email").isEqualTo(PlaygroundDataSeeder.DOCTOR_EMAIL);

		// 6. Anti-enumeration: requesting magic link for non-existent user returns 200 with generic message and no email dispatched
		mailService.clear();
		webTestClient.post()
				.uri("/api/v1/auth/magic-link/request")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("""
						{
						    "email": "nonexistent@hospital.org"
						}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.message").isNotEmpty()
				.jsonPath("$.token").doesNotExist();

		assertThat(mailService.getLatestEmailFor("nonexistent@hospital.org")).isEmpty();
	}

	@Test
	@DisplayName("Should return database-backed scope hierarchy tree for anonymous visitor with neutral access status")
	void shouldReturnDynamicScopeTreeForAnonymousVisitor() {
		webTestClient.get()
				.uri("/api/v1/playground/scopes/tree?tenantId=" + PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.tenantName").isEqualTo("Metro General Hospital")
				.jsonPath("$.authenticated").isEqualTo(false)
				.jsonPath("$.tree.length()").isEqualTo(1)
				.jsonPath("$.tree[0].code").isEqualTo("METRO_HOSPITAL")
				.jsonPath("$.tree[0].colorTheme").isEqualTo("gray")
				.jsonPath("$.tree[0].accessStatus").isEqualTo("ANONYMOUS")
				.jsonPath("$.tree[0].children.length()").isEqualTo(2)
				.jsonPath("$.flatList.length()").isEqualTo(4);
	}

	@Test
	@DisplayName("Should dynamically evaluate scope access status and colors for scoped clinician Dr. House")
	void shouldEvaluateDynamicScopeAccessForScopedClinician() {
		// 1. Authenticate as Dr. Gregory House (scoped to Cardiology with inheritance)
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
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");

		// 2. Fetch scope tree with Dr. House's bearer token
		webTestClient.get()
				.uri("/api/v1/playground/scopes/tree?tenantId=" + PlaygroundDataSeeder.METRO_HOSPITAL_TENANT_ID)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.authenticated").isEqualTo(true)
				.jsonPath("$.actorEmail").isEqualTo(PlaygroundDataSeeder.DOCTOR_EMAIL)
				// Root: outside Dr. House's cardiology subtree
				.jsonPath("$.flatList[?(@.code == 'METRO_HOSPITAL')].accessible").isEqualTo(false)
				.jsonPath("$.flatList[?(@.code == 'METRO_HOSPITAL')].colorTheme").isEqualTo("red")
				.jsonPath("$.flatList[?(@.code == 'METRO_HOSPITAL')].accessStatus").isEqualTo("FORBIDDEN")
				// Cardiology: directly assigned scope
				.jsonPath("$.flatList[?(@.code == 'CARDIOLOGY')].accessible").isEqualTo(true)
				.jsonPath("$.flatList[?(@.code == 'CARDIOLOGY')].colorTheme").isEqualTo("emerald")
				.jsonPath("$.flatList[?(@.code == 'CARDIOLOGY')].accessStatus").isEqualTo("DIRECT")
				.jsonPath("$.flatList[?(@.code == 'CARDIOLOGY')].patientCount").isEqualTo(1)
				// ICU: inherited scope from Cardiology
				.jsonPath("$.flatList[?(@.code == 'ICU')].accessible").isEqualTo(true)
				.jsonPath("$.flatList[?(@.code == 'ICU')].colorTheme").isEqualTo("emerald")
				.jsonPath("$.flatList[?(@.code == 'ICU')].accessStatus").isEqualTo("INHERITED")
				.jsonPath("$.flatList[?(@.code == 'ICU')].patientCount").isEqualTo(1)
				// Pediatrics: forbidden / outside assigned branch
				.jsonPath("$.flatList[?(@.code == 'PEDIATRICS')].accessible").isEqualTo(false)
				.jsonPath("$.flatList[?(@.code == 'PEDIATRICS')].colorTheme").isEqualTo("red")
				.jsonPath("$.flatList[?(@.code == 'PEDIATRICS')].accessStatus").isEqualTo("FORBIDDEN");
	}

	@Test
	@DisplayName("Should evaluate dynamic scopes as NO_PERMISSION when user lacks PATIENT_READ in active tenant")
	void shouldEvaluateDynamicScopesAsNoPermissionWhenRoleLacksReadPrivilege() {
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
				.returnResult().getResponseBody();

		String token = JsonPath.read(new String(loginResponseBody), "$.accessToken");

		// 2. Switch tenant to St. Jude Medical Center (where Cameron has AUDITOR role without PATIENT_READ)
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
				.returnResult().getResponseBody();

		String switchedToken = JsonPath.read(new String(switchResponseBody), "$.accessToken");

		// 3. Query St. Jude scope tree: all scopes must show NO_PERMISSION and red theme
		webTestClient.get()
				.uri("/api/v1/playground/scopes/tree?tenantId=" + PlaygroundDataSeeder.ST_JUDE_TENANT_ID)
				.headers(headers -> headers.setBearerAuth(switchedToken))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.tenantName").isEqualTo("St. Jude Medical Center")
				.jsonPath("$.authenticated").isEqualTo(true)
				.jsonPath("$.tree[0].code").isEqualTo("ST_JUDE")
				.jsonPath("$.tree[0].accessible").isEqualTo(false)
				.jsonPath("$.tree[0].colorTheme").isEqualTo("red")
				.jsonPath("$.tree[0].accessStatus").isEqualTo("NO_PERMISSION");
	}

	@Test
	@DisplayName("Should expose IAM metrics and capture SIEM audit events in playground")
	void shouldExposeObservabilityMetricsAndEvents() {
		// 1. Perform login to trigger metrics and audit event
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(PlaygroundDataSeeder.DOCTOR_EMAIL, PlaygroundDataSeeder.DEMO_PASSWORD);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk();

		// 2. Verify events endpoint contains captured events
		webTestClient.get()
				.uri("/api/v1/playground/observability/events")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.totalCaptured").isNumber()
				.jsonPath("$.events").isNotEmpty();

		// 3. Verify metrics endpoint contains recorded metrics
		webTestClient.get()
				.uri("/api/v1/playground/observability/metrics")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.auth.totalAttempts").isNumber()
				.jsonPath("$.token.totalValidations").isNumber()
				.jsonPath("$.accessDenied.totalDenied").isNumber();

		// 4. Clear events
		webTestClient.delete()
				.uri("/api/v1/playground/observability/events")
				.exchange()
				.expectStatus().isOk();

		webTestClient.get()
				.uri("/api/v1/playground/observability/events")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.totalCaptured").isEqualTo(0)
				.jsonPath("$.events").isEmpty();
	}
}
