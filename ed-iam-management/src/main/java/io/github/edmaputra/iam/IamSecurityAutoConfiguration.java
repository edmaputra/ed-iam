package io.github.edmaputra.iam;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.github.edmaputra.iam.adapter.persistence.crypto.AesGcmEncryptor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.persistence.adapter.GroupRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.GroupRoleAssignmentRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.JpaMagicLinkTokenStoreAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.RoleRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserGroupMembershipRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserIdentityRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserMfaRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.UserRoleAssignmentRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.MagicLinkTokenJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.RoleJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserGroupMembershipJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserIdentityJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserMfaJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.UserRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.application.port.out.MagicLinkTokenStorePort;
import io.github.edmaputra.iam.adapter.rest.IamExceptionHandler;
import io.github.edmaputra.iam.adapter.security.IamResourceServerAutoConfiguration;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.adapter.persistence.adapter.PasswordPolicyRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.PermissionRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.adapter.RedirectUriRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.repository.PasswordPolicyJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.PermissionJpaRepository;
import io.github.edmaputra.iam.adapter.persistence.repository.RedirectUriJpaRepository;
import io.github.edmaputra.iam.domain.repository.PasswordPolicyRepository;
import io.github.edmaputra.iam.domain.repository.PermissionRepository;
import io.github.edmaputra.iam.domain.repository.RedirectUriRepository;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Spring Boot auto-configuration for IAM persistence adapters and JPA repositories.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration(after = IamResourceServerAutoConfiguration.class, beforeName = "io.github.edmaputra.iam.IamAuthAutoConfiguration")
@EntityScan(basePackages = "io.github.edmaputra.iam.adapter.persistence.entity")
@EnableJpaRepositories(basePackages = "io.github.edmaputra.iam.adapter.persistence.repository")
@Import(IamExceptionHandler.class)
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
	public AesGcmEncryptor aesGcmEncryptor(ObjectProvider<JwtProperties> jwtPropertiesProvider) {
		JwtProperties jwtProperties = jwtPropertiesProvider.getIfAvailable();
		String secret = (jwtProperties != null && jwtProperties.secret() != null)
				? jwtProperties.secret()
				: "iam-default-fallback-mfa-encryption-key-for-credentials-at-rest!";
		return new AesGcmEncryptor(secret);
	}

	@Bean
	@ConditionalOnMissingBean
	public UserMfaRepository userMfaRepository(
			UserMfaJpaRepository repository,
			ObjectProvider<AesGcmEncryptor> encryptorProvider) {
		return new UserMfaRepositoryAdapter(repository, encryptorProvider.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public MagicLinkTokenStorePort magicLinkTokenStorePort(MagicLinkTokenJpaRepository repository) {
		return new JpaMagicLinkTokenStoreAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public PasswordPolicyRepository passwordPolicyRepository(PasswordPolicyJpaRepository repository) {
		return new PasswordPolicyRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public RedirectUriRepository redirectUriRepository(RedirectUriJpaRepository repository) {
		return new RedirectUriRepositoryAdapter(repository);
	}

	@Bean
	@ConditionalOnMissingBean
	public PermissionRepository permissionRepository(PermissionJpaRepository repository) {
		return new PermissionRepositoryAdapter(repository);
	}
}
