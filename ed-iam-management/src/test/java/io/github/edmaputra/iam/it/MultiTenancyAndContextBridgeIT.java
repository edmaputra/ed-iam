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
 * Integration test verifying multi-tenant isolation and host {@link io.github.edmaputra.iam.domain.tenancy.TenantContextBridge}
 * context propagation through the request filter chain using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class MultiTenancyAndContextBridgeIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should propagate tenant context to host application via TenantContextBridge")
	void shouldPropagateTenantContextViaHostTenantContextBridge() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		String email = "tenant-user-" + UUID.randomUUID() + "@tenant.com";
		String rawPassword = "Password123!";

		User user = User.create(email, passwordEncoder.encode(rawPassword), "Tenant User", false);
		userRepository.save(user);

		Role role = Role.createCustom(tenantId, "TENANT_ADMIN", "Admin", "Admin", Set.of("TENANT_MANAGE"));
		roleRepository.save(role);

		UserRoleAssignment assignment = UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId);
		userRoleAssignmentRepository.save(assignment);

		// Login to obtain JWT token with tenant claims via X-Tenant-ID header
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, rawPassword);

		byte[] loginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.header("X-Tenant-ID", tenantUuid.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		assertThat(loginBytes).isNotNull();
		String accessToken = JsonPath.read(new String(loginBytes, StandardCharsets.UTF_8), "$.accessToken");

		// Call /api/test/tenant which reads TestTenantContextHolder.getTenantId()
		String expectedTenantJson = """
				{
				    "active": true,
				    "tenantId": "%s"
				}
				""".formatted(tenantUuid);

		webTestClient.get()
				.uri("/api/test/tenant")
				.header("Authorization", "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedTenantJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should isolate permissions between distinct tenants")
	void shouldIsolatePermissionsBetweenTenants() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();

		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "shared-user-" + UUID.randomUUID() + "@corp.com";
		String password = "Password123!";

		User user = User.create(email, passwordEncoder.encode(password), "Shared User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "ROLE_A", "Role A", "Desc", Set.of("ACTION_A"));
		roleRepository.save(roleA);

		Role roleB = Role.createCustom(tenantIdB, "ROLE_B", "Role B", "Desc", Set.of("ACTION_B"));
		roleRepository.save(roleB);

		// Assign user different roles in each tenant
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		// Login to Tenant A
		String loginJsonA = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, password, tenantA);

		byte[] resultBytesA = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJsonA)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.user.roles[0]").isEqualTo("ROLE_A")
				.returnResult()
				.getResponseBody();

		assertThat(resultBytesA).isNotNull();
		String accessTokenA = JsonPath.read(new String(resultBytesA, StandardCharsets.UTF_8), "$.accessToken");

		// Fetch /api/v1/auth/me for Tenant A
		String expectedProfileJson = """
				{
				    "roles": ["ROLE_A"],
				    "permissions": ["ACTION_A"]
				}
				""";

		webTestClient.get()
				.uri("/api/v1/auth/me")
				.header("Authorization", "Bearer " + accessTokenA)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedProfileJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should automatically resolve tenant context on login for single-tenant users")
	void shouldAutoResolveTenantWhenUserBelongsToSingleTenant() {
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
		String accessToken = JsonPath.read(new String(loginBytes, StandardCharsets.UTF_8), "$.accessToken");

		// Verify host TenantContextBridge automatically receives the auto-resolved tenant
		String expectedTenantJson = """
				{
				    "active": true,
				    "tenantId": "%s"
				}
				""".formatted(tenantUuid);

		webTestClient.get()
				.uri("/api/test/tenant")
				.header("Authorization", "Bearer " + accessToken)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedTenantJson, JsonCompareMode.LENIENT);
	}

	@Test
	@DisplayName("Should list available tenants on login and allow switching tenant context via POST /api/v1/auth/switch-tenant")
	void shouldSupportMultiTenantListingAndSwitching() {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		UUID unauthorizedTenant = UUID.randomUUID();

		TenantId tenantIdA = new TenantId(tenantA);
		TenantId tenantIdB = new TenantId(tenantB);

		String email = "multi-tenant-" + UUID.randomUUID() + "@enterprise.com";
		String password = "MultiPassword123!";

		User user = User.create(email, passwordEncoder.encode(password), "Multi Tenant User", false);
		userRepository.save(user);

		Role roleA = Role.createCustom(tenantIdA, "PHYSICIAN", "Physician", "Physician role", Set.of("CHART_WRITE"));
		roleRepository.save(roleA);

		Role roleB = Role.createCustom(tenantIdB, "RESEARCHER", "Researcher", "Researcher role", Set.of("DATA_ANALYZE"));
		roleRepository.save(roleB);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleA.getId(), tenantIdA));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), roleB.getId(), tenantIdB));

		// 1. Initial Login with ONLY email and password (multi-tenant user)
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(email, password);

		String expectedInitialLoginJson = """
				{
				    "user": {
				        "email": "%s",
				        "tenantId": null,
				        "availableTenantIds": ["%s", "%s"]
				    }
				}
				""".formatted(email, tenantA, tenantB);

		byte[] initialLoginBytes = webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedInitialLoginJson, JsonCompareMode.LENIENT)
				.returnResult()
				.getResponseBody();

		assertThat(initialLoginBytes).isNotNull();
		String initialToken = JsonPath.read(new String(initialLoginBytes, StandardCharsets.UTF_8), "$.accessToken");

		// 2. Switch to Tenant A using X-Tenant-ID header
		String expectedTenantAJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["PHYSICIAN"],
				        "permissions": ["CHART_WRITE"]
				    }
				}
				""".formatted(tenantA);

		byte[] switchABytes = webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", tenantA.toString())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedTenantAJson, JsonCompareMode.LENIENT)
				.returnResult()
				.getResponseBody();

		assertThat(switchABytes).isNotNull();
		String tokenA = JsonPath.read(new String(switchABytes, StandardCharsets.UTF_8), "$.accessToken");

		// Verify host context is bridged to Tenant A
		webTestClient.get()
				.uri("/api/test/tenant")
				.header("Authorization", "Bearer " + tokenA)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json("""
						{
						    "active": true,
						    "tenantId": "%s"
						}
						""".formatted(tenantA), JsonCompareMode.LENIENT);

		// 3. Switch to Tenant B using request body
		String switchBodyB = """
				{
				    "tenantId": "%s"
				}
				""".formatted(tenantB);

		String expectedTenantBJson = """
				{
				    "user": {
				        "tenantId": "%s",
				        "roles": ["RESEARCHER"],
				        "permissions": ["DATA_ANALYZE"]
				    }
				}
				""".formatted(tenantB);

		byte[] switchBBytes = webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(switchBodyB)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json(expectedTenantBJson, JsonCompareMode.LENIENT)
				.returnResult()
				.getResponseBody();

		assertThat(switchBBytes).isNotNull();
		String tokenB = JsonPath.read(new String(switchBBytes, StandardCharsets.UTF_8), "$.accessToken");

		// Verify host context is bridged to Tenant B
		webTestClient.get()
				.uri("/api/test/tenant")
				.header("Authorization", "Bearer " + tokenB)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.json("""
						{
						    "active": true,
						    "tenantId": "%s"
						}
						""".formatted(tenantB), JsonCompareMode.LENIENT);

		// 4. Reject switching to unauthorized tenant with 403 Forbidden
		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.header("X-Tenant-ID", unauthorizedTenant.toString())
				.exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.json("""
						{
						    "status": 403,
						    "detail": "User does not have access to tenant: %s"
						}
						""".formatted(unauthorizedTenant), JsonCompareMode.LENIENT);

		// 5. Reject switching without tenant ID with 400 Bad Request
		webTestClient.post()
				.uri("/api/v1/auth/switch-tenant")
				.header("Authorization", "Bearer " + initialToken)
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.json("""
						{
						    "status": 400,
						    "detail": "Target tenant ID must be provided via X-Tenant-ID header or request body."
						}
						""", JsonCompareMode.LENIENT);
	}
}
