package io.github.edmaputra.iam.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureMockMvc
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

	@Autowired
	protected MockMvc mockMvc;

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
}
