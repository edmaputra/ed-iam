package io.github.edmaputra.iam.domain.security.annotation;

/**
 * Logical operator for evaluating multiple permissions in {@link RequirePermission}.
 *
 * @author edmaputra
 * @since 0.1.0
 */
public enum Logical {

	/**
	 * Requires all specified permissions to be granted.
	 */
	AND,

	/**
	 * Requires at least one of the specified permissions to be granted.
	 */
	OR
}
