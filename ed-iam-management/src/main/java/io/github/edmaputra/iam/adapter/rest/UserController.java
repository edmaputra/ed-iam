package io.github.edmaputra.iam.adapter.rest;

import java.net.URI;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.GroupManagementDtos.GroupResponse;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.AssignUserRoleRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.ChangeUserStatusRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.CreateUserRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UpdateUserRequest;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UserResponse;
import io.github.edmaputra.iam.adapter.rest.dto.UserManagementDtos.UserRoleAssignmentResponse;
import io.github.edmaputra.iam.application.port.in.ManageUserUseCase;
import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.domain.model.GroupId;
import io.github.edmaputra.iam.domain.model.PageQuery;
import io.github.edmaputra.iam.domain.model.PagedResult;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserFilter;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * REST controller for managing users, lifecycles, role assignments, and group memberships.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final ManageUserUseCase manageUserUseCase;

	@PostMapping
	@RequirePermission("iam:user:create")
	public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
		CreateUserCommand command = new CreateUserCommand(
				request.email(),
				request.password(),
				request.fullName(),
				request.isPlatformSuperAdmin());

		User created = manageUserUseCase.createUser(command);
		return ResponseEntity.created(URI.create("/api/v1/users/" + created.getId().value()))
				.body(UserResponse.fromDomain(created));
	}

	@GetMapping("/{id}")
	@RequirePermission("iam:user:read")
	public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
		User user = manageUserUseCase.getUserById(new UserId(id));
		return ResponseEntity.ok(UserResponse.fromDomain(user));
	}

	@GetMapping("/lookup")
	@RequirePermission("iam:user:read")
	public ResponseEntity<UserResponse> getUserByEmail(@RequestParam("email") String email) {
		User user = manageUserUseCase.getUserByEmail(email);
		return ResponseEntity.ok(UserResponse.fromDomain(user));
	}

	@GetMapping
	@RequirePermission("iam:user:read")
	public ResponseEntity<PagedResult<UserResponse>> getUsers(
			@RequestParam(value = "search", required = false) String search,
			@RequestParam(value = "username", required = false) Set<String> usernames,
			@RequestParam(value = "name", required = false) Set<String> names,
			@RequestParam(value = "status", required = false) Set<UserStatus> statuses,
			@RequestParam(value = "role", required = false) Set<String> roles,
			@RequestParam(value = "group", required = false) Set<String> groups,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		UserFilter filter = new UserFilter(search, usernames, names, statuses, roles, groups);
		PageQuery pageQuery = PageQuery.of(page, size);
		PagedResult<User> users = manageUserUseCase.getUsers(filter, pageQuery);
		return ResponseEntity.ok(users.map(UserResponse::fromDomain));
	}

	@PutMapping("/{id}")
	@RequirePermission("iam:user:update")
	public ResponseEntity<UserResponse> updateUser(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserRequest request) {
		User updated = manageUserUseCase.updateUser(new UpdateUserCommand(new UserId(id), request.fullName()));
		return ResponseEntity.ok(UserResponse.fromDomain(updated));
	}

	@PutMapping("/{id}/status")
	@RequirePermission("iam:user:status")
	public ResponseEntity<UserResponse> changeUserStatus(
			@PathVariable UUID id,
			@Valid @RequestBody ChangeUserStatusRequest request) {
		User updated = manageUserUseCase.changeUserStatus(new ChangeUserStatusCommand(new UserId(id), request.status()));
		return ResponseEntity.ok(UserResponse.fromDomain(updated));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("iam:user:delete")
	public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
		manageUserUseCase.deleteUser(new UserId(id));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/roles")
	@RequirePermission("iam:user:assign-role")
	public ResponseEntity<UserRoleAssignmentResponse> assignRole(
			@PathVariable UUID id,
			@Valid @RequestBody AssignUserRoleRequest request) {
		ScopeNodeId scopeId = request.scopeNodeId() != null ? new ScopeNodeId(request.scopeNodeId()) : null;
		AssignUserRoleCommand command = new AssignUserRoleCommand(
				new UserId(id),
				new RoleId(request.roleId()),
				new TenantId(request.tenantId()),
				scopeId);

		UserRoleAssignment assignment = manageUserUseCase.assignRole(command);
		return ResponseEntity.created(URI.create("/api/v1/users/" + id + "/roles/" + assignment.getId().value()))
				.body(UserRoleAssignmentResponse.fromDomain(assignment));
	}

	@DeleteMapping("/{id}/roles/{assignmentId}")
	@RequirePermission("iam:user:assign-role")
	public ResponseEntity<Void> revokeRole(
			@PathVariable UUID id,
			@PathVariable UUID assignmentId) {
		manageUserUseCase.revokeRole(assignmentId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/roles")
	@RequirePermission("iam:user:read")
	public ResponseEntity<List<UserRoleAssignmentResponse>> getRoleAssignments(@PathVariable UUID id) {
		List<UserRoleAssignmentResponse> responses = manageUserUseCase.getRoleAssignments(new UserId(id))
				.stream()
				.map(UserRoleAssignmentResponse::fromDomain)
				.toList();
		return ResponseEntity.ok(responses);
	}

	@PostMapping("/{id}/groups/{groupId}")
	@RequirePermission("iam:user:manage-membership")
	public ResponseEntity<Void> addUserToGroup(
			@PathVariable UUID id,
			@PathVariable UUID groupId) {
		manageUserUseCase.addUserToGroup(new GroupId(groupId), new UserId(id));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/groups/{groupId}")
	@RequirePermission("iam:user:manage-membership")
	public ResponseEntity<Void> removeUserFromGroup(
			@PathVariable UUID id,
			@PathVariable UUID groupId) {
		manageUserUseCase.removeUserFromGroup(new GroupId(groupId), new UserId(id));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/groups")
	@RequirePermission("iam:user:read")
	public ResponseEntity<List<GroupResponse>> getUserGroups(@PathVariable UUID id) {
		List<GroupResponse> responses = manageUserUseCase.getUserGroups(new UserId(id))
				.stream()
				.map(GroupResponse::fromDomain)
				.toList();
		return ResponseEntity.ok(responses);
	}
}
