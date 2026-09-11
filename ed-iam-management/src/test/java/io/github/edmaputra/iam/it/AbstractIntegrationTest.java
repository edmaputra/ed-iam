package io.github.edmaputra.iam.it;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.it.app.TestIamApplication;

/**
 * Base integration test class bootstrapping a shared PostgreSQL Testcontainer
 * and the complete {@link TestIamApplication} host environment.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@SpringBootTest(classes = TestIamApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("ed_iam_test")
			.withUsername("test")
			.withPassword("test");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
	}

	@LocalServerPort
	protected int port;

	protected WebTestClient webTestClient;

	@BeforeEach
	void setUpWebTestClient() {
		this.webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.responseTimeout(Duration.ofSeconds(10))
				.build();
	}

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected RoleRepository roleRepository;

	@Autowired
	protected GroupRepository groupRepository;

	@Autowired
	protected ScopeNodeRepository scopeNodeRepository;

	@Autowired
	protected UserRoleAssignmentRepository userRoleAssignmentRepository;

	@Autowired
	protected GroupRoleAssignmentRepository groupRoleAssignmentRepository;

	@Autowired
	protected UserGroupMembershipRepository userGroupMembershipRepository;

	@Autowired
	protected UserIdentityRepository userIdentityRepository;

	@Autowired
	protected PasswordEncoderPort passwordEncoder;

	@Autowired
	protected io.github.edmaputra.iam.application.port.out.TokenProviderPort tokenProvider;

	/**
	 * Creates a signed JWT access token for a platform superadmin actor.
	 *
	 * @return access token string
	 */
	protected String createSuperAdminToken() {
		io.github.edmaputra.iam.application.model.EffectiveAccess access =
				new io.github.edmaputra.iam.application.model.EffectiveAccess(
						io.github.edmaputra.iam.domain.model.UserId.generate(),
						"superadmin-it@platform.org",
						null,
						true,
						true,
						java.util.Set.of(),
						java.util.Set.of("PLATFORM_SUPERADMIN"),
						java.util.Set.of("*"),
						java.util.Set.of(),
						java.util.Set.of("/")
				);
		return tokenProvider.createAccessToken(access);
	}

	/**
	 * Creates a signed JWT access token for an actor with custom permissions and tenant scope.
	 *
	 * @param email       the user email
	 * @param tenantId    the tenant ID (may be null)
	 * @param permissions the permissions granted
	 * @return access token string
	 */
	protected String createActorToken(String email, java.util.UUID tenantId, java.util.Set<String> permissions) {
		io.github.edmaputra.iam.domain.tenancy.TenantId tid = tenantId != null ? new io.github.edmaputra.iam.domain.tenancy.TenantId(tenantId) : null;
		io.github.edmaputra.iam.application.model.EffectiveAccess access =
				new io.github.edmaputra.iam.application.model.EffectiveAccess(
						io.github.edmaputra.iam.domain.model.UserId.generate(),
						email,
						tid,
						false,
						false,
						java.util.Set.of(),
						java.util.Set.of("USER_ROLE"),
						permissions,
						java.util.Set.of(),
						java.util.Set.of()
				);
		return tokenProvider.createAccessToken(access);
	}
}
