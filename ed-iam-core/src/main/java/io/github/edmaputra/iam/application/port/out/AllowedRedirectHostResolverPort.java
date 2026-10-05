package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound SPI port for resolving whether a candidate target host is permitted
 * for post-authentication redirects (OWASP A10 / CWE-601 Open Redirect prevention).
 * <p>
 * Implementations may resolve trusted redirect hosts statically from configuration
 * or dynamically at runtime from tenant records, database allowlists, or cache.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface AllowedRedirectHostResolverPort {

	/**
	 * Determines whether the given host is an allowed redirect destination, optionally
	 * scoped to a specific tenant.
	 *
	 * @param host     the target host (e.g., "portal.clinic.org" or "localhost")
	 * @param tenantId the tenant requesting authentication, or {@code null} if un-scoped
	 * @return {@code true} if redirect to this host is allowed; {@code false} otherwise
	 */
	boolean isAllowedHost(String host, TenantId tenantId);
}
