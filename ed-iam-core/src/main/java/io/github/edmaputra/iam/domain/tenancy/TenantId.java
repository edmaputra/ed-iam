package io.github.edmaputra.iam.domain.tenancy;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Value object representing a tenant/organization identifier.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public record TenantId(UUID value) {

	public TenantId {
		Objects.requireNonNull(value, "Tenant ID must not be null.");
	}

	public static TenantId generate() {
		return new TenantId(UuidV7.generate());
	}

	public static TenantId from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Tenant ID must not be blank.");
		}
		return new TenantId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
