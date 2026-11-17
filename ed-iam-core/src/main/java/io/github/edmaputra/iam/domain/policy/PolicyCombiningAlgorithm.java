package io.github.edmaputra.iam.domain.policy;

/**
 * Combining algorithm used to resolve multiple policy evaluation results into a single authorization decision.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public enum PolicyCombiningAlgorithm {

	/**
	 * Any matching policy with a {@link PolicyEffect#DENY} effect overrides any permit.
	 * If at least one policy permits and none deny, the decision is {@link DecisionEffect#PERMIT}.
	 * If no policies match, the result is {@link DecisionEffect#NOT_APPLICABLE}.
	 */
	DENY_OVERRIDES,

	/**
	 * Any matching policy with a {@link PolicyEffect#PERMIT} effect overrides any deny.
	 */
	PERMIT_OVERRIDES,

	/**
	 * Policies are evaluated strictly in descending order of priority.
	 * The first policy that matches target and constraints dictates the decision.
	 */
	FIRST_APPLICABLE
}
