package io.github.edmaputra.iam.domain.policy;

import java.util.Set;

/**
 * Defines the applicability target of an ABAC policy based on actions, resource types, and subject roles.
 *
 * @param actions set of action identifiers (e.g., "read", "write", "patient:*", "*")
 * @param resourceTypes set of target resource types (e.g., "patient-record", "billing:*", "*")
 * @param roles optional set of roles to which this policy applies (empty means all roles)
 * @author edmaputra
 * @since 0.14.0
 */
public record PolicyTarget(
		Set<String> actions,
		Set<String> resourceTypes,
		Set<String> roles) {

	public PolicyTarget {
		actions = actions == null ? Set.of() : Set.copyOf(actions);
		resourceTypes = resourceTypes == null ? Set.of() : Set.copyOf(resourceTypes);
		roles = roles == null ? Set.of() : Set.copyOf(roles);
	}

	public static PolicyTarget all() {
		return new PolicyTarget(Set.of("*"), Set.of("*"), Set.of());
	}

	public static PolicyTarget of(String action, String resourceType) {
		return new PolicyTarget(
				action != null ? Set.of(action) : Set.of("*"),
				resourceType != null ? Set.of(resourceType) : Set.of("*"),
				Set.of()
		);
	}

	/**
	 * Checks if this target applies to the given action, resource type, and subject roles.
	 *
	 * @param action requested action
	 * @param resourceType requested resource type
	 * @param subjectRoles subject's assigned roles
	 * @return true if target matches, false otherwise
	 */
	public boolean matches(String action, String resourceType, Set<String> subjectRoles) {
		if (!matchesPatternSet(actions, action)) {
			return false;
		}

		if (!matchesPatternSet(resourceTypes, resourceType)) {
			return false;
		}

		if (!roles.isEmpty()) {
			if (subjectRoles == null || subjectRoles.isEmpty()) {
				return false;
			}
			boolean hasRole = false;
			for (String r : roles) {
				if (subjectRoles.contains(r) || "*".equals(r)) {
					hasRole = true;
					break;
				}
			}
			if (!hasRole) {
				return false;
			}
		}

		return true;
	}

	private static boolean matchesPatternSet(Set<String> patterns, String value) {
		if (patterns.isEmpty() || patterns.contains("*")) {
			return true;
		}
		if (value == null) {
			return false;
		}
		if (patterns.contains(value)) {
			return true;
		}
		for (String pattern : patterns) {
			if (pattern.endsWith("*")) {
				String prefix = pattern.substring(0, pattern.length() - 1);
				if (value.startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}
}
