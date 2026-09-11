package io.github.edmaputra.iam.playground.controller;

import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.playground.config.HostTenantContext;

/**
 * Controller exposing the resolved security and tenancy context for debugging and verification.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/playground")
@RequiredArgsConstructor
public class CurrentActorContextController {

	private final CurrentActorProvider currentActorProvider;

	@GetMapping("/actor-context")
	public ResponseEntity<ActorContextResponse> getActorContext() {
		CurrentActor actor = currentActorProvider.requireCurrentActor();
		UUID hostTenant = HostTenantContext.getTenantId().orElse(null);

		return ResponseEntity.ok(new ActorContextResponse(
				actor.userId(),
				actor.email(),
				actor.tenantId(),
				hostTenant,
				actor.isPlatformSuperAdmin(),
				actor.isTenantWide(),
				actor.roles(),
				actor.permissions(),
				actor.accessibleScopeNodeIds()
		));
	}

	public record ActorContextResponse(
			UUID userId,
			String email,
			UUID iamTenantId,
			UUID hostBridgeTenantId,
			boolean isPlatformSuperAdmin,
			boolean isTenantWide,
			Set<String> roles,
			Set<String> permissions,
			Set<UUID> accessibleScopeNodeIds
	) {}
}
