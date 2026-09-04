package io.github.edmaputra.iam.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignmentTest {

	@Test
	@DisplayName("Should create scoped user role assignment")
	void shouldCreateScopedUserRoleAssignment() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeNodeId = ScopeNodeId.generate();

		UserRoleAssignment assignment = UserRoleAssignment.create(
				userId,
				roleId,
				tenantId,
				scopeNodeId,
				true);

		assertThat(assignment.getId()).isNotNull();
		assertThat(assignment.getUserId()).isEqualTo(userId);
		assertThat(assignment.getRoleId()).isEqualTo(roleId);
		assertThat(assignment.getTenantId()).isEqualTo(tenantId);
		assertThat(assignment.getScopeNodeId()).isEqualTo(scopeNodeId);
		assertThat(assignment.isInheritChildren()).isTrue();
		assertThat(assignment.isTenantWide()).isFalse();
		assertThat(assignment.isGlobal()).isFalse();
	}

	@Test
	@DisplayName("Should create tenant-wide and global superadmin user role assignments")
	void shouldCreateTenantWideAndGlobalUserRoleAssignments() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();

		UserRoleAssignment tenantWide = UserRoleAssignment.createTenantWide(userId, roleId, tenantId);
		assertThat(tenantWide.isTenantWide()).isTrue();
		assertThat(tenantWide.isGlobal()).isFalse();
		assertThat(tenantWide.getScopeNodeId()).isNull();

		UserRoleAssignment global = UserRoleAssignment.createGlobalSuperadmin(userId, roleId);
		assertThat(global.isGlobal()).isTrue();
		assertThat(global.isTenantWide()).isTrue();
		assertThat(global.getTenantId()).isNull();
	}

	@Test
	@DisplayName("Should create scoped and tenant-wide group role assignments")
	void shouldCreateGroupRoleAssignments() {
		GroupId groupId = GroupId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeNodeId = ScopeNodeId.generate();

		GroupRoleAssignment scoped = GroupRoleAssignment.create(groupId, roleId, tenantId, scopeNodeId, true);
		assertThat(scoped.getGroupId()).isEqualTo(groupId);
		assertThat(scoped.isTenantWide()).isFalse();
		assertThat(scoped.tenantId()).isEqualTo(tenantId);

		GroupRoleAssignment tenantWide = GroupRoleAssignment.createTenantWide(groupId, roleId, tenantId);
		assertThat(tenantWide.isTenantWide()).isTrue();
		assertThat(tenantWide.getScopeNodeId()).isNull();
	}
}
