package io.github.edmaputra.iam.domain.policy;

/**
 * Attribute target entity source within an ABAC policy evaluation context.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public enum AttributeSource {
	SUBJECT,
	RESOURCE,
	ENVIRONMENT
}
