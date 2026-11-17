package io.github.edmaputra.iam.domain.exception;

import io.github.edmaputra.iam.domain.policy.PolicyId;

/**
 * Thrown when an ABAC policy cannot be found.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public class PolicyNotFoundException extends RuntimeException {

	public PolicyNotFoundException(PolicyId policyId) {
		super("Policy not found with id: " + policyId);
	}

	public PolicyNotFoundException(String name) {
		super("Policy not found with name: " + name);
	}
}
