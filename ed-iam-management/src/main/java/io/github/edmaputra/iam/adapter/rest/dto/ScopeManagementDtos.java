package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.edmaputra.iam.domain.model.ScopeNode;

/**
 * Request and response DTOs for organizational scope administrative management endpoints.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public final class ScopeManagementDtos {

	private ScopeManagementDtos() {}

	public record CreateScopeRequest(
			@NotNull(message = "Tenant ID must not be null.")
			UUID tenantId,

			UUID parentId,

			@NotBlank(message = "Scope code must not be blank.")
			String code,

			@NotBlank(message = "Scope name must not be blank.")
			String name) {}

	public record UpdateScopeRequest(
			String code,

			@NotBlank(message = "Scope name must not be blank.")
			String name) {}

	public record MoveScopeRequest(
			UUID newParentId) {}

	public record ScopeNodeResponse(
			UUID id,
			UUID tenantId,
			UUID parentId,
			String code,
			String name,
			String path,
			int level) {

		public static ScopeNodeResponse fromDomain(ScopeNode node) {
			int level = Math.max(0, (int) node.getPath().chars().filter(ch -> ch == '/').count() - 2);
			return new ScopeNodeResponse(
					node.getId().value(),
					node.getTenantId().value(),
					node.getParentId() != null ? node.getParentId().value() : null,
					node.getCode(),
					node.getName(),
					node.getPath(),
					level);
		}
	}
}
