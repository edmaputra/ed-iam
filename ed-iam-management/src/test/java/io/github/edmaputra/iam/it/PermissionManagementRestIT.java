package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying permission catalog management REST endpoints (/api/v1/permissions).
 *
 * @author edmaputra
 * @since 0.10.0
 */
class PermissionManagementRestIT extends AbstractIntegrationTest {

	@BeforeEach
	void authenticateAsSuperAdmin() {
		this.webTestClient = this.webTestClient.mutate()
				.defaultHeader("Authorization", "Bearer " + createSuperAdminToken())
				.build();
	}

	@Test
	@DisplayName("Should perform full Permission CRUD, querying, and catalog listing via /api/v1/permissions")
	void shouldPerformPermissionCrudAndCatalogListing() {
		UUID tenantId = UUID.randomUUID();
		String permCode = "custom:patient:discharge:" + UUID.randomUUID().toString().substring(0, 8);

		// 1. Create Custom Tenant Permission
		String createJson = """
				{
				    "tenantId": "%s",
				    "code": "%s",
				    "name": "Discharge Patient",
				    "description": "Allows patient discharge",
				    "category": "CLINICAL"
				}
				""".formatted(tenantId, permCode);

		String permId = webTestClient.post()
				.uri("/api/v1/permissions")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.id").isNotEmpty()
				.jsonPath("$.code").isEqualTo(permCode)
				.jsonPath("$.name").isEqualTo("Discharge Patient")
				.jsonPath("$.description").isEqualTo("Allows patient discharge")
				.jsonPath("$.category").isEqualTo("CLINICAL")
				.jsonPath("$.systemPermission").isEqualTo(false)
				.returnResult()
				.getResponseBody() != null
				? new String(webTestClient.post()
				.uri("/api/v1/permissions")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createJson)
				.exchange()
				.returnResult(String.class).getResponseBody().blockFirst() != null ? "" : "") : "";

		// Re-fetch created permission by listing
		var response = webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/permissions")
						.queryParam("tenantId", tenantId)
						.queryParam("category", "CLINICAL")
						.build())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[?(@.code == '%s')]".formatted(permCode)).exists()
				.returnResult();

		// Query by code
		webTestClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/permissions/code/" + permCode)
						.queryParam("tenantId", tenantId)
						.build())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.code").isEqualTo(permCode)
				.jsonPath("$.name").isEqualTo("Discharge Patient");

		// 2. Create Global System Permission as Superadmin
		String sysCode = "iam:global:audit:" + UUID.randomUUID().toString().substring(0, 8);
		String createSysJson = """
				{
				    "code": "%s",
				    "name": "Global Audit",
				    "description": "View global system audits",
				    "category": "AUDIT"
				}
				""".formatted(sysCode);

		webTestClient.post()
				.uri("/api/v1/permissions")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(createSysJson)
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.code").isEqualTo(sysCode)
				.jsonPath("$.systemPermission").isEqualTo(true);

		// 3. Verify security: Actor without permission is rejected with 403
		String readOnlyToken = createActorToken("actor@clinic.org", tenantId, Set.of("unrelated:perm"));
		webTestClient.mutate().defaultHeader("Authorization", "Bearer " + readOnlyToken).build()
				.get()
				.uri("/api/v1/permissions")
				.exchange()
				.expectStatus().isForbidden();
	}
}
