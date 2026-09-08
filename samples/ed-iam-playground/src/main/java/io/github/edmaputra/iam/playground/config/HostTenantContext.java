package io.github.edmaputra.iam.playground.config;

import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;

/**
 * Context holder demonstrating how host applications can manage request-scoped
 * tenant identifiers using Java 25 {@link ScopedValue}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class HostTenantContext {

	private static final ScopedValue<UUID> TENANT_ID = ScopedValue.newInstance();

	private HostTenantContext() {
	}

	public static ScopedValue<UUID> scopedValue() {
		return TENANT_ID;
	}

	public static Optional<UUID> getTenantId() {
		return TENANT_ID.isBound() ? Optional.of(TENANT_ID.get()) : Optional.empty();
	}
}
