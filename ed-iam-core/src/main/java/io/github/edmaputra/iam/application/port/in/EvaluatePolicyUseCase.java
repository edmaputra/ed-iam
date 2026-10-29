package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.policy.PolicyDecision;
import io.github.edmaputra.iam.domain.policy.PolicyEvaluationContext;

/**
 * Primary use case driving dynamic ABAC policy evaluation for access requests.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public interface EvaluatePolicyUseCase {

	/**
	 * Evaluates an authorization request tuple (subject, action, resource, environment)
	 * against applicable policies and returns a combining-resolved decision.
	 *
	 * @param context evaluation context tuple
	 * @return policy decision (PERMIT, DENY, or NOT_APPLICABLE)
	 */
	PolicyDecision evaluate(PolicyEvaluationContext context);
}
