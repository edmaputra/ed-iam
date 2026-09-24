package io.github.edmaputra.iam;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
import io.github.edmaputra.iam.adapter.rest.IamExceptionHandler;
import io.github.edmaputra.iam.adapter.security.IamResourceServerAutoConfiguration;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;

/**
 * Spring Boot auto-configuration for IAM persistence adapters and JPA repositories.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration(after = IamResourceServerAutoConfiguration.class)
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
}
