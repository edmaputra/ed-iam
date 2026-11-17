package io.github.edmaputra.iam.domain.policy;

/**
 * Comparison operator used for attribute evaluation in dynamic ABAC policies.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public enum AttributeOperator {
	EQUALS,
	NOT_EQUALS,
	CONTAINS,
	IN,
	GREATER_THAN,
	GREATER_THAN_OR_EQUAL,
	LESS_THAN,
	LESS_THAN_OR_EQUAL,
	EXISTS
}
