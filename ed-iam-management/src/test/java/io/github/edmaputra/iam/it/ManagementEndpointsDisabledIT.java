package io.github.edmaputra.iam.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import io.github.edmaputra.iam.adapter.rest.GroupController;
import io.github.edmaputra.iam.adapter.rest.RoleController;
import io.github.edmaputra.iam.adapter.rest.ScopeController;
import io.github.edmaputra.iam.adapter.rest.UserController;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that management REST endpoints can be disabled via configuration property
 * {@code iam.management.endpoints.enabled=false} while application services remain available.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@TestPropertySource(properties = "iam.management.endpoints.enabled=false")
class ManagementEndpointsDisabledIT extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	@DisplayName("Should not register management REST controllers when endpoints are disabled")
	void shouldNotRegisterManagementControllersWhenDisabled() {
		assertThat(applicationContext.getBeansOfType(UserController.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(RoleController.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(GroupController.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(ScopeController.class)).isEmpty();

		// Use cases must remain available for embedded usage
		assertThat(applicationContext.getBean(ManageUserUseCase.class)).isNotNull();
		assertThat(applicationContext.getBean(ManageRoleUseCase.class)).isNotNull();
		assertThat(applicationContext.getBean(ManageGroupUseCase.class)).isNotNull();
		assertThat(applicationContext.getBean(ManageScopeUseCase.class)).isNotNull();

		// HTTP requests to management endpoints should return 404
		webTestClient.get()
				.uri("/api/v1/users")
				.exchange()
				.expectStatus().isNotFound();

		webTestClient.get()
				.uri("/api/v1/roles")
				.exchange()
				.expectStatus().isNotFound();
	}
}
