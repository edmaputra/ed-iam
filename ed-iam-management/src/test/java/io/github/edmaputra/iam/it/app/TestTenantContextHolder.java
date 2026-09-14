package io.github.edmaputra.iam.it.app;

import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;

/**
 * ScopedValue holder used to simulate a host application's tenant context.
 * Provides boundary-safe, thread-local-free scoping of the tenant UUID.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class TestTenantContextHolder {

	private static final ScopedValue<UUID> CURRENT_TENANT = ScopedValue.newInstance();

	private TestTenantContextHolder() {
	}

	public static ScopedValue<UUID> scopedValue() {
		return CURRENT_TENANT;
	}

	public static UUID getTenantId() {
		return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : null;
	}

	public static Optional<UUID> findTenantId() {
		return CURRENT_TENANT.isBound() ? Optional.of(CURRENT_TENANT.get()) : Optional.empty();
	}
}
