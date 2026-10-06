package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Command for updating or setting a password policy for a specific tenant or globally.
 *
 * @param tenantId optional tenant ID (null for global baseline)
 * @param policy   the new password policy configuration
 * @author edmaputra
 * @since 0.9.0
 */
public record UpdatePasswordPolicyCommand(TenantId tenantId, PasswordPolicy policy) {

	public UpdatePasswordPolicyCommand {
		if (policy == null) {
			policy = PasswordPolicy.defaultPolicy();
		}
	}

	public static UpdatePasswordPolicyCommand of(TenantId tenantId, PasswordPolicy policy) {
		return new UpdatePasswordPolicyCommand(tenantId, policy);
	}
}
