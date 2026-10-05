package io.github.edmaputra.iam.domain.repository;

import java.util.Optional;

import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Outbound repository SPI for persisting and loading password policy configurations.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface PasswordPolicyRepository {

	/**
	 * Finds the configured password policy for the specified tenant ID (or null for global).
	 *
	 * @param tenantId the tenant ID, or null for global
	 * @return optional {@link PasswordPolicy}
	 */
	Optional<PasswordPolicy> findByTenantId(TenantId tenantId);

	/**
	 * Saves or updates the password policy for the specified tenant ID.
	 *
	 * @param tenantId the tenant ID, or null for global
	 * @param policy   the policy to persist
	 * @return persisted {@link PasswordPolicy}
	 */
	PasswordPolicy save(TenantId tenantId, PasswordPolicy policy);
}
