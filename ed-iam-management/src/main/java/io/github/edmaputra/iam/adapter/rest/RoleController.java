package io.github.edmaputra.iam.adapter.rest;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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

import io.github.edmaputra.iam.adapter.rest.dto.RoleManagementDtos.CreateRoleRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RoleManagementDtos.RoleResponse;
import io.github.edmaputra.iam.adapter.rest.dto.RoleManagementDtos.UpdateRoleRequest;
import io.github.edmaputra.iam.application.port.in.ManageRoleUseCase;
import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import io.github.edmaputra.iam.adapter.rest.support.TenantResolutionHelper;

/**
 * REST controller for managing custom roles and permissions.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

	private final ManageRoleUseCase manageRoleUseCase;

	public RoleController(ManageRoleUseCase manageRoleUseCase) {
		this.manageRoleUseCase = Objects.requireNonNull(manageRoleUseCase, "ManageRoleUseCase must not be null.");
	}

	@PostMapping
	public ResponseEntity<RoleResponse> createRole(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestBody CreateRoleRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, request.tenantId());

		CreateRoleCommand command = new CreateRoleCommand(
				new TenantId(tenantUuid),
				request.code(),
				request.name(),
				request.description(),
				request.permissions());

		Role created = manageRoleUseCase.createRole(command);
		return ResponseEntity.created(URI.create("/api/v1/roles/" + created.getId().value()))
				.body(RoleResponse.fromDomain(created));
	}

	@GetMapping("/{id}")
	public ResponseEntity<RoleResponse> getRoleById(@PathVariable UUID id) {
		Role role = manageRoleUseCase.getRoleById(new RoleId(id));
		return ResponseEntity.ok(RoleResponse.fromDomain(role));
	}

	@GetMapping
	public ResponseEntity<List<RoleResponse>> getRoles(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);

		List<RoleResponse> responses = manageRoleUseCase.getRolesByTenant(new TenantId(tenantUuid))
				.stream()
				.map(RoleResponse::fromDomain)
				.collect(Collectors.toList());

		return ResponseEntity.ok(responses);
	}

	@PutMapping("/{id}")
	public ResponseEntity<RoleResponse> updateRole(
			@PathVariable UUID id,
			@RequestBody UpdateRoleRequest request) {
		UpdateRoleCommand command = new UpdateRoleCommand(
				new RoleId(id),
				request.name(),
				request.description(),
				request.permissions());

		Role updated = manageRoleUseCase.updateRole(command);
		return ResponseEntity.ok(RoleResponse.fromDomain(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
		manageRoleUseCase.deleteRole(new RoleId(id));
		return ResponseEntity.noContent().build();
	}
}
