package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

import io.github.edmaputra.iam.domain.model.Role;

/**
 * Request and response DTOs for role administrative management endpoints.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class RoleManagementDtos {

	private RoleManagementDtos() {}

	public record CreateRoleRequest(
			UUID tenantId,

			@NotBlank(message = "Role code must not be blank.")
			String code,

			@NotBlank(message = "Role name must not be blank.")
			String name,

			String description,
			Set<String> permissions) {}

	public record UpdateRoleRequest(
			@NotBlank(message = "Role name must not be blank.")
			String name,

			String description,
			Set<String> permissions) {}

	public record RoleResponse(
			UUID id,
			UUID tenantId,
			String code,
			String name,
			String description,
			boolean systemRole,
			Set<String> permissions,
			Instant createdAt,
			Instant updatedAt) {

		public static RoleResponse fromDomain(Role role) {
			return new RoleResponse(
					role.getId().value(),
					role.getTenantId() != null ? role.getTenantId().value() : null,
					role.getCode(),
					role.getName(),
					role.getDescription(),
					role.isSystemRole(),
					role.getPermissions(),
					role.getCreatedAt(),
					role.getUpdatedAt());
		}
	}
}
