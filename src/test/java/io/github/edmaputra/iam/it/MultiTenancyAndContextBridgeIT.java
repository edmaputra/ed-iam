package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test verifying multi-tenant isolation and host {@link io.github.edmaputra.iam.domain.tenancy.TenantContextBridge}
 * context propagation through the request filter chain.
 *
 * @author edmaputra
 */
class MultiTenancyAndContextBridgeIT extends AbstractIntegrationTest {

	@Test
	@DisplayName("Should propagate tenant context to host application via TenantContextBridge")
	void shouldPropagateTenantContextViaHostTenantContextBridge() throws Exception {
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

		// Login to obtain JWT token with tenant claims
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, rawPassword, tenantUuid);

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson))
				.andExpect(status().isOk())
				.andReturn();

		String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

		// Call /api/test/tenant which reads TestTenantContextHolder.getTenantId()
		mockMvc.perform(get("/api/test/tenant")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.tenantId").value(tenantUuid.toString()));
	}

	@Test
	@DisplayName("Should isolate permissions between distinct tenants")
	void shouldIsolatePermissionsBetweenTenants() throws Exception {
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

		MvcResult resultA = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJsonA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.roles[0]").value("ROLE_A"))
				.andReturn();

		String accessTokenA = JsonPath.read(resultA.getResponse().getContentAsString(), "$.accessToken");

		// Fetch /api/v1/auth/me for Tenant A
		mockMvc.perform(get("/api/v1/auth/me")
						.header("Authorization", "Bearer " + accessTokenA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("ROLE_A"))
				.andExpect(jsonPath("$.permissions[0]").value("ACTION_A"));
	}
}
