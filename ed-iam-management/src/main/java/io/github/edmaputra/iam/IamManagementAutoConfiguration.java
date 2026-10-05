package io.github.edmaputra.iam;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.github.edmaputra.iam.adapter.rest.GroupController;
import io.github.edmaputra.iam.adapter.rest.RoleController;
import io.github.edmaputra.iam.adapter.rest.ScopeController;
import io.github.edmaputra.iam.adapter.rest.UserController;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.domain.security.DefaultPasswordValidator;
import io.github.edmaputra.iam.domain.security.PasswordValidator;
import io.github.edmaputra.iam.application.service.GroupLifecycleService;
import io.github.edmaputra.iam.application.service.GroupManagementService;
import io.github.edmaputra.iam.application.service.GroupRoleAssignmentService;
import io.github.edmaputra.iam.application.service.RoleManagementService;
import io.github.edmaputra.iam.application.service.SessionManagementService;
import io.github.edmaputra.iam.application.service.UserAccountService;
import io.github.edmaputra.iam.application.service.UserCredentialService;
import io.github.edmaputra.iam.application.service.UserGroupMembershipService;
import io.github.edmaputra.iam.application.service.UserManagementService;
import io.github.edmaputra.iam.application.service.UserRoleAssignmentService;
import io.github.edmaputra.iam.application.service.UserSessionRevocationService;

import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

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
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
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

	@Bean
	@ConditionalOnMissingBean
	public PasswordValidator passwordValidator() {
		return new DefaultPasswordValidator();
	}

	/**
	 * Registers the {@link ManageUserUseCase} bean.
	 *
	 * @param userRepository               the user repository
	 * @param passwordEncoder              the password encoder
	 * @param sessionRegistryProvider      session registry provider
	 * @param tokenRevocationPortProvider  token revocation provider
	 * @param userMfaRepositoryProvider    user MFA provider
	 * @param currentActorProvider         current actor provider
	 * @param eventPublisherProvider       event publisher provider
	 * @param passwordValidatorProvider    password validator provider
	 * @return user account service
	 */
	@Bean
	@ConditionalOnMissingBean
	public UserCredentialService userCredentialService(
			PasswordEncoderPort passwordEncoder,
			ObjectProvider<PasswordValidator> passwordValidatorProvider) {
		return new UserCredentialService(passwordEncoder, passwordValidatorProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public UserSessionRevocationService userSessionRevocationService(
			ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
			ObjectProvider<TokenRevocationPort> tokenRevocationPortProvider) {
		return new UserSessionRevocationService(
				sessionRegistryProvider.getIfAvailable(),
				tokenRevocationPortProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public UserAccountService userAccountService(
			UserRepository userRepository,
			UserCredentialService credentialService,
			UserSessionRevocationService sessionRevocationService,
			ObjectProvider<UserMfaRepository> userMfaRepositoryProvider,
			ObjectProvider<CurrentActorProvider> currentActorProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new UserAccountService(
				userRepository,
				credentialService,
				sessionRevocationService,
				userMfaRepositoryProvider.getIfAvailable(),
				currentActorProvider.getIfAvailable(),
				eventPublisherProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public UserRoleAssignmentService userRoleAssignmentService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			ObjectProvider<CurrentActorProvider> currentActorProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new UserRoleAssignmentService(
				userRepository,
				roleRepository,
				userRoleAssignmentRepository,
				currentActorProvider.getIfAvailable(),
				eventPublisherProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public UserGroupMembershipService userGroupMembershipService(
			UserRepository userRepository,
			GroupRepository groupRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			ObjectProvider<CurrentActorProvider> currentActorProvider) {
		return new UserGroupMembershipService(
				userRepository,
				groupRepository,
				userGroupMembershipRepository,
				currentActorProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public ManageUserUseCase manageUserUseCase(
			UserAccountService userAccountService,
			UserRoleAssignmentService userRoleAssignmentService,
			UserGroupMembershipService userGroupMembershipService) {
		return new UserManagementService(userAccountService, userRoleAssignmentService, userGroupMembershipService);
	}

	/**
	 * Registers the {@link SessionManagementService} implementing session and lockout management use cases.
	 */
	@Bean
	@ConditionalOnMissingBean
	public SessionManagementService sessionManagementService(
			UserRepository userRepository,
			ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
			ObjectProvider<TokenRevocationPort> tokenRevocationPortProvider,
			ObjectProvider<LoginAttemptTrackerPort> loginAttemptTrackerProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new SessionManagementService(
				userRepository,
				sessionRegistryProvider.getIfAvailable(),
				tokenRevocationPortProvider.getIfAvailable(),
				loginAttemptTrackerProvider.getIfAvailable(),
				eventPublisherProvider.getIfAvailable());
	}


	/**
	 * Registers the {@link ManageRoleUseCase} bean.
	 *
	 * @param roleRepository the role repository
	 * @return role management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public ManageRoleUseCase manageRoleUseCase(
			RoleRepository roleRepository,
			ObjectProvider<CurrentActorProvider> currentActorProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new RoleManagementService(roleRepository, currentActorProvider.getIfAvailable(), eventPublisherProvider.getIfAvailable());
	}

	/**
	 * Registers the {@link ManageGroupUseCase} bean.
	 *
	 * @param groupRepository              the group repository
	 * @param groupRoleAssignmentRepository the group role assignment repository
	 * @param userGroupMembershipRepository the user group membership repository
	 * @param roleRepository               the role repository
	 * @param userRepository               the user repository
	 * @param currentActorProvider         provider for current actor security context
	 * @return group management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public GroupLifecycleService groupLifecycleService(
			GroupRepository groupRepository,
			ObjectProvider<CurrentActorProvider> currentActorProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new GroupLifecycleService(
				groupRepository,
				currentActorProvider.getIfAvailable(),
				eventPublisherProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public GroupRoleAssignmentService groupRoleAssignmentService(
			GroupRepository groupRepository,
			RoleRepository roleRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			ObjectProvider<CurrentActorProvider> currentActorProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new GroupRoleAssignmentService(
				groupRepository,
				roleRepository,
				groupRoleAssignmentRepository,
				currentActorProvider.getIfAvailable(),
				eventPublisherProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public ManageGroupUseCase manageGroupUseCase(
			GroupLifecycleService groupLifecycleService,
			GroupRoleAssignmentService groupRoleAssignmentService,
			UserGroupMembershipService userGroupMembershipService) {
		return new GroupManagementService(groupLifecycleService, groupRoleAssignmentService, userGroupMembershipService);
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
