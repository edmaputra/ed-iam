package io.github.edmaputra.iam;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.edmaputra.iam.adapter.rest.GroupController;
import io.github.edmaputra.iam.adapter.rest.RoleController;
import io.github.edmaputra.iam.adapter.rest.ScopeController;
import io.github.edmaputra.iam.adapter.rest.UserController;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.service.GroupManagementService;
import io.github.edmaputra.iam.application.service.RoleManagementService;
import io.github.edmaputra.iam.application.service.UserManagementService;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Spring Boot auto-configuration for administrative IAM entity management services
 * (Users, Roles, Groups, Scopes) and their toggleable REST endpoints.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration(after = {IamSecurityAutoConfiguration.class, IamScopeAutoConfiguration.class})
public class IamManagementAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public PasswordEncoderPort passwordEncoderPort() {
		org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
				new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
		return new PasswordEncoderPort() {
			@Override
			public String encode(CharSequence rawPassword) {
				return encoder.encode(rawPassword);
			}

			@Override
			public boolean matches(CharSequence rawPassword, String encodedPassword) {
				return encoder.matches(rawPassword, encodedPassword);
			}
		};
	}

	/**
	 * Registers the {@link ManageUserUseCase} bean.
	 *
	 * @param userRepository               the user repository
	 * @param passwordEncoder              the password encoder
	 * @param userRoleAssignmentRepository the user role assignment repository
	 * @param userGroupMembershipRepository the user group membership repository
	 * @param roleRepository               the role repository
	 * @param groupRepository              the group repository
	 * @return user management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public ManageUserUseCase manageUserUseCase(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			GroupRepository groupRepository) {
		return new UserManagementService(
				userRepository,
				passwordEncoder,
				userRoleAssignmentRepository,
				userGroupMembershipRepository,
				roleRepository,
				groupRepository);
	}

	/**
	 * Registers the {@link ManageRoleUseCase} bean.
	 *
	 * @param roleRepository the role repository
	 * @return role management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public ManageRoleUseCase manageRoleUseCase(RoleRepository roleRepository) {
		return new RoleManagementService(roleRepository);
	}

	/**
	 * Registers the {@link ManageGroupUseCase} bean.
	 *
	 * @param groupRepository              the group repository
	 * @param groupRoleAssignmentRepository the group role assignment repository
	 * @param userGroupMembershipRepository the user group membership repository
	 * @param roleRepository               the role repository
	 * @param userRepository               the user repository
	 * @return group management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public ManageGroupUseCase manageGroupUseCase(
			GroupRepository groupRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			RoleRepository roleRepository,
			UserRepository userRepository) {
		return new GroupManagementService(
				groupRepository,
				groupRoleAssignmentRepository,
				userGroupMembershipRepository,
				roleRepository,
				userRepository);
	}

	/**
	 * Auto-configuration for entity management REST controllers, enabled by default
	 * and toggleable via {@code iam.management.endpoints.enabled=false}.
	 *
	 * @author edmaputra
	 * @since 0.0.1
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "iam.management.endpoints", name = "enabled", havingValue = "true", matchIfMissing = true)
	@Import({UserController.class, RoleController.class, GroupController.class, ScopeController.class})
	public static class ManagementEndpointsConfiguration {
	}
}
