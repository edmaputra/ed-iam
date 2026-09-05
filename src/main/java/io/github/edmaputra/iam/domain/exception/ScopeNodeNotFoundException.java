package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.model.ScopeNodeId;

/**
 * Thrown when a scope node cannot be found in the tenant hierarchy.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class ScopeNodeNotFoundException extends RuntimeException {

	/**
	 * Constructs the exception with a scope node ID.
	 *
	 * @param id the missing scope node ID
	 */
	public ScopeNodeNotFoundException(ScopeNodeId id) {
		super("Scope node not found with id: " + id);
	}

	/**
	 * Constructs the exception with a scope path.
	 *
	 * @param path the missing scope path
	 */
	public ScopeNodeNotFoundException(String path) {
		super("Scope node not found with path: " + path);
	}
}
