package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;
import java.util.Set;

import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Command for updating the permitted redirect URIs for a tenant or global system.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public record UpdateRedirectUriPolicyCommand(TenantId tenantId, RedirectUriPolicy policy) {

	public UpdateRedirectUriPolicyCommand {
		Objects.requireNonNull(policy, "RedirectUriPolicy must not be null.");
	}

	public static UpdateRedirectUriPolicyCommand of(TenantId tenantId, Set<String> uris) {
		return new UpdateRedirectUriPolicyCommand(tenantId, new RedirectUriPolicy(uris));
	}
}
