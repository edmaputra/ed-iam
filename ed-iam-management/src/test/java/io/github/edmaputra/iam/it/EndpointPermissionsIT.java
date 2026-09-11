package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying declarative endpoint-level permission enforcement (@{@link io.github.edmaputra.iam.domain.security.annotation.RequirePermission})
 * across domain entity management REST endpoints (Users, Roles, Groups, Scopes).
 *
 * @author edmaputra
 * @since 0.1.0
 */
class EndpointPermissionsIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should reject unauthenticated requests to protected endpoints with 401 Unauthorized")
	void shouldRejectUnauthenticatedRequestsWith401() {
		String userJson = """
				{
				    "email": "unauth-%s@corp.com",
				    "password": "Password123!",
				    "fullName": "Unauthenticated User"
				}
				""".formatted(UUID.randomUUID());

		// POST /api/v1/users without Authorization header
		webTestClient.post()
				.uri("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(userJson)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(401);

		// GET /api/v1/roles without Authorization header
		webTestClient.get()
				.uri("/api/v1/roles?tenantId=" + UUID.randomUUID())
				.exchange()
				.expectStatus().isUnauthorized();

		// GET /api/v1/groups without Authorization header
		webTestClient.get()
				.uri("/api/v1/groups?tenantId=" + UUID.randomUUID())
				.exchange()
				.expectStatus().isUnauthorized();

		// GET /api/v1/scopes without Authorization header
		webTestClient.get()
				.uri("/api/v1/scopes?tenantId=" + UUID.randomUUID())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should reject authenticated requests lacking the required permission with 403 Forbidden")
	void shouldRejectUnauthorizedRequestsWith403() {
		String tokenWithWrongPermission = createActorToken(
				"limited-user@corp.com",
				UUID.randomUUID(),
				Set.of("iam:audit:read")
		);

		String userJson = """
				{
				    "email": "forbidden-%s@corp.com",
				    "password": "Password123!",
				    "fullName": "Forbidden User"
				}
				""".formatted(UUID.randomUUID());

		webTestClient.post()
				.uri("/api/v1/users")
				.header("Authorization", "Bearer " + tokenWithWrongPermission)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(userJson)
				.exchange()
				.expectStatus().isForbidden()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.detail").value(detail ->
						assertThat(detail.toString()).contains("iam:user:create"));
	}

	@Test
	@DisplayName("Should allow access when actor has the specific required permission")
	void shouldAllowRequestWhenActorHasSpecificPermission() {
		UUID tenantId = UUID.randomUUID();
		String tokenWithUserCreate = createActorToken(
				"user-admin@corp.com",
				tenantId,
				Set.of("iam:user:create", "iam:user:read")
		);

		String userEmail = "granted-" + UUID.randomUUID() + "@corp.com";
		String userJson = """
				{
				    "email": "%s",
				    "password": "Password123!",
				    "fullName": "Granted User"
				}
				""".formatted(userEmail);

		// POST /api/v1/users should succeed with 201
		byte[] createdBytes = webTestClient.post()
				.uri("/api/v1/users")
				.header("Authorization", "Bearer " + tokenWithUserCreate)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(userJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.email").isEqualTo(userEmail)
				.returnResult()
				.getResponseBody();

		assertThat(createdBytes).isNotNull();
		String userId = com.jayway.jsonpath.JsonPath.read(new String(createdBytes), "$.id");

		// GET /api/v1/users/{id} should succeed with 200
		webTestClient.get()
				.uri("/api/v1/users/" + userId)
				.header("Authorization", "Bearer " + tokenWithUserCreate)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.email").isEqualTo(userEmail);
	}

	@Test
	@DisplayName("Should allow read permission but deny write permission for read-only actor")
	void shouldAllowReadAndDenyWriteForReadOnlyActor() {
		UUID tenantId = UUID.randomUUID();
		String readOnlyToken = createActorToken(
				"readonly-role-admin@corp.com",
				tenantId,
				Set.of("iam:role:read")
		);

		// GET /api/v1/roles should succeed with 200
		webTestClient.get()
				.uri("/api/v1/roles?tenantId=" + tenantId)
				.header("Authorization", "Bearer " + readOnlyToken)
				.exchange()
				.expectStatus().isOk();

		// POST /api/v1/roles should be rejected with 403
		String roleJson = """
				{
				    "tenantId": "%s",
				    "code": "AUDITOR_%s",
				    "name": "Auditor",
				    "description": "Auditor Role",
				    "permissions": ["AUDIT_READ"]
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		webTestClient.post()
				.uri("/api/v1/roles")
				.header("Authorization", "Bearer " + readOnlyToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJson)
				.exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.detail").value(detail ->
						assertThat(detail.toString()).contains("iam:role:create"));
	}

	@Test
	@DisplayName("Should allow platform superadmin full access to all domain management endpoints unconditionally")
	void shouldAllowPlatformSuperadminUnconditionally() {
		String superAdminToken = createSuperAdminToken();
		UUID tenantId = UUID.randomUUID();

		// 1. Create User
		String userJson = """
				{
				    "email": "superadmin-created-%s@corp.com",
				    "password": "Password123!",
				    "fullName": "SuperAdmin Created"
				}
				""".formatted(UUID.randomUUID());

		webTestClient.post()
				.uri("/api/v1/users")
				.header("Authorization", "Bearer " + superAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(userJson)
				.exchange()
				.expectStatus().isCreated();

		// 2. Create Role
		String roleJson = """
				{
				    "tenantId": "%s",
				    "code": "SUPER_%s",
				    "name": "Super Role",
				    "description": "Super Role Desc",
				    "permissions": ["TEST_PERM"]
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		webTestClient.post()
				.uri("/api/v1/roles")
				.header("Authorization", "Bearer " + superAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(roleJson)
				.exchange()
				.expectStatus().isCreated();

		// 3. Create Group
		String groupJson = """
				{
				    "tenantId": "%s",
				    "code": "GROUP_%s",
				    "name": "Super Group",
				    "description": "Super Group Desc"
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		webTestClient.post()
				.uri("/api/v1/groups")
				.header("Authorization", "Bearer " + superAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(groupJson)
				.exchange()
				.expectStatus().isCreated();

		// 4. Create Scope
		String scopeJson = """
				{
				    "tenantId": "%s",
				    "code": "SCOPE_%s",
				    "name": "Super Scope"
				}
				""".formatted(tenantId, UUID.randomUUID().toString().substring(0, 6));

		webTestClient.post()
				.uri("/api/v1/scopes")
				.header("Authorization", "Bearer " + superAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(scopeJson)
				.exchange()
				.expectStatus().isCreated();
	}
}
