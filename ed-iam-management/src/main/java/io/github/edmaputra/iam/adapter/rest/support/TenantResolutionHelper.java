package io.github.edmaputra.iam.adapter.rest.support;

import java.util.UUID;

/**
 * Utility for extracting and validating tenant identifiers from HTTP request headers and payloads.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class TenantResolutionHelper {

	private TenantResolutionHelper() {}

	/**
	 * Resolves a non-null tenant identifier from an optional HTTP header or fallback identifier.
	 *
	 * @param headerTenantId   optional {@code X-Tenant-ID} header string
	 * @param fallbackTenantId optional tenant ID from request body or query parameter
	 * @return validated tenant {@link UUID}
	 * @throws IllegalArgumentException if neither header nor fallback provides a valid tenant ID
	 */
	public static UUID resolveTenantId(String headerTenantId, UUID fallbackTenantId) {
		if (headerTenantId != null && !headerTenantId.isBlank()) {
			return UUID.fromString(headerTenantId.trim());
		}
		if (fallbackTenantId != null) {
			return fallbackTenantId;
		}
		throw new IllegalArgumentException("Tenant ID must be specified via X-Tenant-ID header or request parameter.");
	}
}
