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

import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.CreatePermissionRequest;
import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.PermissionResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PermissionManagementDtos.UpdatePermissionRequest;
import io.github.edmaputra.iam.application.port.in.ManagePermissionUseCase;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.CreatePermissionCommand;
import io.github.edmaputra.iam.application.port.in.PermissionCommands.UpdatePermissionCommand;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller for retrieving, creating, updating, and managing permissions in the IAM catalog.
 *
 * @author edmaputra
 * @since 0.10.0
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

	private final ManagePermissionUseCase managePermissionUseCase;

	@PostMapping
	@RequirePermission("iam:permission:create")
	public ResponseEntity<PermissionResponse> createPermission(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody CreatePermissionRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		CreatePermissionCommand command = new CreatePermissionCommand(
				tenantId,
				request.code(),
				request.name(),
				request.description(),
				request.category());

		Permission created = managePermissionUseCase.createPermission(command);
		return ResponseEntity.created(URI.create("/api/v1/permissions/" + created.id().value()))
				.body(PermissionResponse.fromDomain(created));
	}

	@GetMapping("/{id}")
	@RequirePermission("iam:permission:read")
	public ResponseEntity<PermissionResponse> getPermissionById(@PathVariable UUID id) {
		Permission permission = managePermissionUseCase.getPermissionById(new PermissionId(id));
		return ResponseEntity.ok(PermissionResponse.fromDomain(permission));
	}

	@GetMapping("/code/{code}")
	@RequirePermission("iam:permission:read")
	public ResponseEntity<PermissionResponse> getPermissionByCode(
			@PathVariable String code,
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		Permission permission = managePermissionUseCase.getPermissionByCode(tenantId, code);
		return ResponseEntity.ok(PermissionResponse.fromDomain(permission));
	}

	@GetMapping
	@RequirePermission("iam:permission:read")
	public ResponseEntity<List<PermissionResponse>> getPermissions(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@RequestParam(value = "category", required = false) String category) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		List<PermissionResponse> responses = managePermissionUseCase.getPermissions(tenantId, category)
				.stream()
				.map(PermissionResponse::fromDomain)
				.toList();

		return ResponseEntity.ok(responses);
	}

	@PutMapping("/{id}")
	@RequirePermission("iam:permission:update")
	public ResponseEntity<PermissionResponse> updatePermission(
			@PathVariable UUID id,
			@Valid @RequestBody UpdatePermissionRequest request) {
		UpdatePermissionCommand command = new UpdatePermissionCommand(
				new PermissionId(id),
				request.name(),
				request.description(),
				request.category());

		Permission updated = managePermissionUseCase.updatePermission(command);
		return ResponseEntity.ok(PermissionResponse.fromDomain(updated));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("iam:permission:delete")
	public ResponseEntity<Void> deletePermission(@PathVariable UUID id) {
		managePermissionUseCase.deletePermission(new PermissionId(id));
		return ResponseEntity.noContent().build();
	}
}
