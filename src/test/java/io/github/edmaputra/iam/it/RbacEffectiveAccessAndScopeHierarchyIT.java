package io.github.edmaputra.iam.it;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.application.model.EffectiveAccess;
import io.github.edmaputra.iam.application.model.ScopeTreeNode;
import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.application.service.EffectiveAccessResolver;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserGroupMembership;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying hierarchical scope management, subtree recalculation,
 * and comprehensive RBAC EffectiveAccess resolution (direct roles + group inheritance + scope boundaries).
 *
 * @author edmaputra
 */
class RbacEffectiveAccessAndScopeHierarchyIT extends AbstractIntegrationTest {

	@Autowired
	private ManageScopeUseCase manageScopeUseCase;

	@Autowired
	private EffectiveAccessResolver effectiveAccessResolver;

	@Test
	@DisplayName("Should create scope tree hierarchy and update materialized paths on move")
	void shouldManageScopeHierarchyAndMaterializedPaths() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// 1. Create root node: Main Hospital
		ScopeNode hospital = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "HOSPITAL", "Central Hospital"),
				context);

		assertThat(hospital.isRoot()).isTrue();
		assertThat(hospital.getPath()).isEqualTo("/" + tenantId.value() + "/" + hospital.getId().value() + "/");

		// 2. Create child node: Oncology Clinic
		ScopeNode oncology = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, hospital.getId(), "ONCOLOGY", "Oncology Clinic"),
				context);

		assertThat(oncology.getPath()).isEqualTo(hospital.getPath() + oncology.getId().value() + "/");

		// 3. Create second root: Research Wing
		ScopeNode researchWing = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "RESEARCH", "Research Wing"),
				context);

		// 4. Move Oncology under Research Wing
		ScopeNode movedOncology = manageScopeUseCase.moveNode(
				new MoveScopeNodeCommand(tenantId, oncology.getId(), researchWing.getId()),
				context);

		assertThat(movedOncology.getPath()).isEqualTo(researchWing.getPath() + oncology.getId().value() + "/");

		// 5. Verify nested tree representation
		List<ScopeTreeNode> tree = manageScopeUseCase.getScopeTree(tenantId);
		assertThat(tree).hasSize(2);
	}

	@Test
	@DisplayName("Should resolve effective access combining direct roles, group roles, and scope boundaries")
	void shouldResolveEffectiveAccessWithGroupInheritanceAndScopes() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		// 1. Create scope hierarchy: Hospital -> Pediatric Ward
		ScopeNode hospital = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "GEN_HOSP", "General Hospital"),
				context);
		ScopeNode pediatric = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, hospital.getId(), "PED", "Pediatric Ward"),
				context);

		// Unrelated department
		ScopeNode radiology = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "RAD", "Radiology Center"),
				context);

		// 2. Seed User
		User user = User.create("doctor-" + UUID.randomUUID() + "@clinic.org", passwordEncoder.encode("Pass!"), "Dr. House", false);
		userRepository.save(user);

		// 3. Seed Group & Role A assigned to Group
		Group clinicalStaff = Group.create(tenantId, "STAFF", "Clinical Staff", "All medical staff", null);
		groupRepository.save(clinicalStaff);

		Role nurseRole = Role.createCustom(tenantId, "NURSE", "Nurse", "Desc", Set.of("VITAL_SIGNS_WRITE"));
		roleRepository.save(nurseRole);

		// Group assignment scoped to Hospital (inheriting children)
		groupRoleAssignmentRepository.save(GroupRoleAssignment.forScope(
				clinicalStaff.getId(),
				nurseRole.getId(),
				tenantId,
				hospital.getId(),
				true));

		// Add user to Group
		userGroupMembershipRepository.save(UserGroupMembership.of(clinicalStaff.getId(), user.getId()));

		// 4. Direct Role B assigned to User (e.g. Doctor with prescription permission)
		Role doctorRole = Role.createCustom(tenantId, "DOCTOR", "Doctor", "Desc", Set.of("PRESCRIPTION_WRITE"));
		roleRepository.save(doctorRole);

		userRoleAssignmentRepository.save(UserRoleAssignment.createTenantWide(
				user.getId(),
				doctorRole.getId(),
				tenantId));

		// 5. Resolve Effective Access
		EffectiveAccess access = effectiveAccessResolver.resolve(user, tenantId);

		// Assert union of permissions
		assertThat(access.permissions()).containsExactlyInAnyOrder("VITAL_SIGNS_WRITE", "PRESCRIPTION_WRITE");

		// Assert roles
		assertThat(access.roles()).containsExactlyInAnyOrder("NURSE", "DOCTOR");

		// 6. Test CurrentActor scope authorization check
		SecurityContextCurrentActor actor = new SecurityContextCurrentActor(
				user.getId().value(),
				user.getEmail(),
				tenantId.value(),
				user.isPlatformSuperAdmin(),
				access.tenantWide(),
				access.groups(),
				access.roles(),
				access.permissions(),
				access.accessibleScopeNodeIds(),
				access.accessibleScopePaths()
		);

		// Since doctorRole is tenant-wide, actor has tenant-wide access
		assertThat(actor.canAccessScope(hospital.getId().value())).isTrue();
		assertThat(actor.canAccessScope(pediatric.getId().value())).isTrue();
		assertThat(actor.canAccessScope(radiology.getId().value())).isTrue();
	}

	@Test
	@DisplayName("Should restrict access to exact assigned node when inheritChildren is false")
	void shouldEnforceNonInheritedScopeAssignments() {
		TenantId tenantId = TenantId.generate();
		OperationContext context = OperationContext.system();

		ScopeNode building = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.root(tenantId, "BLDG", "Building"), context);
		ScopeNode floor = manageScopeUseCase.createScopeNode(
				CreateScopeNodeCommand.child(tenantId, building.getId(), "FLR", "Floor"), context);

		User user = User.create("guard-" + UUID.randomUUID() + "@clinic.org", passwordEncoder.encode("Pass!"), "Security Guard", false);
		userRepository.save(user);

		Role guardRole = Role.createCustom(tenantId, "GUARD", "Guard", "Desc", Set.of("DOOR_OPEN"));
		roleRepository.save(guardRole);

		// Assign role on Building with inheritChildren = false
		userRoleAssignmentRepository.save(UserRoleAssignment.create(
				user.getId(), guardRole.getId(), tenantId, building.getId(), false));

		EffectiveAccess access = effectiveAccessResolver.resolve(user, tenantId);

		assertThat(access.tenantWide()).isFalse();
		assertThat(access.accessibleScopeNodeIds()).containsExactly(building.getId().value());
		assertThat(access.accessibleScopeNodeIds()).doesNotContain(floor.getId().value());
	}

	@Test
	@DisplayName("Should resolve global system roles (tenant_id = null) alongside tenant roles")
	void shouldResolveGlobalSystemRolesForTenant() {
		TenantId tenantId = TenantId.generate();

		// Create global system role
		String sysCode = "SYS_AUDITOR_" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
		Role systemRole = Role.createSystemRole(sysCode, "System Auditor", "Global auditor", Set.of("AUDIT_LOG_READ"));
		roleRepository.save(systemRole);

		// Create custom tenant role
		Role tenantRole = Role.createCustom(tenantId, "LOCAL_MGR", "Local Manager", "Desc", Set.of("LOCAL_MANAGE"));
		roleRepository.save(tenantRole);

		// Repository query for tenant or global roles
		List<Role> availableRoles = roleRepository.findAllByTenantIdOrGlobal(tenantId);
		Set<String> codes = availableRoles.stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());

		assertThat(codes).contains(sysCode, "LOCAL_MGR");
	}
}
