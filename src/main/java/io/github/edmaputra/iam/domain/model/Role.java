package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import lombok.Getter;

/**
 * Domain entity representing an IAM role and its assigned permission keys.
 * <p>
 * Roles can either be global/system roles (immutable across tenants) or custom tenant-specific roles.
 *
 * @author edmaputra
 */
@Getter
public class Role {

	private final RoleId id;
	private final TenantId tenantId;
	private final String code;
	private String name;
	private String description;
	private final boolean systemRole;
	private Set<String> permissions;
	private final Instant createdAt;
	private Instant updatedAt;

	/**
	 * Full constructor for reconstructing a role from persistence.
	 *
	 * @param id the unique role ID
	 * @param tenantId the tenant ID (nullable for system roles)
	 * @param code unique role code (uppercase)
	 * @param name human-readable role name
	 * @param description role description
	 * @param systemRole whether this is an immutable system role
	 * @param permissions assigned permission strings
	 * @param createdAt creation timestamp
	 * @param updatedAt last modified timestamp
	 */
	public Role(
			RoleId id,
			TenantId tenantId,
			String code,
			String name,
			String description,
			boolean systemRole,
			Set<String> permissions,
			Instant createdAt,
			Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "Role ID must not be null.");
		this.tenantId = tenantId; // Nullable for global/system roles
		this.code = validateCode(code);
		this.name = validateName(name);
		this.description = description;
		this.systemRole = systemRole;
		this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	/**
	 * Factory method to create a new custom tenant-scoped role.
	 *
	 * @param tenantId the owning tenant ID
	 * @param code unique role code
	 * @param name human-readable name
	 * @param description role description
	 * @param permissions initial permission strings
	 * @return new custom Role instance
	 */
	public static Role createCustom(
			TenantId tenantId,
			String code,
			String name,
			String description,
			Set<String> permissions) {
		Objects.requireNonNull(tenantId, "Tenant ID must not be null for custom roles.");
		Instant now = Instant.now();
		return new Role(
				RoleId.generate(),
				tenantId,
				code,
				name,
				description,
				false,
				permissions,
				now,
				now);
	}

	/**
	 * Factory method to create a global immutable system role.
	 *
	 * @param code unique system role code
	 * @param name human-readable name
	 * @param description role description
	 * @param permissions initial permission strings
	 * @return new system Role instance
	 */
	public static Role createSystemRole(
			String code,
			String name,
			String description,
			Set<String> permissions) {
		Instant now = Instant.now();
		return new Role(
				RoleId.generate(),
				null,
				code,
				name,
				description,
				true,
				permissions,
				now,
				now);
	}

	/**
	 * Updates role details and permissions. System roles cannot be modified.
	 *
	 * @param name updated name
	 * @param description updated description
	 * @param permissions updated permission set
	 */
	public void update(String name, String description, Set<String> permissions) {
		if (this.systemRole) {
			throw new IllegalStateException("System role '" + this.code + "' is immutable and cannot be updated.");
		}
		this.name = validateName(name);
		this.description = description;
		this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		this.updatedAt = Instant.now();
	}

	/**
	 * Returns the optional tenant ID for custom roles.
	 *
	 * @return Optional containing tenant ID, or empty for system roles
	 */
	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	/**
	 * Returns the optional role description.
	 *
	 * @return Optional containing description, or empty
	 */
	public Optional<String> optionalDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Returns an unmodifiable set of assigned permissions.
	 *
	 * @return unmodifiable permission set
	 */
	public Set<String> permissions() {
		return Collections.unmodifiableSet(permissions);
	}

	/**
	 * Checks if this role has the specified permission key.
	 *
	 * @param permission the permission string to check
	 * @return {@code true} if role contains permission, {@code false} otherwise
	 */
	public boolean hasPermission(String permission) {
		return permission != null && permissions.contains(permission);
	}

	private static String validateCode(String code) {
		Objects.requireNonNull(code, "Role code must not be null.");
		String trimmed = code.trim().toUpperCase();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Role code must not be blank.");
		}
		return trimmed;
	}

	private static String validateName(String name) {
		Objects.requireNonNull(name, "Role name must not be null.");
		String trimmed = name.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Role name must not be blank.");
		}
		return trimmed;
	}
}
