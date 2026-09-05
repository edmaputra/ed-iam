package io.github.edmaputra.iam.it;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying database constraint cascades (PostgreSQL foreign keys)
 * and cross-tenant duplicate code isolation.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class CascadeAndCrossTenantIsolationIT extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserIdentityRepository userIdentityRepository;

	@Autowired
	private UserGroupMembershipRepository userGroupMembershipRepository;

	@Autowired
	private UserRoleAssignmentRepository userRoleAssignmentRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private GroupRoleAssignmentRepository groupRoleAssignmentRepository;

	@Autowired
	private ManageScopeUseCase manageScopeUseCase;

	@Test
	@DisplayName("Should allow identical codes across different tenants but reject duplicates within same tenant")
	void shouldIsolateCodesAcrossTenants() {
		TenantId tenantA = TenantId.generate();
		TenantId tenantB = TenantId.generate();
		OperationContext context = OperationContext.system();

		String code = "SHARED_CODE";

		// 1. Scope Node: Can exist in both tenants
		ScopeNode scopeA = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantA, code, "Scope in Tenant A"),
				context);
		ScopeNode scopeB = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantB, code, "Scope in Tenant B"),
				context);

		assertThat(scopeA.getCode()).isEqualTo(code);
		assertThat(scopeB.getCode()).isEqualTo(code);
		assertThat(scopeA.getId()).isNotEqualTo(scopeB.getId());

		// Duplicate scope code in same tenant throws IllegalArgumentException
		assertThatThrownBy(() -> manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantA, code, "Duplicate Scope"),
				context))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already exists for this tenant");

		// 2. Group: Can exist in both tenants
		Group groupA = Group.create(tenantA, code, "Group A", "Desc", null);
		Group groupB = Group.create(tenantB, code, "Group B", "Desc", null);
		groupRepository.save(groupA);
		groupRepository.save(groupB);

		assertThat(groupRepository.existsByTenantIdAndCode(tenantA, code)).isTrue();
		assertThat(groupRepository.existsByTenantIdAndCode(tenantB, code)).isTrue();

		// Duplicate group in same tenant violates uk_iam_group_tenant_code
		Group duplicateGroup = Group.create(tenantA, code, "Duplicate Group", "Desc", null);
		assertThatThrownBy(() -> groupRepository.save(duplicateGroup))
				.isInstanceOf(Exception.class);

		// 3. Role: Can exist in both tenants
		Role roleA = Role.createCustom(tenantA, code, "Role A", "Desc", Set.of("READ"));
		Role roleB = Role.createCustom(tenantB, code, "Role B", "Desc", Set.of("WRITE"));
		roleRepository.save(roleA);
		roleRepository.save(roleB);

		assertThat(roleRepository.existsByTenantIdAndCode(tenantA, code)).isTrue();
		assertThat(roleRepository.existsByTenantIdAndCode(tenantB, code)).isTrue();

		// Duplicate role in same tenant violates uk_iam_role_tenant_code
		Role duplicateRole = Role.createCustom(tenantA, code, "Duplicate Role", "Desc", Set.of("EXEC"));
		assertThatThrownBy(() -> roleRepository.save(duplicateRole))
				.isInstanceOf(Exception.class);
	}

	@Test
	@DisplayName("Should cascade delete user identities, group memberships, and role assignments when user is deleted")
	void shouldCascadeDeleteOnUserRemoval() {
		TenantId tenantId = TenantId.generate();

		// 1. Seed user, role, group
		User user = User.create("cascade-" + UUID.randomUUID() + "@clinic.org", "hash", "Cascade User", false);
		user = userRepository.save(user);

		Role role = Role.createCustom(tenantId, "TEMP_ROLE", "Temp Role", "Desc", Set.of("ACTION"));
		role = roleRepository.save(role);

		Group group = Group.create(tenantId, "TEMP_GRP", "Temp Group", "Desc", null);
		group = groupRepository.save(group);

		// 2. Add identity, membership, and role assignment
		UserIdentity identity = UserIdentity.create(user.getId(), ProviderType.OIDC_GENERIC, "sub-" + UUID.randomUUID(), "https://idp.com");
		userIdentityRepository.save(identity);

		userGroupMembershipRepository.save(UserGroupMembership.of(group.getId(), user.getId()));
		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(user.getId(), role.getId(), tenantId));

		// Verify entities exist
		assertThat(userIdentityRepository.findByProviderTypeAndExternalSubjectId(identity.getProviderType(), identity.getExternalSubjectId())).isPresent();
		assertThat(userGroupMembershipRepository.existsByGroupIdAndUserId(group.getId(), user.getId())).isTrue();
		assertThat(userRoleAssignmentRepository.findAllByUserId(user.getId())).hasSize(1);

		// 3. Delete user
		userRepository.delete(user.getId());

		// 4. Verify cascade deletions in child tables
		assertThat(userRepository.findById(user.getId())).isEmpty();
		assertThat(userIdentityRepository.findByProviderTypeAndExternalSubjectId(identity.getProviderType(), identity.getExternalSubjectId())).isEmpty();
		assertThat(userGroupMembershipRepository.existsByGroupIdAndUserId(group.getId(), user.getId())).isFalse();
		assertThat(userRoleAssignmentRepository.findAllByUserId(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("Should cascade delete group memberships and group role assignments when group is deleted")
	void shouldCascadeDeleteOnGroupRemoval() {
		TenantId tenantId = TenantId.generate();

		User user = User.create("grp-cascade-" + UUID.randomUUID() + "@clinic.org", "hash", "User", false);
		user = userRepository.save(user);

		Role role = Role.createCustom(tenantId, "GRP_ROLE", "Group Role", "Desc", Set.of("OP"));
		role = roleRepository.save(role);

		Group group = Group.create(tenantId, "TO_DELETE", "To Delete", "Desc", null);
		group = groupRepository.save(group);

		userGroupMembershipRepository.save(UserGroupMembership.of(group.getId(), user.getId()));
		groupRoleAssignmentRepository.save(GroupRoleAssignment.createTenantWide(group.getId(), role.getId(), tenantId));

		assertThat(groupRoleAssignmentRepository.findAllByGroupIds(Set.of(group.getId()))).hasSize(1);

		// Delete group
		groupRepository.delete(group.getId());

		assertThat(groupRepository.findById(group.getId())).isEmpty();
		assertThat(userGroupMembershipRepository.existsByGroupIdAndUserId(group.getId(), user.getId())).isFalse();
		assertThat(groupRoleAssignmentRepository.findAllByGroupIds(Set.of(group.getId()))).isEmpty();
	}
}
