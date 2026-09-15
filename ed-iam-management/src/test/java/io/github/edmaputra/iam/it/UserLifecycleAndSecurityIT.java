package io.github.edmaputra.iam.it;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Integration test covering user lifecycle states (suspended, deactivated),
 * external account login protection, email case-insensitivity, and platform superadmin permissions
 * using {@link org.springframework.test.web.reactive.server.WebTestClient}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class UserLifecycleAndSecurityIT extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoderPort passwordEncoder;

	@Autowired
	private ManageScopeUseCase manageScopeUseCase;

	@Test
	@DisplayName("Should reject password login for suspended and deactivated accounts with 401")
	void shouldRejectLoginForSuspendedAndDeactivatedUsers() {
		String password = "SecretPassword123!";
		String suspendedEmail = "suspended-" + UUID.randomUUID() + "@clinic.org";
		User suspendedUser = User.create(suspendedEmail, passwordEncoder.encode(password), "Suspended User", false);
		suspendedUser.suspend();
		userRepository.save(suspendedUser);

		// Suspended login attempt
		String suspendedLoginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(suspendedEmail, password);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(suspendedLoginJson)
				.exchange()
				.expectStatus().isUnauthorized();

		// Deactivated login attempt
		String deactivatedEmail = "deactivated-" + UUID.randomUUID() + "@clinic.org";
		User deactivatedUser = User.create(deactivatedEmail, passwordEncoder.encode(password), "Deactivated User", false);
		deactivatedUser.deactivate();
		userRepository.save(deactivatedUser);

		String deactivatedLoginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(deactivatedEmail, password);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(deactivatedLoginJson)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should reject password login attempt for SSO-only external user accounts")
	void shouldRejectPasswordLoginForExternalAccounts() {
		String email = "sso-user-" + UUID.randomUUID() + "@enterprise.org";
		User externalUser = User.createExternal(email, "SSO User", false);
		userRepository.save(externalUser);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "AnyRandomPassword123!"
				}
				""".formatted(email);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	@DisplayName("Should authenticate user regardless of email letter casing")
	void shouldAuthenticateCaseInsensitiveEmail() {
		String mixedCaseEmail = "Doctor.Strange-" + UUID.randomUUID() + "@Hospital.Org";
		String password = "TimeStonePassword123!";
		User user = User.create(mixedCaseEmail, passwordEncoder.encode(password), "Stephen Strange", false);
		userRepository.save(user);

		// Login with lowercase email
		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s"
				}
				""".formatted(mixedCaseEmail.toLowerCase(), password);

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.accessToken").isNotEmpty()
				.jsonPath("$.user.email").isEqualTo(mixedCaseEmail.toLowerCase());
	}

	@Test
	@DisplayName("Should grant global superadmin wildcard access when logging in without tenant context")
	void shouldResolveGlobalSuperAdminAccess() {
		String email = "superadmin-" + UUID.randomUUID() + "@platform.org";
		String password = "AdminMasterPassword!";
		User superAdmin = User.create(email, passwordEncoder.encode(password), "Root Admin", true);
		userRepository.save(superAdmin);

		// Global login (tenantId is null)
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
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.user.platformSuperAdmin").isEqualTo(true)
				.jsonPath("$.user.tenantWide").isEqualTo(true)
				.jsonPath("$.user.roles[0]").isEqualTo("PLATFORM_SUPERADMIN")
				.jsonPath("$.user.permissions[0]").isEqualTo("*")
				.jsonPath("$.user.accessibleScopePaths[0]").isEqualTo("/");
	}

	@Test
	@DisplayName("Should automatically include all tenant scope nodes when superadmin logs into specific tenant")
	void shouldResolveTenantWideAccessForSuperAdminInTenant() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// Create two scope nodes in this tenant
		manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "DEPT_A_" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(), "Department A"),
				context);
		manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "DEPT_B_" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(), "Department B"),
				context);

		String email = "tenant-superadmin-" + UUID.randomUUID() + "@platform.org";
		String password = "AdminPassword123!";
		User superAdmin = User.create(email, passwordEncoder.encode(password), "Tenant SuperAdmin", true);
		userRepository.save(superAdmin);

		String loginJson = """
				{
				    "email": "%s",
				    "password": "%s",
				    "tenantId": "%s"
				}
				""".formatted(email, password, tenantId.value());

		webTestClient.post()
				.uri("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(loginJson)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.user.platformSuperAdmin").isEqualTo(true)
				.jsonPath("$.user.tenantWide").isEqualTo(true)
				.jsonPath("$.user.accessibleScopePaths").isArray()
				.jsonPath("$.user.accessibleScopeNodeIds").isArray();
	}
}
