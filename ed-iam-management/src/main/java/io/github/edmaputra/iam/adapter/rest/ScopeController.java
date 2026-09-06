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

import io.github.edmaputra.iam.adapter.rest.dto.ScopeManagementDtos.CreateScopeRequest;
import io.github.edmaputra.iam.adapter.rest.dto.ScopeManagementDtos.MoveScopeRequest;
import io.github.edmaputra.iam.adapter.rest.dto.ScopeManagementDtos.ScopeNodeResponse;
import io.github.edmaputra.iam.adapter.rest.dto.ScopeManagementDtos.UpdateScopeRequest;
import io.github.edmaputra.iam.application.model.ScopeTreeNode;
import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.DeleteScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import io.github.edmaputra.iam.adapter.rest.support.TenantResolutionHelper;

/**
 * REST controller for managing hierarchical scope trees and node reparenting.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/scopes")
public class ScopeController {

	private final ManageScopeUseCase manageScopeUseCase;

	public ScopeController(ManageScopeUseCase manageScopeUseCase) {
		this.manageScopeUseCase = Objects.requireNonNull(manageScopeUseCase, "ManageScopeUseCase must not be null.");
	}

	@PostMapping
	public ResponseEntity<ScopeNodeResponse> createScope(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestBody CreateScopeRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = new TenantId(tenantUuid);
		OperationContext context = OperationContext.system();

		CreateScopeNodeCommand command = request.parentId() != null
				? CreateScopeNodeCommand.child(tenantId, new ScopeNodeId(request.parentId()), request.code(), request.name())
				: CreateScopeNodeCommand.root(tenantId, request.code(), request.name());

		ScopeNode created = manageScopeUseCase.createScopeNode(command, context);
		return ResponseEntity.created(URI.create("/api/v1/scopes/" + created.getId().value()))
				.body(ScopeNodeResponse.fromDomain(created));
	}

	@GetMapping("/tree")
	public ResponseEntity<List<ScopeTreeNode>> getScopeTree(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		List<ScopeTreeNode> tree = manageScopeUseCase.getScopeTree(new TenantId(tenantUuid));
		return ResponseEntity.ok(tree);
	}

	@GetMapping
	public ResponseEntity<List<ScopeNodeResponse>> getFlatScopeList(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		List<ScopeNodeResponse> responses = manageScopeUseCase.getFlatScopeList(new TenantId(tenantUuid))
				.stream()
				.map(ScopeNodeResponse::fromDomain)
				.collect(Collectors.toList());

		return ResponseEntity.ok(responses);
	}

	@GetMapping("/{id}")
	public ResponseEntity<ScopeNodeResponse> getScopeById(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@PathVariable UUID id) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		ScopeNode node = manageScopeUseCase.getById(new TenantId(tenantUuid), new ScopeNodeId(id));
		return ResponseEntity.ok(ScopeNodeResponse.fromDomain(node));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ScopeNodeResponse> updateScope(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@PathVariable UUID id,
			@RequestBody UpdateScopeRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		UpdateScopeNodeCommand command = new UpdateScopeNodeCommand(
				new TenantId(tenantUuid),
				new ScopeNodeId(id),
				request.code(),
				request.name());

		ScopeNode updated = manageScopeUseCase.updateMetadata(command, OperationContext.system());
		return ResponseEntity.ok(ScopeNodeResponse.fromDomain(updated));
	}

	@PostMapping("/{id}/move")
	public ResponseEntity<ScopeNodeResponse> moveScope(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@PathVariable UUID id,
			@RequestBody MoveScopeRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		MoveScopeNodeCommand command = new MoveScopeNodeCommand(
				new TenantId(tenantUuid),
				new ScopeNodeId(id),
				new ScopeNodeId(request.newParentId()));

		ScopeNode moved = manageScopeUseCase.moveNode(command, OperationContext.system());
		return ResponseEntity.ok(ScopeNodeResponse.fromDomain(moved));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteScope(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@PathVariable UUID id) {
		UUID tenantUuid = TenantResolutionHelper.resolveTenantId(headerTenantId, paramTenantId);
		DeleteScopeNodeCommand command = new DeleteScopeNodeCommand(new TenantId(tenantUuid), new ScopeNodeId(id));
		manageScopeUseCase.deleteNode(command, OperationContext.system());
		return ResponseEntity.noContent().build();
	}
}
