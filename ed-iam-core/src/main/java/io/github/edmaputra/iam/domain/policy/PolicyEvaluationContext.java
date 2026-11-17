package io.github.edmaputra.iam.domain.policy;

import java.util.Objects;

/**
 * Composite evaluation context provided to the ABAC policy engine.
 * Represents the complete tuple of Subject, Action, Resource, and Environment (SARE).
 *
 * @param subject security subject (actor) attributes
 * @param action operation or action code being attempted (e.g. "read", "patient:update")
 * @param resource target resource attributes
 * @param environment contextual and environmental attributes
 * @author edmaputra
 * @since 0.14.0
 */
public record PolicyEvaluationContext(
		SubjectAttributes subject,
		String action,
		ResourceAttributes resource,
		EnvironmentAttributes environment) {

	public PolicyEvaluationContext {
		subject = subject != null ? subject : SubjectAttributes.anonymous();
		Objects.requireNonNull(action, "Action must not be null.");
		if (action.isBlank()) {
			throw new IllegalArgumentException("Action must not be blank.");
		}
		action = action.trim();
		Objects.requireNonNull(resource, "Resource must not be null.");
		environment = environment != null ? environment : new EnvironmentAttributes(null, null, null, null, null);
	}
}
