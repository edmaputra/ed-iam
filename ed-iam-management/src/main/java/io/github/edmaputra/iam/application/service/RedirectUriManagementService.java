package io.github.edmaputra.iam.application.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.application.port.in.ManageRedirectUriUseCase;
import io.github.edmaputra.iam.application.port.in.UpdateRedirectUriPolicyCommand;
import io.github.edmaputra.iam.application.port.out.AllowedRedirectHostResolverPort;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.repository.RedirectUriRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service for retrieving, updating, and dynamically evaluating allowed redirect URLs and host patterns.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class RedirectUriManagementService implements ManageRedirectUriUseCase, AllowedRedirectHostResolverPort {

	private final RedirectUriRepository repository;
	private final AllowedRedirectHostResolverPort fallbackResolver;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public RedirectUriManagementService(
			RedirectUriRepository repository,
			AllowedRedirectHostResolverPort fallbackResolver,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.repository = Objects.requireNonNull(repository, "RedirectUriRepository must not be null.");
		this.fallbackResolver = fallbackResolver;
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public RedirectUriManagementService(RedirectUriRepository repository) {
		this(repository, null, null, null);
	}

	@Override
	public RedirectUriPolicy getPolicy(TenantId tenantId) {
		TenantId effectiveTenantId = resolveEffectiveTenantId(tenantId);
		return repository.findByTenantId(effectiveTenantId);
	}

	@Override
	public RedirectUriPolicy updatePolicy(UpdateRedirectUriPolicyCommand command) {
		Objects.requireNonNull(command, "UpdateRedirectUriPolicyCommand must not be null.");
		TenantId effectiveTenantId = resolveEffectiveTenantId(command.tenantId());
		RedirectUriPolicy saved = repository.save(effectiveTenantId, command.policy());

		publishEvent(
				IamEventTypes.REDIRECT_URI_POLICY_UPDATED,
				effectiveTenantId,
				Map.of("allowedUris", saved.allowedUris()));
		return saved;
	}

	@Override
	public RedirectUriPolicy addUri(TenantId tenantId, String uri) {
		if (uri == null || uri.isBlank()) {
			throw new IllegalArgumentException("Redirect URI must not be blank.");
		}
		TenantId effectiveTenantId = resolveEffectiveTenantId(tenantId);
		repository.addUri(effectiveTenantId, uri.trim());
		RedirectUriPolicy updated = repository.findByTenantId(effectiveTenantId);

		publishEvent(
				IamEventTypes.REDIRECT_URI_ADDED,
				effectiveTenantId,
				Map.of("uri", uri.trim()));
		return updated;
	}

	@Override
	public RedirectUriPolicy removeUri(TenantId tenantId, String uri) {
		if (uri == null || uri.isBlank()) {
			throw new IllegalArgumentException("Redirect URI must not be blank.");
		}
		TenantId effectiveTenantId = resolveEffectiveTenantId(tenantId);
		repository.removeUri(effectiveTenantId, uri.trim());
		RedirectUriPolicy updated = repository.findByTenantId(effectiveTenantId);

		publishEvent(
				IamEventTypes.REDIRECT_URI_REMOVED,
				effectiveTenantId,
				Map.of("uri", uri.trim()));
		return updated;
	}

	@Override
	public boolean isAllowedHost(String host, TenantId tenantId) {
		if (host == null || host.isBlank()) {
			return false;
		}

		// 1. Check tenant-specific policy in database
		RedirectUriPolicy tenantPolicy = repository.findByTenantId(tenantId);
		if (tenantPolicy.isAllowedHost(host)) {
			return true;
		}

		// 2. Check global policy in database
		if (tenantId != null) {
			RedirectUriPolicy globalPolicy = repository.findByTenantId(null);
			if (globalPolicy.isAllowedHost(host)) {
				return true;
			}
		}

		// 3. Fall back to properties-based or secondary resolver if configured
		if (fallbackResolver != null) {
			return fallbackResolver.isAllowedHost(host, tenantId);
		}

		return false;
	}

	private TenantId resolveEffectiveTenantId(TenantId tenantId) {
		if (tenantId != null) {
			return tenantId;
		}
		if (currentActorProvider != null && currentActorProvider.currentActor().isPresent()) {
			CurrentActor actor = currentActorProvider.currentActor().get();
			if (actor.tenantId() != null) {
				return new TenantId(actor.tenantId());
			}
		}
		return null;
	}

	private String resolveActor() {
		if (currentActorProvider != null && currentActorProvider.currentActor().isPresent()) {
			CurrentActor actor = currentActorProvider.currentActor().get();
			if (actor.userId() != null) {
				return actor.userId().toString();
			}
		}
		return "system";
	}

	private void publishEvent(String eventType, TenantId effectiveTenantId, Map<String, Object> payload) {
		if (eventPublisher != null) {
			String actorStr = resolveActor();
			UUID entityId = effectiveTenantId != null
					? effectiveTenantId.value()
					: UUID.nameUUIDFromBytes("GLOBAL_REDIRECT_URI_POLICY".getBytes(StandardCharsets.UTF_8));
			eventPublisher.publish(IamEvent.of(
					eventType,
					effectiveTenantId != null ? effectiveTenantId.value() : null,
					entityId,
					"REDIRECT_URI_POLICY",
					payload,
					actorStr
			));
		}
	}
}
