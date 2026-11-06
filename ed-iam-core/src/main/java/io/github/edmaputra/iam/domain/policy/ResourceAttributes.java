package io.github.edmaputra.iam.domain.policy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the attributes of the target resource being accessed.
 *
 * @param id resource identifier (e.g. record UUID or string ID)
 * @param type resource type category (e.g. "patient-record", "billing-invoice", "user")
 * @param tenantId owning tenant UUID of the resource
 * @param ownerId user UUID of the resource owner / author
 * @param attributes arbitrary additional resource attributes (e.g. department, classification, status)
 * @author edmaputra
 * @since 0.14.0
 */
public record ResourceAttributes(
		String id,
		String type,
		UUID tenantId,
		UUID ownerId,
		Map<String, Object> attributes) {

	public ResourceAttributes {
		Objects.requireNonNull(type, "Resource type must not be null.");
		if (type.isBlank()) {
			throw new IllegalArgumentException("Resource type must not be blank.");
		}
		type = type.trim();
		id = id != null ? id.trim() : null;
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}

	public static ResourceAttributes ofType(String type) {
		return new ResourceAttributes(null, type, null, null, Map.of());
	}

	public static ResourceAttributes of(String id, String type) {
		return new ResourceAttributes(id, type, null, null, Map.of());
	}
}
