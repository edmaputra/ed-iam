package io.github.edmaputra.iam.it.app;

import java.lang.ScopedValue;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;

/**
 * Test implementation of {@link TenantContextBridge} verifying host tenant context propagation
 * using modern Java 25 {@link ScopedValue}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Component
public class TestTenantContextBridge implements TenantContextBridge {

	@Override
	public <E extends Throwable> void runWithTenant(UUID tenantId, ThrowingRunnable<E> runnable) throws E {
		Objects.requireNonNull(tenantId, "Tenant ID must not be null.");
		Objects.requireNonNull(runnable, "Runnable must not be null.");
		ScopedValue.where(TestTenantContextHolder.scopedValue(), tenantId)
				.call(() -> {
					runnable.run();
					return null;
				});
	}
}
