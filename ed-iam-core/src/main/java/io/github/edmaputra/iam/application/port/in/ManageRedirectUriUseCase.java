package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Primary driving use-case port for managing allowed redirect URLs at runtime.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface ManageRedirectUriUseCase {

	/**
	 * Retrieves the allowed redirect URI policy for the specified tenant, or global policy if null.
	 *
	 * @param tenantId optional tenant identifier
	 * @return the redirect URI policy
	 */
	RedirectUriPolicy getPolicy(TenantId tenantId);

	/**
	 * Updates the entire allowed redirect URI policy for the specified tenant or global scope.
	 *
	 * @param command update command containing target tenant and new policy
	 * @return the updated redirect URI policy
	 */
	RedirectUriPolicy updatePolicy(UpdateRedirectUriPolicyCommand command);

	/**
	 * Adds an allowed redirect URI or host pattern for the specified tenant.
	 *
	 * @param tenantId optional tenant identifier
	 * @param uri      candidate redirect URI or host pattern
	 * @return the updated redirect URI policy
	 */
	RedirectUriPolicy addUri(TenantId tenantId, String uri);

	/**
	 * Removes an allowed redirect URI or host pattern for the specified tenant.
	 *
	 * @param tenantId optional tenant identifier
	 * @param uri      redirect URI or host pattern to remove
	 * @return the updated redirect URI policy
	 */
	RedirectUriPolicy removeUri(TenantId tenantId, String uri);
}
