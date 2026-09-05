package io.github.edmaputra.iam.it.app;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.GroupRepository;
import io.github.edmaputra.iam.domain.repository.GroupRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.repository.RoleRepository;
import io.github.edmaputra.iam.domain.repository.UserGroupMembershipRepository;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.repository.UserRoleAssignmentRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Test REST controller exposing management endpoints for scopes, groups, roles, and cascade deletion status
 * to enable testing cascade foreign-key constraints and cross-tenant isolation over HTTP.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/test/admin")
public class TestCascadeManagementController {

	private final ManageScopeUseCase manageScopeUseCase;
	private final GroupRepository groupRepository;
	private final RoleRepository roleRepository;
	private final UserRepository userRepository;
	private final UserIdentityRepository userIdentityRepository;
	private final UserGroupMembershipRepository userGroupMembershipRepository;
	private final UserRoleAssignmentRepository userRoleAssignmentRepository;
	private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;

	public TestCascadeManagementController(
			ManageScopeUseCase manageScopeUseCase,
			GroupRepository groupRepository,
			RoleRepository roleRepository,
			UserRepository userRepository,
			UserIdentityRepository userIdentityRepository,
			UserGroupMembershipRepository userGroupMembershipRepository,
			UserRoleAssignmentRepository userRoleAssignmentRepository,
			GroupRoleAssignmentRepository groupRoleAssignmentRepository) {
		this.manageScopeUseCase = Objects.requireNonNull(manageScopeUseCase);
		this.groupRepository = Objects.requireNonNull(groupRepository);
		this.roleRepository = Objects.requireNonNull(roleRepository);
		this.userRepository = Objects.requireNonNull(userRepository);
		this.userIdentityRepository = Objects.requireNonNull(userIdentityRepository);
		this.userGroupMembershipRepository = Objects.requireNonNull(userGroupMembershipRepository);
		this.userRoleAssignmentRepository = Objects.requireNonNull(userRoleAssignmentRepository);
		this.groupRoleAssignmentRepository = Objects.requireNonNull(groupRoleAssignmentRepository);
	}

	@PostMapping("/scopes")
	public ResponseEntity<TestScopeResponse> createScope(@RequestBody CreateScopeRequest request) {
		TenantId tenantId = new TenantId(request.tenantId());
		OperationContext context = OperationContext.system();

		CreateScopeNodeCommand command = request.parentId() != null
				? CreateScopeNodeCommand.child(tenantId, new ScopeNodeId(request.parentId()), request.code(), request.name())
				: CreateScopeNodeCommand.root(tenantId, request.code(), request.name());

		ScopeNode created = manageScopeUseCase.createScopeNode(command, context);
		return ResponseEntity.created(URI.create("/api/test/admin/scopes/" + created.getId().value()))
				.body(new TestScopeResponse(created.getId().value(), created.getTenantId().value(), created.getCode(), created.getName()));
	}

	@PostMapping("/groups")
	public ResponseEntity<TestGroupResponse> createGroup(@RequestBody CreateGroupRequest request) {
		TenantId tenantId = new TenantId(request.tenantId());
		Group group = Group.create(tenantId, request.code(), request.name(), request.description(), null);
		try {
			Group saved = groupRepository.save(group);
			return ResponseEntity.created(URI.create("/api/test/admin/groups/" + saved.getId().value()))
					.body(new TestGroupResponse(saved.getId().value(), saved.getTenantId().value(), saved.getCode(), saved.getName()));
		}
		catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException("Duplicate group code for tenant: " + request.code());
		}
	}

	@PostMapping("/roles")
	public ResponseEntity<TestRoleResponse> createRole(@RequestBody CreateRoleRequest request) {
		TenantId tenantId = new TenantId(request.tenantId());
		Role role = Role.createCustom(tenantId, request.code(), request.name(), request.description(), request.permissions());
		try {
			Role saved = roleRepository.save(role);
			return ResponseEntity.created(URI.create("/api/test/admin/roles/" + saved.getId().value()))
					.body(new TestRoleResponse(saved.getId().value(), saved.getTenantId().value(), saved.getCode(), saved.getName()));
		}
		catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException("Duplicate role code for tenant: " + request.code());
		}
	}

	@DeleteMapping("/users/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
		userRepository.delete(new UserId(userId));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/groups/{groupId}")
	public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
		groupRepository.delete(new GroupId(groupId));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/users/{userId}/cascade-status")
	public ResponseEntity<UserCascadeStatusResponse> getUserCascadeStatus(@PathVariable UUID userId) {
		UserId id = new UserId(userId);
		boolean userExists = userRepository.findById(id).isPresent();
		int identitiesCount = userIdentityRepository.findAllByUserId(id).size();
		int membershipsCount = userGroupMembershipRepository.findAllByUserId(id).size();
		int assignmentsCount = userRoleAssignmentRepository.findAllByUserId(id).size();

		return ResponseEntity.ok(new UserCascadeStatusResponse(userExists, identitiesCount, membershipsCount, assignmentsCount));
	}

	@GetMapping("/groups/{groupId}/cascade-status")
	public ResponseEntity<GroupCascadeStatusResponse> getGroupCascadeStatus(@PathVariable UUID groupId) {
		GroupId id = new GroupId(groupId);
		boolean groupExists = groupRepository.findById(id).isPresent();
		int membershipsCount = userGroupMembershipRepository.findAllByGroupId(id).size();
		int assignmentsCount = groupRoleAssignmentRepository.findAllByGroupIds(Set.of(id)).size();

		return ResponseEntity.ok(new GroupCascadeStatusResponse(groupExists, membershipsCount, assignmentsCount));
	}

	public record CreateScopeRequest(UUID tenantId, UUID parentId, String code, String name) {}

	public record TestScopeResponse(UUID id, UUID tenantId, String code, String name) {}

	public record CreateGroupRequest(UUID tenantId, String code, String name, String description) {}

	public record TestGroupResponse(UUID id, UUID tenantId, String code, String name) {}

	public record CreateRoleRequest(UUID tenantId, String code, String name, String description, Set<String> permissions) {}

	public record TestRoleResponse(UUID id, UUID tenantId, String code, String name) {}

	public record UserCascadeStatusResponse(boolean userExists, int identitiesCount, int membershipsCount, int roleAssignmentsCount) {}

	public record GroupCascadeStatusResponse(boolean groupExists, int membershipsCount, int assignmentsCount) {}
}
