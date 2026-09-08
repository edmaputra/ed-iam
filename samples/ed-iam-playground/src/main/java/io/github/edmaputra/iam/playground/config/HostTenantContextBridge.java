package io.github.edmaputra.iam.playground.config;

import java.lang.ScopedValue;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;

/**
 * Host implementation of {@link TenantContextBridge} hooking into the ed-iam
 * authentication filter to propagate the resolved tenant into {@link HostTenantContext}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Component
public class HostTenantContextBridge implements TenantContextBridge {

	@Override
	public <E extends Throwable> void runWithTenant(UUID tenantId, ThrowingRunnable<E> runnable) throws E {
		Objects.requireNonNull(tenantId, "Tenant ID must not be null.");
		Objects.requireNonNull(runnable, "Runnable must not be null.");

		ScopedValue.where(HostTenantContext.scopedValue(), tenantId)
				.call(() -> {
					runnable.run();
					return null;
				});
	}
}
