package io.github.edmaputra.iam.domain.policy;

/**
 * Final decision outcome from policy evaluation.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public enum DecisionEffect {
	PERMIT,
	DENY,
	NOT_APPLICABLE
}
