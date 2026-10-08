package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.domain.model.Permission;

/**
 * Request and response DTOs for permission administrative management endpoints.
 *
 * @author edmaputra
 * @since 0.10.0
 */
public final class PermissionManagementDtos {

	private PermissionManagementDtos() {}

	/**
	 * Request payload for creating a permission.
	 *
	 * @param tenantId optional tenant ID (if creating tenant-scoped custom permission)
	 * @param code unique permission code
	 * @param name human-readable permission name
	 * @param description optional description
	 * @param category optional domain category
	 *
	 * @author edmaputra
	 * @since 0.10.0
	 */
	public record CreatePermissionRequest(
			UUID tenantId,

			@NotBlank(message = "Permission code must not be blank.")
			String code,

			@NotBlank(message = "Permission name must not be blank.")
			String name,

			String description,
			String category) {}

	/**
	 * Request payload for updating permission details.
	 *
	 * @param name human-readable permission name
	 * @param description optional description
	 * @param category optional domain category
	 *
	 * @author edmaputra
	 * @since 0.10.0
	 */
	public record UpdatePermissionRequest(
			@NotBlank(message = "Permission name must not be blank.")
			String name,

			String description,
			String category) {}

	/**
	 * Response payload describing a permission in the catalog.
	 *
	 * @param id permission ID
	 * @param tenantId tenant ID (null for system permissions)
	 * @param code unique permission code
	 * @param name human-readable permission name
	 * @param description optional description
	 * @param category domain category
	 * @param systemPermission whether this is a system permission
	 * @param createdAt creation timestamp
	 * @param updatedAt last modified timestamp
	 *
	 * @author edmaputra
	 * @since 0.10.0
	 */
	public record PermissionResponse(
			UUID id,
			UUID tenantId,
			String code,
			String name,
			String description,
			String category,
			boolean systemPermission,
			Instant createdAt,
			Instant updatedAt) {

		public static PermissionResponse fromDomain(Permission permission) {
			return new PermissionResponse(
					permission.id().value(),
					permission.tenantId() != null ? permission.tenantId().value() : null,
					permission.code(),
					permission.name(),
					permission.description(),
					permission.category(),
					permission.systemPermission(),
					permission.createdAt(),
					permission.updatedAt());
		}
	}
}
