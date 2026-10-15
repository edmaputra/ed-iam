package io.github.edmaputra.iam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.edmaputra.iam.adapter.rest.UserController;
import io.github.edmaputra.iam.application.service.ScopeSubtreeResolver;
import io.github.edmaputra.iam.application.service.UserManagementService;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.PasswordPolicyRepository;
import io.github.edmaputra.iam.domain.repository.PermissionRepository;
import io.github.edmaputra.iam.domain.repository.RedirectUriRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying conditional property toggles across ed-iam-management auto-configurations.
 *
 * @author edmaputra
 * @since 0.12.0
 */
class IamManagementAutoConfigurationConditionsTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner();

	@Test
	@DisplayName("Should omit all management beans when ed-iam.management.enabled is false")
	void shouldOmitManagementWhenDisabled() {
		contextRunner
				.withConfiguration(AutoConfigurations.of(IamManagementAutoConfiguration.class))
				.withUserConfiguration(MockRepositoriesConfig.class)
				.withPropertyValues("ed-iam.management.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(IamManagementAutoConfiguration.class);
					assertThat(context).doesNotHaveBean(UserManagementService.class);
					assertThat(context).doesNotHaveBean(UserController.class);
				});
	}

	@Test
	@DisplayName("Should omit controllers when ed-iam.management.endpoints.enabled is false")
	void shouldOmitControllersWhenEndpointsDisabledWithEdIamPrefix() {
		contextRunner
				.withConfiguration(AutoConfigurations.of(IamManagementAutoConfiguration.class))
				.withUserConfiguration(MockRepositoriesConfig.class)
				.withPropertyValues("ed-iam.management.endpoints.enabled=false")
				.run(context -> {
					assertThat(context).hasSingleBean(UserManagementService.class);
					assertThat(context).doesNotHaveBean(UserController.class);
				});
	}

	@Test
	@DisplayName("Should omit scope beans when ed-iam.scope.enabled is false")
	void shouldOmitScopeBeansWhenDisabled() {
		contextRunner
				.withConfiguration(AutoConfigurations.of(IamScopeAutoConfiguration.class))
				.withBean(ScopeNodeRepository.class, () -> Mockito.mock(ScopeNodeRepository.class))
				.withPropertyValues("ed-iam.scope.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(IamScopeAutoConfiguration.class);
					assertThat(context).doesNotHaveBean(ScopeSubtreeResolver.class);
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class MockRepositoriesConfig {

		@Bean
		UserRepository userRepository() {
			return Mockito.mock(UserRepository.class);
		}

		@Bean
		RoleRepository roleRepository() {
			return Mockito.mock(RoleRepository.class);
		}

		@Bean
		GroupRepository groupRepository() {
			return Mockito.mock(GroupRepository.class);
		}

		@Bean
		ScopeNodeRepository scopeNodeRepository() {
			return Mockito.mock(ScopeNodeRepository.class);
		}

		@Bean
		UserRoleAssignmentRepository userRoleAssignmentRepository() {
			return Mockito.mock(UserRoleAssignmentRepository.class);
		}

		@Bean
		GroupRoleAssignmentRepository groupRoleAssignmentRepository() {
			return Mockito.mock(GroupRoleAssignmentRepository.class);
		}

		@Bean
		UserGroupMembershipRepository userGroupMembershipRepository() {
			return Mockito.mock(UserGroupMembershipRepository.class);
		}

		@Bean
		UserIdentityRepository userIdentityRepository() {
			return Mockito.mock(UserIdentityRepository.class);
		}

		@Bean
		UserMfaRepository userMfaRepository() {
			return Mockito.mock(UserMfaRepository.class);
		}

		@Bean
		PasswordPolicyRepository passwordPolicyRepository() {
			return Mockito.mock(PasswordPolicyRepository.class);
		}

		@Bean
		PermissionRepository permissionRepository() {
			return Mockito.mock(PermissionRepository.class);
		}

		@Bean
		RedirectUriRepository redirectUriRepository() {
			return Mockito.mock(RedirectUriRepository.class);
		}

		@Bean
		CurrentActorProvider currentActorProvider() {
			return Mockito.mock(CurrentActorProvider.class);
		}
	}
}
