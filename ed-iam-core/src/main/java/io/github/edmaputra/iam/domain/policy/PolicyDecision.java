package io.github.edmaputra.iam.domain.policy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result produced by evaluating an access request against active ABAC policies.
 *
 * @param effect the decision outcome (PERMIT, DENY, or NOT_APPLICABLE)
 * @param reason human-readable justification for the decision
 * @param matchedPolicyIds IDs of matching policies that contributed to this decision
 * @param evaluatedAt evaluation timestamp
 * @author edmaputra
 * @since 0.14.0
 */
public record PolicyDecision(
		DecisionEffect effect,
		String reason,
		List<String> matchedPolicyIds,
		Instant evaluatedAt) {

	public PolicyDecision {
		Objects.requireNonNull(effect, "DecisionEffect must not be null.");
		reason = (reason == null || reason.isBlank()) ? effect.name() : reason.trim();
		matchedPolicyIds = matchedPolicyIds == null ? List.of() : List.copyOf(matchedPolicyIds);
		evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
	}

	public boolean isPermitted() {
		return effect == DecisionEffect.PERMIT;
	}

	public boolean isDenied() {
		return effect == DecisionEffect.DENY;
	}

	public static PolicyDecision permit(String reason) {
		return permit(reason, List.of());
	}

	public static PolicyDecision permit(String reason, List<String> matchedPolicyIds) {
		return new PolicyDecision(DecisionEffect.PERMIT, reason, matchedPolicyIds, Instant.now());
	}

	public static PolicyDecision deny(String reason) {
		return deny(reason, List.of());
	}

	public static PolicyDecision deny(String reason, List<String> matchedPolicyIds) {
		return new PolicyDecision(DecisionEffect.DENY, reason, matchedPolicyIds, Instant.now());
	}

	public static PolicyDecision notApplicable(String reason) {
		return new PolicyDecision(DecisionEffect.NOT_APPLICABLE, reason, List.of(), Instant.now());
	}
}
