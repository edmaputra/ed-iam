package io.github.edmaputra.iam.adapter.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.github.edmaputra.iam.domain.model.Role;

/**
 * Request and response DTOs for role administrative management endpoints.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class RoleManagementDtos {

	private RoleManagementDtos() {}

	public record CreateRoleRequest(
			UUID tenantId,
			String code,
			String name,
			String description,
			Set<String> permissions) {}

	public record UpdateRoleRequest(
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
