package io.github.edmaputra.iam.domain.policy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Domain entity representing a dynamic Attribute-Based Access Control (ABAC) policy.
 *
 * <p>Policies define fine-grained authorization rules combining subject, resource, action,
 * and contextual constraints (time windows, IP CIDR subnets, device posture, attribute conditions).
 *
 * @param id unique policy identifier (UUIDv7)
 * @param tenantId owning tenant UUID (null for global platform policies)
 * @param name human-readable policy identifier
 * @param description optional explanation of what this policy enforces
 * @param effect authorization effect produced when this policy matches (PERMIT or DENY)
 * @param priority numerical priority for precedence ordering (higher values take precedence)
 * @param enabled whether this policy is currently active
 * @param target target actions, resource types, and roles this policy applies to
 * @param constraints list of contextual and attribute conditions that must all be satisfied
 * @param createdAt creation timestamp
 * @param updatedAt last modified timestamp
 * @author edmaputra
 * @since 0.14.0
 */
public record Policy(
		PolicyId id,
		TenantId tenantId,
		String name,
		String description,
		PolicyEffect effect,
		int priority,
		boolean enabled,
		PolicyTarget target,
		List<PolicyConstraint> constraints,
		Instant createdAt,
		Instant updatedAt) {

	public Policy {
		Objects.requireNonNull(id, "Policy ID must not be null.");
		Objects.requireNonNull(name, "Policy name must not be null.");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Policy name must not be blank.");
		}
		name = name.trim();
		description = description != null ? description.trim() : null;
		effect = effect != null ? effect : PolicyEffect.PERMIT;
		target = target != null ? target : PolicyTarget.all();
		constraints = constraints == null ? List.of() : List.copyOf(constraints);
		createdAt = createdAt != null ? createdAt : Instant.now();
		updatedAt = updatedAt != null ? updatedAt : Instant.now();
	}

	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	/**
	 * Checks whether this policy applies to the given evaluation context and all its constraints pass.
	 *
	 * @param context evaluation context
	 * @return true if this policy applies and all constraints pass, false otherwise
	 */
	public boolean matches(PolicyEvaluationContext context) {
		if (!enabled) {
			return false;
		}

		if (!target.matches(context.action(), context.resource().type(), context.subject().roles())) {
			return false;
		}

		for (PolicyConstraint constraint : constraints) {
			if (!constraint.evaluate(context)) {
				return false;
			}
		}

		return true;
	}
}
