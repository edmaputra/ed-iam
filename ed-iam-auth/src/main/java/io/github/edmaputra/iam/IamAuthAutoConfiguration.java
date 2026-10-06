package io.github.edmaputra.iam;

import io.github.edmaputra.iam.adapter.security.session.InMemoryLoginAttemptTracker;
import io.github.edmaputra.iam.adapter.security.session.InMemorySessionRegistry;
import io.github.edmaputra.iam.adapter.security.session.InMemoryTokenRevocationStore;
import io.github.edmaputra.iam.adapter.security.session.RedisLoginAttemptTracker;
import io.github.edmaputra.iam.adapter.security.session.RedisSessionRegistry;
import io.github.edmaputra.iam.adapter.security.session.RedisTokenRevocationStore;
import io.github.edmaputra.iam.adapter.security.session.SessionProperties;
import io.github.edmaputra.iam.application.port.out.LoginAttemptTrackerPort;
import io.github.edmaputra.iam.application.port.out.SessionRegistryPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.edmaputra.iam.adapter.rest.AuthController;
import io.github.edmaputra.iam.adapter.rest.MagicLinkController;
import io.github.edmaputra.iam.adapter.rest.MfaController;
import io.github.edmaputra.iam.adapter.security.BCryptPasswordEncoderAdapter;
import io.github.edmaputra.iam.adapter.security.IamResourceServerAutoConfiguration;
import io.github.edmaputra.iam.adapter.security.notifier.LoggingMagicLinkNotifier;
import io.github.edmaputra.iam.adapter.security.properties.MagicLinkProperties;
import io.github.edmaputra.iam.adapter.security.redirect.DefaultAllowedRedirectHostResolver;
import io.github.edmaputra.iam.adapter.security.provider.ApiKeyAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.LocalPasswordAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.MagicLinkAuthProvider;
import io.github.edmaputra.iam.adapter.security.provider.OidcAuthProvider;
import io.github.edmaputra.iam.adapter.security.store.InMemoryMagicLinkTokenStore;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.application.port.out.AllowedRedirectHostResolverPort;
import io.github.edmaputra.iam.application.port.out.ApiKeyValidatorPort;
import io.github.edmaputra.iam.application.port.out.AuthenticationProvider;
import io.github.edmaputra.iam.application.port.out.AuthenticationProviderRouter;
import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.application.service.AuthSessionService;
import io.github.edmaputra.iam.application.service.AuthenticationService;
import io.github.edmaputra.iam.application.service.CredentialAuthService;
import io.github.edmaputra.iam.application.service.EffectiveAccessResolver;
import io.github.edmaputra.iam.application.service.FederatedIdentityService;
import io.github.edmaputra.iam.application.service.MagicLinkDispatchService;
import io.github.edmaputra.iam.application.service.MagicLinkService;
import io.github.edmaputra.iam.application.service.MfaService;
import io.github.edmaputra.iam.application.service.UserTokenService;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring Boot auto-configuration for IAM authentication, credential providers,
 * token issuance, and {@link AuthController}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
