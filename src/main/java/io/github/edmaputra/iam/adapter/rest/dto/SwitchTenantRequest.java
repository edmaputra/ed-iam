package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.UUID;

/**
 * REST request body for switching active tenant context.
 *
 * @param tenantId target tenant UUID
 * @author edmaputra
 * @since 1.0.0
 */
public record SwitchTenantRequest(UUID tenantId) {
}
