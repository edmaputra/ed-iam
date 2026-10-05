package io.github.edmaputra.iam.adapter.rest.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Request and response DTOs for allowed redirect URI administrative endpoints.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public final class RedirectUriDtos {

	private RedirectUriDtos() {}

	/**
	 * DTO representing allowed redirect URI policy response.
	 */
	public record RedirectUriPolicyResponse(
			UUID tenantId,
			Set<String> allowedUris) {

		public static RedirectUriPolicyResponse fromDomain(TenantId tenantId, RedirectUriPolicy policy) {
			return new RedirectUriPolicyResponse(
					tenantId != null ? tenantId.value() : null,
					policy.allowedUris()
			);
		}
	}

	/**
	 * DTO representing request to replace entire allowed redirect URI policy.
	 */
	public record UpdateRedirectUriPolicyRequest(
			UUID tenantId,
			Set<String> allowedUris) {

		public RedirectUriPolicy toDomain() {
			return new RedirectUriPolicy(allowedUris != null ? allowedUris : Set.of());
		}
	}

	/**
	 * DTO representing request to append a single allowed redirect URI or host pattern.
	 */
	public record AddRedirectUriRequest(
			UUID tenantId,

			@NotBlank(message = "uri must not be blank.")
			@Size(max = 500, message = "uri must not exceed 500 characters.")
			String uri) {}
}
