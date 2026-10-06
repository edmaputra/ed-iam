package io.github.edmaputra.iam.domain.repository;

import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound repository SPI for persisting and loading runtime allowed redirect URIs.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface RedirectUriRepository {

	/**
	 * Loads the allowed redirect URI policy for a specific tenant or global scope.
	 *
	 * @param tenantId optional tenant identifier
	 * @return the redirect URI policy
	 */
	RedirectUriPolicy findByTenantId(TenantId tenantId);

	/**
	 * Persists and replaces the allowed redirect URI policy for a specific tenant or global scope.
	 *
	 * @param tenantId optional tenant identifier
	 * @param policy   the policy containing allowed URIs
	 * @return the persisted policy
	 */
	RedirectUriPolicy save(TenantId tenantId, RedirectUriPolicy policy);

	/**
	 * Adds an allowed URI for a specific tenant or global scope.
	 *
	 * @param tenantId optional tenant identifier
	 * @param uri      URI or host pattern
	 */
	void addUri(TenantId tenantId, String uri);

	/**
	 * Removes an allowed URI for a specific tenant or global scope.
	 *
	 * @param tenantId optional tenant identifier
	 * @param uri      URI or host pattern
	 */
	void removeUri(TenantId tenantId, String uri);
}
