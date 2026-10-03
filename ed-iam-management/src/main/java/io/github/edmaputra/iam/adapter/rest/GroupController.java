package io.github.edmaputra.iam.adapter.rest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.AssignGroupRoleRequest;
import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.CreateGroupRequest;
import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.GroupResponse;
import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.GroupRoleAssignmentResponse;
import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.UpdateGroupRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UserResponse;
import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.application.port.in.ManageGroupUseCase;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.GroupRoleAssignment;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller for managing user groups, memberships, and group role assignments.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

	private final ManageGroupUseCase manageGroupUseCase;

	@PostMapping
	@RequirePermission("iam:group:create")
	public ResponseEntity<GroupResponse> createGroup(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody CreateGroupRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, request.tenantId());

		CreateGroupCommand command = new CreateGroupCommand(
				new TenantId(tenantUuid),
				request.code(),
				request.name(),
				request.description(),
				request.externalIdpGroupName());

		Group created = manageGroupUseCase.createGroup(command);
		return ResponseEntity.created(URI.create("/api/v1/groups/" + created.getId().value()))
				.body(GroupResponse.fromDomain(created));
	}

	@GetMapping("/{id}")
	@RequirePermission("iam:group:read")
	public ResponseEntity<GroupResponse> getGroupById(@PathVariable UUID id) {
		Group group = manageGroupUseCase.getGroupById(new GroupId(id));
		return ResponseEntity.ok(GroupResponse.fromDomain(group));
	}

	@GetMapping
	@RequirePermission("iam:group:read")
	public ResponseEntity<List<GroupResponse>> getGroups(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);

		List<GroupResponse> responses = manageGroupUseCase.getGroupsByTenant(new TenantId(tenantUuid))
				.stream()
				.map(GroupResponse::fromDomain)
				.toList();

		return ResponseEntity.ok(responses);
	}

	@PutMapping("/{id}")
	@RequirePermission("iam:group:update")
	public ResponseEntity<GroupResponse> updateGroup(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateGroupRequest request) {
		UpdateGroupCommand command = new UpdateGroupCommand(
				new GroupId(id),
				request.name(),
				request.description(),
				request.externalIdpGroupName());

		Group updated = manageGroupUseCase.updateGroup(command);
		return ResponseEntity.ok(GroupResponse.fromDomain(updated));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("iam:group:delete")
	public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
		manageGroupUseCase.deleteGroup(new GroupId(id));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/roles")
	@RequirePermission("iam:group:assign-role")
	public ResponseEntity<GroupRoleAssignmentResponse> assignRole(
			@PathVariable UUID id,
			@Valid @RequestBody AssignGroupRoleRequest request) {
		ScopeNodeId scopeId = request.scopeNodeId() != null ? new ScopeNodeId(request.scopeNodeId()) : null;
		AssignGroupRoleCommand command = new AssignGroupRoleCommand(
				new GroupId(id),
				new RoleId(request.roleId()),
				new TenantId(request.tenantId()),
				scopeId);

		GroupRoleAssignment assignment = manageGroupUseCase.assignRole(command);
		return ResponseEntity.created(URI.create("/api/v1/groups/" + id + "/roles/" + assignment.getId().value()))
				.body(GroupRoleAssignmentResponse.fromDomain(assignment));
	}

	@DeleteMapping("/{id}/roles/{assignmentId}")
	@RequirePermission("iam:group:assign-role")
	public ResponseEntity<Void> revokeRole(
			@PathVariable UUID id,
			@PathVariable UUID assignmentId) {
		manageGroupUseCase.revokeRole(assignmentId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/roles")
	@RequirePermission("iam:group:read")
	public ResponseEntity<List<GroupRoleAssignmentResponse>> getRoleAssignments(@PathVariable UUID id) {
		List<GroupRoleAssignmentResponse> responses = manageGroupUseCase.getRoleAssignments(new GroupId(id))
				.stream()
				.map(GroupRoleAssignmentResponse::fromDomain)
				.toList();
		return ResponseEntity.ok(responses);
	}

	@GetMapping("/{id}/members")
	@RequirePermission("iam:group:read")
	public ResponseEntity<List<UserResponse>> getGroupMembers(@PathVariable UUID id) {
		List<UserResponse> responses = manageGroupUseCase.getGroupMembers(new GroupId(id))
				.stream()
				.map(UserResponse::fromDomain)
				.toList();
		return ResponseEntity.ok(responses);
	}
}
