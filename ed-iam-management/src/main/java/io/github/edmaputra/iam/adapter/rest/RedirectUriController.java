package io.github.edmaputra.iam.adapter.rest;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.AddRedirectUriRequest;
import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.RedirectUriPolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.RedirectUriDtos.UpdateRedirectUriPolicyRequest;
import io.github.edmaputra.iam.application.port.in.ManageRedirectUriUseCase;
import io.github.edmaputra.iam.application.port.in.UpdateRedirectUriPolicyCommand;
import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller for retrieving, updating, and managing permitted redirect URIs at runtime.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@RestController
@RequestMapping({"/api/v1/redirect-uris", "/api/v1/allowed-redirect-uris"})
@RequiredArgsConstructor
public class RedirectUriController {

	private final ManageRedirectUriUseCase manageRedirectUriUseCase;

	@GetMapping
	@RequirePermission("iam:redirect-uri:read")
	public ResponseEntity<RedirectUriPolicyResponse> getPolicy(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;
		RedirectUriPolicy policy = manageRedirectUriUseCase.getPolicy(tenantId);
		return ResponseEntity.ok(RedirectUriPolicyResponse.fromDomain(tenantId, policy));
	}

	@PutMapping
	@RequirePermission("iam:redirect-uri:update")
	public ResponseEntity<RedirectUriPolicyResponse> updatePolicy(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody UpdateRedirectUriPolicyRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		RedirectUriPolicy updated = manageRedirectUriUseCase.updatePolicy(
				new UpdateRedirectUriPolicyCommand(tenantId, request.toDomain()));

		return ResponseEntity.ok(RedirectUriPolicyResponse.fromDomain(tenantId, updated));
	}

	@PostMapping
	@RequirePermission("iam:redirect-uri:update")
	public ResponseEntity<RedirectUriPolicyResponse> addUri(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody AddRedirectUriRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		RedirectUriPolicy updated = manageRedirectUriUseCase.addUri(tenantId, request.uri());

		return ResponseEntity.ok(RedirectUriPolicyResponse.fromDomain(tenantId, updated));
	}

	@DeleteMapping
	@RequirePermission("iam:redirect-uri:update")
	public ResponseEntity<RedirectUriPolicyResponse> removeUri(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@RequestParam("uri") String uri) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		RedirectUriPolicy updated = manageRedirectUriUseCase.removeUri(tenantId, uri);

		return ResponseEntity.ok(RedirectUriPolicyResponse.fromDomain(tenantId, updated));
	}
}
