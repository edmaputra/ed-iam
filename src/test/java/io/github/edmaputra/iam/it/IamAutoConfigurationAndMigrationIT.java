package io.github.edmaputra.iam.it;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.edmaputra.iam.adapter.rest.AuthController;
import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that the starter auto-configurations activate correctly
 * and Liquibase executes schema setup for all {@code iam_*} tables.
 *
 * @author edmaputra
 */
class IamAutoConfigurationAndMigrationIT extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should auto-configure all IAM beans into the Spring ApplicationContext")
	void shouldAutoConfigureAllIamBeans() {
		assertThat(context.getBean(AuthenticateUserUseCase.class)).isNotNull();
		assertThat(context.getBean(ManageScopeUseCase.class)).isNotNull();
		assertThat(context.getBean(AuthenticationProviderRouter.class)).isNotNull();
		assertThat(context.getBean(JwtTokenProvider.class)).isNotNull();
		assertThat(context.getBean(CurrentActorProvider.class)).isNotNull();
		assertThat(context.getBean(JwtAuthenticationFilter.class)).isNotNull();
		assertThat(context.getBean(AuthController.class)).isNotNull();

		assertThat(userRepository).isNotNull();
		assertThat(roleRepository).isNotNull();
		assertThat(groupRepository).isNotNull();
		assertThat(scopeNodeRepository).isNotNull();
		assertThat(userRoleAssignmentRepository).isNotNull();
		assertThat(groupRoleAssignmentRepository).isNotNull();
		assertThat(userGroupMembershipRepository).isNotNull();
		assertThat(userIdentityRepository).isNotNull();
		assertThat(passwordEncoder).isNotNull();
	}

	@Test
	@DisplayName("Should execute Liquibase changelog and create all iam_* database tables")
	void shouldExecuteLiquibaseAndCreateAllIamTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'iam_%'",
				String.class);

		assertThat(tables).contains(
				"iam_user",
				"iam_role",
				"iam_group",
				"iam_scope_node",
				"iam_user_role_assignment",
				"iam_group_role_assignment",
				"iam_user_group_membership",
				"iam_user_identity");
	}
}
