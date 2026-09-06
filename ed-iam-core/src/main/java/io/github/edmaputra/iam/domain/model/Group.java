package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.tenancy.TenantOwned;
import lombok.Getter;

/**
 * Pure domain entity representing a user group or team within a tenant.
 * Supports mapping external IdP group claims for automated federation assignments.
 *
 * @author edmaputra
 * @since 1.0.0
 */
@Getter
public class Group implements TenantOwned {

	private final GroupId id;
	private final TenantId tenantId;
	private final String code;
	private String name;
	private String description;
	private String externalIdpGroupName;
	private final Instant createdAt;
	private Instant updatedAt;

	/**
	 * Canonical constructor for reconstructing existing group domain models.
	 *
	 * @param id                   the unique group ID
	 * @param tenantId             the owning tenant ID
	 * @param code                 the uppercase group code
	 * @param name                 the human-readable group name
	 * @param description          optional description
	 * @param externalIdpGroupName optional external IdP group claim name
	 * @param createdAt            creation timestamp
	 * @param updatedAt            last updated timestamp
	 */
	public Group(
			GroupId id,
			TenantId tenantId,
			String code,
			String name,
			String description,
			String externalIdpGroupName,
			Instant createdAt,
			Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "Group ID must not be null.");
		this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null.");
		this.code = validateCode(code);
		this.name = validateName(name);
		this.description = description;
		this.externalIdpGroupName = externalIdpGroupName;
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	/**
	 * Factory method creating a new tenant group.
	 *
	 * @param tenantId             the owning tenant ID
	 * @param code                 the group code
	 * @param name                 the group name
	 * @param description          optional description
	 * @param externalIdpGroupName optional external IdP group mapping name
	 * @return new {@link Group}
	 */
	public static Group create(
			TenantId tenantId,
			String code,
			String name,
			String description,
			String externalIdpGroupName) {
		Instant now = Instant.now();
		return new Group(
				GroupId.generate(),
				tenantId,
				code,
				name,
				description,
				externalIdpGroupName,
				now,
				now);
	}

	@Override
	public TenantId tenantId() {
		return this.tenantId;
	}

	/**
	 * Updates the group metadata.
	 *
	 * @param name                 the updated group name
	 * @param description          the updated description
	 * @param externalIdpGroupName the updated external IdP group claim name
	 */
	public void updateDetails(String name, String description, String externalIdpGroupName) {
		this.name = validateName(name);
		this.description = description;
		this.externalIdpGroupName = externalIdpGroupName;
		this.updatedAt = Instant.now();
	}

	/**
	 * Returns the optional group description.
	 *
	 * @return optional description string
	 */
	public Optional<String> optionalDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Returns the optional external IdP group claim mapping name.
	 *
	 * @return optional external group name string
	 */
	public Optional<String> optionalExternalIdpGroupName() {
		return Optional.ofNullable(externalIdpGroupName);
	}

	private static String validateCode(String code) {
		Objects.requireNonNull(code, "Group code must not be null.");
		String trimmed = code.trim().toUpperCase();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Group code must not be blank.");
		}
		return trimmed;
	}

	private static String validateName(String name) {
		Objects.requireNonNull(name, "Group name must not be null.");
		String trimmed = name.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Group name must not be blank.");
		}
		return trimmed;
	}
}
