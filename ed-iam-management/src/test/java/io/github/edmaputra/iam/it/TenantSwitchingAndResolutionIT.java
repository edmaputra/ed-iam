package io.github.edmaputra.iam.it;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration test suite verifying tenant context auto-resolution,
 * explicit tenant parameter handling (request header vs. request body precedence),
 * and post-login tenant context switching via {@code POST /api/v1/auth/switch-tenant}
 * using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class TenantSwitchingAndResolutionIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should auto-resolve tenant on login when user belongs to a single tenant without header or body tenantId")
	void shouldAutoResolveTenantWhenNoTenantIdProvidedForSingleTenantUser() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		String email = "single-tenant-" + UUID.randomUUID() + "@autoresolve.com";
		String password = "AutoPassword123!";

		User user = User.create(email, passwordEncoder.encode(password), "Single Tenant User", false);
		userRepository.save(user);

		Role role = Role.createCustom(tenantId, "OPERATOR", "Operator", "Operator role", Set.of("OP_EXECUTE"));
		roleRepository.save(role);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId));

		// Login with ONLY email and password - NO tenantId in header or body
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		String expectedLoginJson = """
				{
				    "tokenType": "Bearer",
				    "user": {
				        "email": "%s",
				        "tenantId": "%s",
				        "availableTenantIds": ["%s"],
				        "roles": ["OPERATOR"],
				        "permissions": ["OP_EXECUTE"]
				    }
				}
				""".formatted(email, tenantUuid, tenantUuid);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedLoginJson, JsonCompareMode.LENIENT)
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
	}

	@Test
	@DisplayName("Should provide availableTenants list when multi-tenant user logs in without tenantId")
	void shouldProvideAvailableTenantsWhenNoTenantIdProvidedForMultiTenantUser() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "multi-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "Multi User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "DOCTOR", "Doctor", "Desc", Set.of("CHART_VIEW"));
		Role roleB = Role.createCustom(tenantIdB, "NURSE", "Nurse", "Desc", Set.of("VITALS_VIEW"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		String expectedLoginJson = """
				{
				    "user": {
				        "email": "%s",
				        "tenantId": null,
				        "availableTenantIds": ["%s", "%s"]
				    }
				}
				""".formatted(email, tenantA, tenantB);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedLoginJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should scope login to tenantId provided in request body")
	void shouldLoginWithTenantIdInRequestBody() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "body-login-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "ADMIN_A", "Admin A", "Desc", Set.of("CONFIG_A"));
		Role roleB = Role.createCustom(tenantIdB, "ADMIN_B", "Admin B", "Desc", Set.of("CONFIG_B"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		String loginBody = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, password, tenantA);

		String expectedJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["ADMIN_A"],
				        "permissions": ["CONFIG_A"]
				    }
				}
				""".formatted(tenantA);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should scope login to tenantId provided in X-Tenant-ID header")
	void shouldLoginWithTenantIdInHeader() {
		UUID tenantA = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);

		String email = "header-login-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "MANAGER", "Manager", "Desc", Set.of("REPORT_VIEW"));
		roleRepository.save(roleA);
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));

		String loginBody = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		String expectedJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["MANAGER"],
				        "permissions": ["REPORT_VIEW"]
				    }
				}
				""".formatted(tenantA);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.header("X-Tenant-ID", tenantA.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should prioritize X-Tenant-ID header over request body tenantId on login")
	void shouldPrioritizeHeaderTenantIdOverBodyOnLogin() {
		UUID tenantHeader = UUID.randomUUID();
		UUID tenantBody = UUID.randomUUID();
		TenantId tenantIdHeader = new TenantId(tenantHeader);
		TenantId tenantIdBody = new TenantId(tenantBody);

		String email = "precedence-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleHeader = Role.createCustom(tenantIdHeader, "ROLE_HEADER", "Role Header", "Desc", Set.of("PERM_HEADER"));
		Role roleBody = Role.createCustom(tenantIdBody, "ROLE_BODY", "Role Body", "Desc", Set.of("PERM_BODY"));
		roleRepository.save(roleHeader);
		roleRepository.save(roleBody);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleHeader.getId(), tenantIdHeader));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleBody.getId(), tenantIdBody));

		// Supply tenantBody in JSON body, BUT tenantHeader in X-Tenant-ID header
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, password, tenantBody);

		String expectedJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["ROLE_HEADER"],
				        "permissions": ["PERM_HEADER"]
				    }
				}
				""".formatted(tenantHeader);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.header("X-Tenant-ID", tenantHeader.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should switch tenant context using X-Tenant-ID header")
	void shouldSwitchTenantUsingHeader() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "switch-hdr-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "ROLE_A", "Role A", "Desc", Set.of("PERM_A"));
		Role roleB = Role.createCustom(tenantIdB, "ROLE_B", "Role B", "Desc", Set.of("PERM_B"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		String initialToken = loginAndGetToken(email, password);

		String expectedSwitchJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["ROLE_B"],
				        "permissions": ["PERM_B"]
				    }
				}
				""".formatted(tenantB);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", tenantB.toString())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedSwitchJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should switch tenant context using request body")
	void shouldSwitchTenantUsingRequestBody() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "switch-body-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "ROLE_A", "Role A", "Desc", Set.of("PERM_A"));
		Role roleB = Role.createCustom(tenantIdB, "ROLE_B", "Role B", "Desc", Set.of("PERM_B"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		String initialToken = loginAndGetToken(email, password);

		String switchBody = """
				{
				    "tenantId": "%s"
				}
				""".formatted(tenantA);

		String expectedSwitchJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["ROLE_A"],
				        "permissions": ["PERM_A"]
				    }
				}
				""".formatted(tenantA);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(switchBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedSwitchJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should prioritize X-Tenant-ID header over request body on switch-tenant")
	void shouldPrioritizeHeaderOverBodyOnSwitchTenant() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "switch-prec-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "ROLE_A", "Role A", "Desc", Set.of("PERM_A"));
		Role roleB = Role.createCustom(tenantIdB, "ROLE_B", "Role B", "Desc", Set.of("PERM_B"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		String initialToken = loginAndGetToken(email, password);

		// Header is tenantA, Body is tenantB -> Header should win
		String switchBody = """
				{
				    "tenantId": "%s"
				}
				""".formatted(tenantB);

		String expectedSwitchJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["ROLE_A"],
				        "permissions": ["PERM_A"]
				    }
				}
				""".formatted(tenantA);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", tenantA.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(switchBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedSwitchJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should reject switch-tenant with 400 Bad Request when tenantId is not provided in header or body")
	void shouldRejectSwitchTenantWithoutTenantIdWith400() {
		String email = "switch-missing-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		String initialToken = loginAndGetToken(email, password);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("Target tenant ID must be provided"));
	}

	@Test
	@DisplayName("Should reject switch-tenant with 403 Forbidden when user does not have access to target tenant")
	void shouldRejectSwitchTenantToUnauthorizedTenantWith403() {
		UUID unauthorizedTenant = UUID.randomUUID();
		String email = "switch-unauth-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		String initialToken = loginAndGetToken(email, password);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", unauthorizedTenant.toString())
				.exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("User does not have access to tenant: " + unauthorizedTenant));
	}

	@Test
	@DisplayName("Should reject switch-tenant with 400 Bad Request when X-Tenant-ID header has invalid UUID format")
	void shouldRejectSwitchTenantWithInvalidUuidInHeaderWith400() {
		String email = "switch-baduuid-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "User", false);
		userRepository.save(user);

		String initialToken = loginAndGetToken(email, password);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", "not-a-valid-uuid")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("Invalid UUID string for X-Tenant-ID header"));
	}

	@Test
	@DisplayName("Should reject login with 400 Bad Request when X-Tenant-ID header has invalid UUID format")
	void shouldRejectLoginWithInvalidUuidInHeaderWith400() {
		String loginJson = """
				{
				    "email": "any@example.com",
				    "password": "Password123!"
				}
				""";

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.header("X-Tenant-ID", "malformed-tenant-uuid")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("Invalid UUID string for X-Tenant-ID header"));
	}

	@Test
	@DisplayName("Should allow platform superadmin to switch to any tenant context")
	void shouldAllowPlatformSuperAdminToSwitchToAnyTenant() {
		UUID anyTenant = UUID.randomUUID();
		String email = "superadmin-switch-" + UUID.randomUUID() + "@platform.org";
		String password = "MasterPassword123!";

		User superAdmin = User.create(email, passwordEncoder.encode(password), "Root Admin", true);
		userRepository.save(superAdmin);

		String superAdminToken = loginAndGetToken(email, password);

		String switchBody = """
				{
				    "tenantId": "%s"
				}
				""".formatted(anyTenant);

		String expectedSwitchJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "platformSuperAdmin": true,
				        "tenantWide": true,
				        "roles": ["PLATFORM_SUPERADMIN"]
				    }
				}
				""".formatted(anyTenant);

		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + superAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(switchBody)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedSwitchJson, JsonCompareMode.LENIENT);
	}

	private String loginAndGetToken(String email, String password) {
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		byte[] response = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		assertThat(response).isNotNull();
		return JsonPath.read(new String(response, StandardCharsets.UTF_8), "$.accessToken");
	}
}
