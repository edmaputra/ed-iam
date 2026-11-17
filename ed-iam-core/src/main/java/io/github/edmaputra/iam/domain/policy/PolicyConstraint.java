package io.github.edmaputra.iam.domain.policy;

/**
 * Strategy interface representing a contextual or attribute-based constraint within an ABAC policy.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public interface PolicyConstraint {

	/**
	 * Identifier type of the constraint (e.g., "TIME_WINDOW", "NETWORK_CIDR", "DEVICE_POSTURE", "ATTRIBUTE").
	 *
	 * @return constraint type string
	 */
	String type();

	/**
	 * Evaluates whether this constraint is satisfied by the given evaluation context.
	 *
	 * @param context the policy evaluation context containing subject, resource, action, and environment
	 * @return true if the constraint is satisfied, false otherwise
	 */
	boolean evaluate(PolicyEvaluationContext context);
}
