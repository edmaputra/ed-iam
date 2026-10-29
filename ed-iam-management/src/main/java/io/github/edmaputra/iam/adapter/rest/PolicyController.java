package io.github.edmaputra.iam.adapter.rest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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

import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.CreatePolicyRequest;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyConstraintDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyEvaluationRequestDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyEvaluationResponseDto;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.PolicyResponse;
import io.github.edmaputra.iam.adapter.rest.dto.PolicyManagementDtos.UpdatePolicyRequest;
import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.application.port.in.ManagePolicyUseCase;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.CreatePolicyCommand;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.UpdatePolicyCommand;
import io.github.edmaputra.iam.domain.exception.PolicyNotFoundException;
import io.github.edmaputra.iam.domain.policy.DevicePosture;
import io.github.edmaputra.iam.domain.policy.EnvironmentAttributes;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyConstraint;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.policy.ResourceAttributes;
import io.github.edmaputra.iam.domain.policy.SubjectAttributes;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.annotation.RequirePermission;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantResolutionHelper;

/**
 * REST controller for provisioning, managing, and evaluating ABAC dynamic policies.
 *
 * @author edmaputra
 * @since 0.14.0
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

	private final ManagePolicyUseCase managePolicyUseCase;
	private final CurrentActorProvider currentActorProvider;
	private final ObjectProvider<EvaluatePolicyUseCase> policyEvaluatorProvider;

	@PostMapping
	@RequirePermission("iam:policy:create")
	public ResponseEntity<PolicyResponse> createPolicy(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@Valid @RequestBody CreatePolicyRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, request.tenantId());
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		PolicyTarget target = request.target() != null ? request.target().toDomain() : PolicyTarget.all();
		List<PolicyConstraint> constraints = request.constraints() != null
				? request.constraints().stream().map(PolicyConstraintDto::toDomain).toList()
				: List.of();

		CreatePolicyCommand command = new CreatePolicyCommand(
				tenantId,
				request.name(),
				request.description(),
				request.resolveEffect(),
				request.resolvePriority(),
				request.resolveEnabled(),
				target,
				constraints
		);

		Policy created = managePolicyUseCase.createPolicy(command);
		return ResponseEntity.created(URI.create("/api/v1/policies/" + created.id().value()))
				.body(PolicyResponse.fromDomain(created));
	}

	@GetMapping("/{id}")
	@RequirePermission("iam:policy:read")
	public ResponseEntity<PolicyResponse> getPolicyById(@PathVariable UUID id) {
		Policy policy = managePolicyUseCase.getPolicy(new PolicyId(id))
				.orElseThrow(() -> new PolicyNotFoundException(new PolicyId(id)));
		return ResponseEntity.ok(PolicyResponse.fromDomain(policy));
	}

	@GetMapping
	@RequirePermission("iam:policy:read")
	public ResponseEntity<List<PolicyResponse>> getPolicies(
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		List<PolicyResponse> responses = managePolicyUseCase.listPolicies(tenantId)
				.stream()
				.map(PolicyResponse::fromDomain)
				.toList();

		return ResponseEntity.ok(responses);
	}

	@PutMapping("/{id}")
	@RequirePermission("iam:policy:update")
	public ResponseEntity<PolicyResponse> updatePolicy(
			@PathVariable UUID id,
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
			@RequestParam(value = "tenantId", required = false) UUID paramTenantId,
			@Valid @RequestBody UpdatePolicyRequest request) {
		UUID tenantUuid = TenantResolutionHelper.resolveOptionalTenantId(headerTenantId, paramTenantId);
		TenantId tenantId = tenantUuid != null ? new TenantId(tenantUuid) : null;

		PolicyTarget target = request.target() != null ? request.target().toDomain() : PolicyTarget.all();
		List<PolicyConstraint> constraints = request.constraints() != null
				? request.constraints().stream().map(PolicyConstraintDto::toDomain).toList()
				: List.of();

		UpdatePolicyCommand command = new UpdatePolicyCommand(
				new PolicyId(id),
				tenantId,
				request.name(),
				request.description(),
				request.resolveEffect(),
				request.resolvePriority(),
				request.resolveEnabled(),
				target,
				constraints
		);

		Policy updated = managePolicyUseCase.updatePolicy(command);
		return ResponseEntity.ok(PolicyResponse.fromDomain(updated));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("iam:policy:delete")
	public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
		managePolicyUseCase.deletePolicy(new PolicyId(id));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/evaluate")
	@RequirePermission("iam:policy:evaluate")
	public ResponseEntity<PolicyEvaluationResponseDto> evaluatePolicy(
			HttpServletRequest servletRequest,
			@Valid @RequestBody PolicyEvaluationRequestDto request) {
		EvaluatePolicyUseCase evaluator = policyEvaluatorProvider != null ? policyEvaluatorProvider.getIfAvailable() : null;
		if (evaluator == null) {
			return ResponseEntity.ok(PolicyEvaluationResponseDto.fromDomain(
					PolicyDecision.deny("No ABAC Policy Evaluation Engine configured")
			));
		}

		Optional<CurrentActor> actorOpt = currentActorProvider.currentActor();
		if (actorOpt.isEmpty()) {
			return ResponseEntity.ok(PolicyEvaluationResponseDto.fromDomain(
					PolicyDecision.deny("Unauthenticated caller")
			));
		}

		CurrentActor actor = actorOpt.get();
		SubjectAttributes subject = new SubjectAttributes(
				actor.userId(),
				actor.tenantId(),
				actor.email(),
				actor.roles(),
				actor.groups(),
				actor.permissions(),
				Map.of()
		);

		ResourceAttributes resource = new ResourceAttributes(
				request.resourceId(),
				request.resourceType(),
				request.resourceTenantId(),
				request.resourceOwnerId(),
				request.resourceAttributes() != null ? request.resourceAttributes() : Map.of()
		);

		String clientIp = extractClientIp(servletRequest);
		String userAgent = servletRequest.getHeader("User-Agent");
		DevicePosture posture = extractDevicePosture(servletRequest);
		Map<String, Object> envAttrs = request.environmentAttributes() != null ? request.environmentAttributes() : Map.of();

		EnvironmentAttributes env = new EnvironmentAttributes(clientIp, Instant.now(), userAgent, posture, envAttrs);
		PolicyEvaluationContext context = new PolicyEvaluationContext(subject, request.action(), resource, env);

		PolicyDecision decision = evaluator.evaluate(context);
		return ResponseEntity.ok(PolicyEvaluationResponseDto.fromDomain(decision));
	}

	private static String extractClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
		}
		return request.getRemoteAddr();
	}

	private static DevicePosture extractDevicePosture(HttpServletRequest request) {
		String managedHeader = request.getHeader("X-Device-Managed");
		boolean managed = managedHeader != null && Boolean.parseBoolean(managedHeader.trim());
		String trustLevel = request.getHeader("X-Device-Trust-Level");
		String platform = request.getHeader("X-Device-Platform");
		return new DevicePosture(managed, trustLevel, platform, Map.of());
	}
}
