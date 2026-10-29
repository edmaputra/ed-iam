package io.github.edmaputra.iam.application.port.in;

import java.util.List;
import java.util.Objects;

import io.github.edmaputra.iam.domain.policy.PolicyConstraint;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Commands for creating and updating ABAC dynamic policies.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public final class PolicyCommands {

	private PolicyCommands() {
	}

	/**
	 * Command to create a new ABAC dynamic policy.
	 */
	public record CreatePolicyCommand(
			TenantId tenantId,
			String name,
			String description,
			PolicyEffect effect,
			int priority,
			boolean enabled,
			PolicyTarget target,
			List<PolicyConstraint> constraints) {

		public CreatePolicyCommand {
			Objects.requireNonNull(name, "Policy name must not be null.");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Policy name must not be blank.");
			}
			name = name.trim();
			description = description != null ? description.trim() : null;
			effect = effect != null ? effect : PolicyEffect.PERMIT;
			target = target != null ? target : PolicyTarget.all();
			constraints = constraints == null ? List.of() : List.copyOf(constraints);
		}
	}

	/**
	 * Command to update an existing ABAC dynamic policy.
	 */
	public record UpdatePolicyCommand(
			PolicyId id,
			TenantId tenantId,
			String name,
			String description,
			PolicyEffect effect,
			int priority,
			boolean enabled,
			PolicyTarget target,
			List<PolicyConstraint> constraints) {

		public UpdatePolicyCommand {
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
		}
	}
}