@AutoConfiguration(after = IamResourceServerAutoConfiguration.class)
@EnableConfigurationProperties({SessionProperties.class, MagicLinkProperties.class})
@Import({AuthController.class, MfaController.class, MagicLinkController.class})
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
	public CredentialAuthService credentialAuthService(
			AuthenticationProviderRouter authRouter,
			ObjectProvider<LoginAttemptTrackerPort> loginAttemptTrackerProvider,
			ObjectProvider<SecurityAuditRecorder> auditRecorderProvider) {
		return new CredentialAuthService(
				authRouter,
				loginAttemptTrackerProvider.getIfAvailable(),
				auditRecorderProvider.getIfAvailable(SecurityAuditRecorder::noop));
	}

	@Bean
	@ConditionalOnMissingBean
	public UserTokenService userTokenService(
			EffectiveAccessResolver effectiveAccessResolver,
			TokenProviderPort tokenProvider,
			ObjectProvider<UserMfaRepository> userMfaRepositoryProvider,
			ObjectProvider<SecurityAuditRecorder> auditRecorderProvider) {
		return new UserTokenService(
				effectiveAccessResolver,
				tokenProvider,
				userMfaRepositoryProvider.getIfAvailable(),
				auditRecorderProvider.getIfAvailable(SecurityAuditRecorder::noop));
	}

	@Bean
	@ConditionalOnMissingBean
	public AuthSessionService authSessionService(
			ObjectProvider<SessionRegistryPort> sessionRegistryProvider,
			ObjectProvider<TokenRevocationPort> tokenRevocationPortProvider,
			ObjectProvider<SessionProperties> sessionPropertiesProvider,
			ObjectProvider<SecurityAuditRecorder> auditRecorderProvider) {
		return new AuthSessionService(
				sessionRegistryProvider.getIfAvailable(),
				tokenRevocationPortProvider.getIfAvailable(),
				sessionPropertiesProvider.getIfAvailable(SessionProperties::defaultProperties),
				auditRecorderProvider.getIfAvailable(SecurityAuditRecorder::noop));
	}

	@Bean
	@ConditionalOnMissingBean
	public AuthenticateUserUseCase authenticateUserUseCase(
			UserRepository userRepository,
			CredentialAuthService credentialAuthService,
			UserTokenService userTokenService,
			AuthSessionService authSessionService,
			ObjectProvider<SecurityAuditRecorder> auditRecorderProvider) {
		return new AuthenticationService(
				userRepository,
				credentialAuthService,
				userTokenService,
				authSessionService,
				auditRecorderProvider.getIfAvailable(SecurityAuditRecorder::noop));
	}

	@Bean
	@ConditionalOnMissingBean
	public ManageMfaUseCase manageMfaUseCase(
			ObjectProvider<UserMfaRepository> userMfaRepositoryProvider,
			UserRepository userRepository,
			PasswordEncoderPort passwordEncoder,
			UserTokenService userTokenService,
			AuthSessionService authSessionService) {

		return new MfaService(
				userMfaRepositoryProvider.getIfAvailable(),
				userRepository,
				passwordEncoder,
				userTokenService,
				authSessionService);
	}

	@Bean
	@ConditionalOnMissingBean(MagicLinkTokenStorePort.class)
	public MagicLinkTokenStorePort inMemoryMagicLinkTokenStore() {
		return new InMemoryMagicLinkTokenStore();
	}

	@Bean
	@ConditionalOnMissingBean(MagicLinkNotifierPort.class)
	public MagicLinkNotifierPort loggingMagicLinkNotifier() {
		return new LoggingMagicLinkNotifier();
	}

	@Bean
	@ConditionalOnMissingBean
	public MagicLinkAuthProvider magicLinkAuthProvider(
			MagicLinkTokenStorePort magicLinkTokenStore,
			UserRepository userRepository) {
		return new MagicLinkAuthProvider(magicLinkTokenStore, userRepository);
	}

	@Bean
	@ConditionalOnMissingBean(AllowedRedirectHostResolverPort.class)
	public AllowedRedirectHostResolverPort allowedRedirectHostResolver(MagicLinkProperties magicLinkProperties) {
		return new DefaultAllowedRedirectHostResolver(magicLinkProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	public MagicLinkDispatchService magicLinkDispatchService(
			MagicLinkProperties magicLinkProperties,
			MagicLinkTokenStorePort magicLinkTokenStore,
			MagicLinkNotifierPort magicLinkNotifier,
			ObjectProvider<AllowedRedirectHostResolverPort> allowedRedirectHostResolverProvider) {
		AllowedRedirectHostResolverPort resolver = allowedRedirectHostResolverProvider.getIfAvailable(
				() -> new DefaultAllowedRedirectHostResolver(magicLinkProperties));
		return new MagicLinkDispatchService(
				magicLinkProperties,
				magicLinkTokenStore,
				magicLinkNotifier,
				resolver);
	}

	@Bean
	@ConditionalOnMissingBean
	public ManageMagicLinkUseCase manageMagicLinkUseCase(
			MagicLinkProperties magicLinkProperties,
			UserRepository userRepository,
			MagicLinkDispatchService magicLinkDispatchService,
			MagicLinkTokenStorePort magicLinkTokenStore,
			AuthenticationProviderRouter authRouter,
			UserTokenService userTokenService,
			AuthSessionService authSessionService) {
		return new MagicLinkService(
				magicLinkProperties,
				userRepository,
				magicLinkDispatchService,
				magicLinkTokenStore,
				authRouter,
				userTokenService,
				authSessionService);
	}

	@Configuration(proxyBeanMethods = false)
	@Order(1)
	@ConditionalOnClass(StringRedisTemplate.class)
	@ConditionalOnBean(StringRedisTemplate.class)
	static class RedisSessionConfiguration {

		@Bean
		@ConditionalOnMissingBean(TokenRevocationPort.class)
		public TokenRevocationPort redisTokenRevocationStore(StringRedisTemplate redisTemplate) {
			return new RedisTokenRevocationStore(redisTemplate);
		}

		@Bean
		@ConditionalOnMissingBean(SessionRegistryPort.class)
		public SessionRegistryPort redisSessionRegistry(
				StringRedisTemplate redisTemplate) {
			return new RedisSessionRegistry(redisTemplate);
		}

		@Bean
		@ConditionalOnMissingBean(LoginAttemptTrackerPort.class)
		public LoginAttemptTrackerPort redisLoginAttemptTracker(
				StringRedisTemplate redisTemplate,
				SessionProperties sessionProperties) {
			return new RedisLoginAttemptTracker(redisTemplate, sessionProperties);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Order(2)
	static class InMemorySessionConfiguration {

		@Bean
		@ConditionalOnMissingBean(TokenRevocationPort.class)
		public TokenRevocationPort inMemoryTokenRevocationStore() {
			return new InMemoryTokenRevocationStore();
		}

		@Bean
		@ConditionalOnMissingBean(SessionRegistryPort.class)
		public SessionRegistryPort inMemorySessionRegistry() {
			return new InMemorySessionRegistry();
		}

		@Bean
		@ConditionalOnMissingBean(LoginAttemptTrackerPort.class)
		public LoginAttemptTrackerPort inMemoryLoginAttemptTracker(
				SessionProperties sessionProperties) {
			return new InMemoryLoginAttemptTracker(sessionProperties);
		}
	}
}

