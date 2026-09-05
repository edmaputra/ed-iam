package io.github.edmaputra.iam.application.port.in;

import java.util.Objects;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Command for switching the active tenant context for the currently authenticated user.
 *
 * @param currentActor   the authenticated actor requesting the tenant switch
 * @param targetTenantId the target tenant ID to switch into
 * @author edmaputra
 * @since 1.0.0
 */
public record SwitchTenantCommand(CurrentActor currentActor, TenantId targetTenantId) {

	public SwitchTenantCommand {
		Objects.requireNonNull(currentActor, "CurrentActor must not be null.");
		Objects.requireNonNull(targetTenantId, "TargetTenantId must not be null.");
	}
}
