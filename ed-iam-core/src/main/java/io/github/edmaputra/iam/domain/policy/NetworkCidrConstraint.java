package io.github.edmaputra.iam.domain.policy;

import java.util.Set;

/**
 * Contextual constraint restricting access based on client IP network CIDR subnets.
 * Supports both explicit allow-lists and deny-lists.
 *
 * @param allowedCidrs permitted CIDR blocks or exact IP addresses (if non-empty, client IP must match at least one)
 * @param deniedCidrs forbidden CIDR blocks or exact IP addresses (if client IP matches any, access is rejected)
 * @author edmaputra
 * @since 0.14.0
 */
public record NetworkCidrConstraint(
		Set<String> allowedCidrs,
		Set<String> deniedCidrs) implements PolicyConstraint {

	public static final String TYPE = "NETWORK_CIDR";

	public NetworkCidrConstraint {
		allowedCidrs = allowedCidrs == null ? Set.of() : Set.copyOf(allowedCidrs);
		deniedCidrs = deniedCidrs == null ? Set.of() : Set.copyOf(deniedCidrs);
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public boolean evaluate(PolicyEvaluationContext context) {
		String clientIp = context.environment().clientIp();
		if (clientIp == null || clientIp.isBlank()) {
			return false;
		}

		for (String denied : deniedCidrs) {
			if (IpCidrMatcher.matches(clientIp, denied)) {
				return false;
			}
		}

		if (allowedCidrs.isEmpty()) {
			return true;
		}

		for (String allowed : allowedCidrs) {
			if (IpCidrMatcher.matches(clientIp, allowed)) {
				return true;
			}
		}

		return false;
	}

	public static NetworkCidrConstraint allowOnly(Set<String> allowedCidrs) {
		return new NetworkCidrConstraint(allowedCidrs, Set.of());
	}

	public static NetworkCidrConstraint denyOnly(Set<String> deniedCidrs) {
		return new NetworkCidrConstraint(Set.of(), deniedCidrs);
	}
}
