package io.github.edmaputra.iam.domain.policy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Encapsulates the attributes of the security subject (actor) requesting access.
 *
 * @param userId unique user identifier
 * @param tenantId active tenant identifier (null for platform superadmin global context)
 * @param email user email address
 * @param roles assigned role codes
 * @param groups member group codes
 * @param permissions granted permission codes
 * @param attributes arbitrary additional subject attributes (e.g. department, clearanceLevel)
 * @author edmaputra
 * @since 0.14.0
 */
public record SubjectAttributes(
		UUID userId,
		UUID tenantId,
		String email,
		Set<String> roles,
		Set<String> groups,
		Set<String> permissions,
		Map<String, Object> attributes) {

	public SubjectAttributes {
		roles = roles == null ? Set.of() : Set.copyOf(roles);
		groups = groups == null ? Set.of() : Set.copyOf(groups);
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}

	public static SubjectAttributes anonymous() {
		return new SubjectAttributes(null, null, "anonymous", Set.of(), Set.of(), Set.of(), Map.of());
	}
}
