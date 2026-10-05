package io.github.edmaputra.iam.adapter.rest;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.adapter.rest.dto.PasswordPolicyDtos.PasswordPolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PasswordPolicyDtos.UpdatePasswordPolicyRequest;
import io.github.edmaputra.iam.application.port.in.ManagePasswordPolicyUseCase;
import io.github.edmaputra.iam.application.port.in.UpdatePasswordPolicyCommand;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller for retrieving and updating runtime password policies.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@RestController
@RequestMapping("/api/v1/password-policy")
@RequiredArgsConstructor
public class PasswordPolicyController {

	private final ManagePasswordPolicyUseCase managePasswordPolicyUseCase;

	@GetMapping
	@RequirePermission("iam:password-policy:read")
	public ResponseEntity<PasswordPolicyResponse> getPolicy(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;
		PasswordPolicy policy = managePasswordPolicyUseCase.getPolicy(tenantId);
		return ResponseEntity.ok(PasswordPolicyResponse.fromDomain(tenantId, policy));
	}

	@PutMapping
	@RequirePermission("iam:password-policy:update")
	public ResponseEntity<PasswordPolicyResponse> updatePolicy(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody UpdatePasswordPolicyRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		PasswordPolicy currentPolicy = managePasswordPolicyUseCase.getPolicy(tenantId);
		PasswordPolicy newPolicy = request.toDomain(currentPolicy);

		PasswordPolicy updated = managePasswordPolicyUseCase.updatePolicy(
				new UpdatePasswordPolicyCommand(tenantId, newPolicy));

		return ResponseEntity.ok(PasswordPolicyResponse.fromDomain(tenantId, updated));
	}
}
