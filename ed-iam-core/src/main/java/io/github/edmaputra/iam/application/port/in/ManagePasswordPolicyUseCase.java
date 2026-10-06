package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Inbound driving use case port for managing, retrieving, and configuring password policies.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface ManagePasswordPolicyUseCase {

	/**
	 * Retrieves the active password policy for the given tenant, or the global default if un-scoped.
	 *
	 * @param tenantId optional tenant ID
	 * @return active {@link PasswordPolicy}
	 */
	PasswordPolicy getPolicy(TenantId tenantId);

	/**
	 * Updates the password policy for the specified tenant or globally.
	 *
	 * @param command update command containing target scope and policy constraints
	 * @return updated {@link PasswordPolicy}
	 */
	PasswordPolicy updatePolicy(UpdatePasswordPolicyCommand command);
}
