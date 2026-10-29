package io.github.edmaputra.iam.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.application.port.in.EvaluatePolicyUseCase;
import io.github.edmaputra.iam.domain.policy.DecisionEffect;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyCombiningAlgorithm;
import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;
import io.github.edmaputra.iam.domain.repository.PolicyRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Pure orchestration domain service evaluating access requests against active ABAC policies
 * and resolving the final authorization decision via pluggable combining algorithms.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public class DynamicPolicyEvaluationService implements EvaluatePolicyUseCase {

	private final PolicyRepository policyRepository;
	private final PolicyCombiningAlgorithm combiningAlgorithm;

	public DynamicPolicyEvaluationService(PolicyRepository policyRepository, PolicyCombiningAlgorithm combiningAlgorithm) {
		this.policyRepository = Objects.requireNonNull(policyRepository, "PolicyRepository must not be null.");
		this.combiningAlgorithm = combiningAlgorithm != null ? combiningAlgorithm : PolicyCombiningAlgorithm.DENY_OVERRIDES;
	}

	public DynamicPolicyEvaluationService(PolicyRepository policyRepository) {
		this(policyRepository, PolicyCombiningAlgorithm.DENY_OVERRIDES);
	}

	@Override
	public PolicyDecision evaluate(PolicyEvaluationContext context) {
		Objects.requireNonNull(context, "EvaluationContext must not be null.");

		// Platform SuperAdmin bypass check
		if (isPlatformSuperAdmin(context)) {
			return PolicyDecision.permit("Platform SuperAdmin has unrestricted access.", List.of("PLATFORM_SUPERADMIN_BYPASS"));
		}

		TenantId tenantId = context.subject().tenantId() != null ? new TenantId(context.subject().tenantId()) : null;
		List<Policy> activePolicies = policyRepository.findAllActive(tenantId);

		if (activePolicies == null || activePolicies.isEmpty()) {
			return PolicyDecision.notApplicable("No active policies configured for tenant.");
		}

		// Sort by priority descending (higher number = higher precedence)
		List<Policy> sortedPolicies = activePolicies.stream()
				.sorted(Comparator.comparingInt(Policy::priority).reversed())
				.toList();

		return switch (combiningAlgorithm) {
			case FIRST_APPLICABLE -> evaluateFirstApplicable(sortedPolicies, context);
			case PERMIT_OVERRIDES -> evaluatePermitOverrides(sortedPolicies, context);
			case DENY_OVERRIDES -> evaluateDenyOverrides(sortedPolicies, context);
		};
	}

	private PolicyDecision evaluateFirstApplicable(List<Policy> policies, PolicyEvaluationContext context) {
		for (Policy policy : policies) {
			if (policy.matches(context)) {
				String policyId = policy.id().toString();
				if (policy.effect() == PolicyEffect.PERMIT) {
					return PolicyDecision.permit("Access permitted by first matching policy: " + policy.name(), List.of(policyId));
				} else {
					return PolicyDecision.deny("Access denied by first matching policy: " + policy.name(), List.of(policyId));
				}
			}
		}
		return PolicyDecision.notApplicable("No matching policy applicable to action '" + context.action()
				+ "' and resource '" + context.resource().type() + "'.");
	}

	private PolicyDecision evaluateDenyOverrides(List<Policy> policies, PolicyEvaluationContext context) {
		List<Policy> matching = policies.stream()
				.filter(p -> p.matches(context))
				.toList();

		if (matching.isEmpty()) {
			return PolicyDecision.notApplicable("No matching policy applicable to action '" + context.action()
					+ "' and resource '" + context.resource().type() + "'.");
		}

		Optional<Policy> explicitDeny = matching.stream()
				.filter(p -> p.effect() == PolicyEffect.DENY)
				.findFirst();

		List<String> matchedIds = matching.stream().map(p -> p.id().toString()).toList();

		if (explicitDeny.isPresent()) {
			return PolicyDecision.deny("Access denied by policy: " + explicitDeny.get().name(), matchedIds);
		}

		List<Policy> permits = matching.stream()
				.filter(p -> p.effect() == PolicyEffect.PERMIT)
				.toList();

		if (!permits.isEmpty()) {
			return PolicyDecision.permit("Access permitted by " + permits.size() + " matching policy(ies).", matchedIds);
		}

		return PolicyDecision.deny("Default deny: no matching policy granted permit.", matchedIds);
	}

	private PolicyDecision evaluatePermitOverrides(List<Policy> policies, PolicyEvaluationContext context) {
		List<Policy> matching = policies.stream()
				.filter(p -> p.matches(context))
				.toList();

		if (matching.isEmpty()) {
			return PolicyDecision.notApplicable("No matching policy applicable to action '" + context.action()
					+ "' and resource '" + context.resource().type() + "'.");
		}

		List<String> matchedIds = matching.stream().map(p -> p.id().toString()).toList();

		Optional<Policy> explicitPermit = matching.stream()
				.filter(p -> p.effect() == PolicyEffect.PERMIT)
				.findFirst();

		if (explicitPermit.isPresent()) {
			return PolicyDecision.permit("Access permitted by policy: " + explicitPermit.get().name(), matchedIds);
		}

		return PolicyDecision.deny("Access denied: no policy granted permit.", matchedIds);
	}

	private static boolean isPlatformSuperAdmin(PolicyEvaluationContext context) {
		var subject = context.subject();
		if (subject.roles().contains("ROLE_PLATFORM_SUPER_ADMIN") || subject.roles().contains("PLATFORM_SUPERADMIN")) {
			return true;
		}
		Object superAdminAttr = subject.attributes().get("platformSuperAdmin");
		return Boolean.TRUE.equals(superAdminAttr);
	}
}
