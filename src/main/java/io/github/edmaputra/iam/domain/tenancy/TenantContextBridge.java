package io.github.edmaputra.iam.domain.tenancy;

import java.util.UUID;

/**
 * Pluggable SPI hook allowing consuming applications to propagate tenant context
 * (e.g. ScopedValue, ThreadLocal) during authenticated request execution.
 *
 * @author edmaputra
 */
@FunctionalInterface
public interface TenantContextBridge {

	@FunctionalInterface
	interface ThrowingRunnable<E extends Throwable> {
		void run() throws E;
	}

	/**
	 * Executes an operation within the scope of the given tenant ID.
	 *
	 * @param tenantId the tenant ID to bind
	 * @param runnable the callback to run within the tenant context
	 * @param <E> the exception type that may be thrown
	 * @throws E if the operation fails
	 */
	<E extends Throwable> void runWithTenant(UUID tenantId, ThrowingRunnable<E> runnable) throws E;
}
