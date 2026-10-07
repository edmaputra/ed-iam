package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Domain record representing an IAM permission in the catalog.
 * <p>
 * Permissions define fine-grained entitlements (e.g. {@code iam:user:create}, {@code ehr:patient:read})
 * that can be assigned to roles. They can be global system permissions or tenant-scoped custom permissions.
 *
 * @param id the unique permission ID
 * @param tenantId the owning tenant ID (null for global system permissions)
 * @param code unique permission code
 * @param name human-readable permission name
 * @param description optional description of what the permission grants
 * @param category logical domain or module category (e.g. "IAM_USER", "BILLING")
 * @param systemPermission whether this is an immutable system permission
 * @param createdAt creation timestamp
 * @param updatedAt last modified timestamp
 *
 * @author edmaputra
 * @since 0.10.0
 */
public record Permission(
		PermissionId id,
		TenantId tenantId,
		String code,
		String name,
		String description,
		String category,
		boolean systemPermission,
		Instant createdAt,
		Instant updatedAt) {

	public Permission {
		Objects.requireNonNull(id, "Permission ID must not be null.");
		Objects.requireNonNull(code, "Permission code must not be null.");
		if (code.isBlank()) {
			throw new IllegalArgumentException("Permission code must not be blank.");
		}
		Objects.requireNonNull(name, "Permission name must not be null.");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Permission name must not be blank.");
		}
		code = code.trim();
		name = name.trim();
		description = description == null ? null : description.trim();
		category = (category == null || category.isBlank()) ? "GENERAL" : category.trim();
		createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	public Optional<String> optionalDescription() {
		return Optional.ofNullable(description);
	}

	public static Permission createSystem(String code, String name, String description, String category) {
		Instant now = Instant.now();
		return new Permission(
				PermissionId.generate(),
				null,
				code,
				name,
				description,
				category,
				true,
				now,
				now);
	}

	public static Permission createCustom(
			TenantId tenantId,
			String code,
			String name,
			String description,
			String category) {
		Objects.requireNonNull(tenantId, "Tenant ID must not be null for custom permissions.");
		Instant now = Instant.now();
		return new Permission(
				PermissionId.generate(),
				tenantId,
				code,
				name,
				description,
				category,
				false,
				now,
				now);
	}

	public Permission update(String name, String description, String category) {
		if (this.systemPermission) {
			throw new IllegalStateException("System permission '" + this.code + "' is immutable and cannot be updated.");
		}
		return new Permission(
				this.id,
				this.tenantId,
				this.code,
				name,
				description,
				category,
				this.systemPermission,
				this.createdAt,
				Instant.now());
	}
}
