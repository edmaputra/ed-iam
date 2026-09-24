package io.github.edmaputra.iam;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.github.edmaputra.iam.adapter.rest.AuthController;
import io.github.edmaputra.iam.adapter.security.BCryptPasswordEncoderAdapter;
import io.github.edmaputra.iam.adapter.security.IamResourceServerAutoConfiguration;
import io.github.edmaputra.iam.adapter.security.provider.ApiKeyAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.LocalPasswordAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.OidcAuthProvider;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.service.AuthenticationService;
import io.github.edmaputra.iam.application.service.EffectiveAccessResolver;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Spring Boot auto-configuration for IAM authentication, credential providers,
 * token issuance, and {@link AuthController}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
@AutoConfiguration(after = IamResourceServerAutoConfiguration.class)
@Import(AuthController.class)
public class IamAuthAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public PasswordEncoderPort passwordEncoderPort() {
		return new BCryptPasswordEncoderAdapter();
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
			ScopeNodeRepository scopeNodeRepository) {
		return new EffectiveAccessResolver(
				userGroupMembershipRepository,
				groupRepository,
				userRoleAssignmentRepository,
				groupRoleAssignmentRepository,
				roleRepository,
				scopeNodeRepository);
	}

	@Bean
	@ConditionalOnMissingBean
	public AuthenticateUserUseCase authenticateUserUseCase(
			AuthenticationProviderRouter authRouter,
			UserRepository userRepository,
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider) {
		return new AuthenticationService(authRouter, userRepository, effectiveAccessResolver, tokenProvider);
	}
}
