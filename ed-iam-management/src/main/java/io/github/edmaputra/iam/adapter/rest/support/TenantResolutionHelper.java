package io.github.edmaputra.iam.adapter.rest.support;

import java.util.UUID;

/**
 * Utility for extracting and validating tenant identifiers from HTTP request headers and payloads.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class TenantResolutionHelper {

	private TenantResolutionHelper() {}

	/**
	 * Parses an optional tenant identifier from the {@code X-Tenant-ID} HTTP header.
	 *
	 * @param headerTenantId optional {@code X-Tenant-ID} header string
	 * @return parsed tenant {@link UUID}, or {@code null} if header is absent or blank
	 * @throws IllegalArgumentException if the header value is not a valid UUID
	 */
	public static UUID parseTenantHeader(String headerTenantId) {
		if (headerTenantId == null || headerTenantId.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(headerTenantId.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid UUID string for X-Tenant-ID header: " + headerTenantId);
		}
	}

	/**
	 * Resolves an optional tenant identifier from an optional HTTP header or fallback identifier.
	 *
	 * @param headerTenantId   optional {@code X-Tenant-ID} header string
	 * @param fallbackTenantId optional tenant ID from request body or query parameter
	 * @return resolved tenant {@link UUID}, or {@code null} if neither is provided
	 * @throws IllegalArgumentException if the header value is not a valid UUID
	 */
	public static UUID resolveOptionalTenantId(String headerTenantId, UUID fallbackTenantId) {
		UUID headerUuid = parseTenantHeader(headerTenantId);
		return headerUuid != null ? headerUuid : fallbackTenantId;
	}

	/**
	 * Resolves a non-null tenant identifier from an optional HTTP header or fallback identifier.
	 *
	 * @param headerTenantId   optional {@code X-Tenant-ID} header string
	 * @param fallbackTenantId optional tenant ID from request body or query parameter
	 * @return validated tenant {@link UUID}
	 * @throws IllegalArgumentException if neither header nor fallback provides a valid tenant ID, or header is not a valid UUID
	 */
	public static UUID resolveTenantId(String headerTenantId, UUID fallbackTenantId) {
		UUID resolved = resolveOptionalTenantId(headerTenantId, fallbackTenantId);
		if (resolved != null) {
			return resolved;
		}
		throw new IllegalArgumentException("Tenant ID must be specified via X-Tenant-ID header or request parameter.");
	}
}
