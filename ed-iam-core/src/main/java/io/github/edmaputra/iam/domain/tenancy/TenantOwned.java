package io.github.edmaputra.iam.domain.tenancy;

/**
 * Marks an IAM domain entity or aggregate as belonging to a specific tenant.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface TenantOwned {

	TenantId tenantId();
}
