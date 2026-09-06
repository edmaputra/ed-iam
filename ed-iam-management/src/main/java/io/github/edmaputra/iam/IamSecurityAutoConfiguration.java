package io.github.edmaputra.iam;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;
import io.github.edmaputra.iam.adapter.persistence.adapter.GroupRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.GroupRoleAssignmentRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.RoleRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserGroupMembershipRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserIdentityRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserRoleAssignmentRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.RoleJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserGroupMembershipJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserIdentityJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.adapter.rest.AuthController;
import io.github.edmaputra.iam.adapter.rest.IamExceptionHandler;
import io.github.edmaputra.iam.adapter.security.BCryptPasswordEncoderAdapter;
import io.github.edmaputra.iam.adapter.security.SecurityContextAccessor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.adapter.security.provider.ApiKeyAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.LocalPasswordAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.OidcAuthProvider;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.service.AuthenticationService;
import io.github.edmaputra.iam.application.service.EffectiveAccessResolver;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.application.service.ScopeSubtreeResolver;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Spring Boot auto-configuration for IAM security, authentication SPI providers,
 * JWT token engine, persistence adapters, and security context bridges.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@EntityScan(basePackages = "io.github.edmaputra.iam.adapter.persistence.entity")
@EnableJpaRepositories(basePackages = "io.github.edmaputra.iam.adapter.persistence.repository")
@Import({AuthController.class, IamExceptionHandler.class})
public class IamSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public UserRepository userRepository(UserJpaRepository repository) {
		return new UserRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public RoleRepository roleRepository(RoleJpaRepository repository) {
		return new RoleRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public GroupRepository groupRepository(GroupJpaRepository repository) {
		return new GroupRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public UserGroupMembershipRepository userGroupMembershipRepository(UserGroupMembershipJpaRepository repository) {
		return new UserGroupMembershipRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public UserRoleAssignmentRepository userRoleAssignmentRepository(UserRoleAssignmentJpaRepository repository) {
		return new UserRoleAssignmentRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public GroupRoleAssignmentRepository groupRoleAssignmentRepository(GroupRoleAssignmentJpaRepository repository) {
		return new GroupRoleAssignmentRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public UserIdentityRepository userIdentityRepository(UserIdentityJpaRepository repository) {
		return new UserIdentityRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public FederatedIdentityService federatedIdentityService(
			UserRepository userRepository,
			UserIdentityRepository userIdentityRepository,
			GroupRepository groupRepository,
			UserGroupMembershipRepository userGroupMembershipRepository) {
		return new FederatedIdentityService(userRepository, userIdentityRepository, groupRepository, userGroupMembershipRepository);
	}

	@Bean
	@ConditionalOnMissingBean
	public PasswordEncoderPort passwordEncoderPort() {
		return new BCryptPasswordEncoderAdapter();
	}

	@Bean
	@ConditionalOnMissingBean
	public LocalPasswordAuthProvider localPasswordAuthProvider(
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder) {
		return new LocalPasswordAuthProvider(userRepository, passwordEncoder);
	}

	@Bean
	@ConditionalOnMissingBean
	public OidcAuthProvider oidcAuthProvider(FederatedIdentityService federatedIdentityService) {
		return new OidcAuthProvider(federatedIdentityService);
	}

	/**
	 * Configures the machine-to-machine (M2M) API key authentication provider when an {@link ApiKeyValidatorPort} bean is present.
	 *
	 * @param apiKeyValidator the API key validation port
	 * @return the API key authentication provider
	 */
	@Bean
	@ConditionalOnBean(ApiKeyValidatorPort.class)
	@ConditionalOnMissingBean
	public ApiKeyAuthProvider apiKeyAuthProvider(ApiKeyValidatorPort apiKeyValidator) {
		return new ApiKeyAuthProvider(apiKeyValidator);
	}

	@Bean
	@ConditionalOnMissingBean
	public AuthenticationProviderRouter authenticationProviderRouter(List<AuthenticationProvider> providers) {
		return new AuthenticationProviderRouter(providers);
	}

	@Bean
	@ConditionalOnMissingBean
	public EffectiveAccessResolver effectiveAccessResolver(
			UserGroupMembershipRepository userGroupMembershipRepository,
			GroupRepository groupRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository,
			RoleRepository roleRepository,
			ScopeNodeRepository scopeNodeRepository,
			ScopeSubtreeResolver scopeSubtreeResolver) {
		return new EffectiveAccessResolver(
				userGroupMembershipRepository,
				groupRepository,
				userRoleAssignmentRepository,
				groupRoleAssignmentRepository,
				roleRepository,
				scopeNodeRepository,
				scopeSubtreeResolver);
	}

	@Bean
	@ConditionalOnMissingBean
	public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
		return new JwtTokenProvider(properties);
	}

	@Bean
	@ConditionalOnMissingBean(CurrentActorProvider.class)
	public SecurityContextAccessor securityContextAccessor() {
		return new SecurityContextAccessor();
	}

	@Bean
	@ConditionalOnMissingBean
	public AuthenticateUserUseCase authenticateUserUseCase(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			JwtTokenProvider jwtTokenProvider) {
		return new AuthenticationService(authRouter, userRepository, effectiveAccessResolver, jwtTokenProvider);
	}

	@Bean
	@ConditionalOnMissingBean
	public JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtTokenProvider jwtTokenProvider,
			SecurityContextAccessor securityContextAccessor,
			ObjectProvider<TenantContextBridge> tenantContextBridgeProvider) {
		return new JwtAuthenticationFilter(jwtTokenProvider, securityContextAccessor, tenantContextBridgeProvider);
	}
}
