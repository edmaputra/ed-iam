package io.github.edmaputra.iam.application.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedirectUriManagementService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class RedirectUriManagementServiceTest {

	private RedirectUriRepository repository;
	private AllowedRedirectHostResolverPort fallbackResolver;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;
	private RedirectUriManagementService service;

	@BeforeEach
	void setUp() {
		repository = mock(RedirectUriRepository.class);
		fallbackResolver = mock(AllowedRedirectHostResolverPort.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);
		service = new RedirectUriManagementService(repository, fallbackResolver, currentActorProvider, eventPublisher);
	}

	@Test
	@DisplayName("getPolicy retrieves policy from repository for tenant")
	void getPolicyReturnsStoredPolicy() {
		TenantId tenantId = TenantId.generate();
		RedirectUriPolicy stored = RedirectUriPolicy.of("https://portal.clinic.org/callback");
		when(repository.findByTenantId(tenantId)).thenReturn(stored);

		RedirectUriPolicy result = service.getPolicy(tenantId);

		assertThat(result).isEqualTo(stored);
		assertThat(result.allowedUris()).containsExactly("https://portal.clinic.org/callback");
	}

	@Test
	@DisplayName("getPolicy resolves tenant from currentActor when tenantId is null")
	void getPolicyResolvesTenantFromCurrentActor() {
		UUID tenantUuid = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantUuid);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		RedirectUriPolicy stored = RedirectUriPolicy.of("https://mytenant.org");
		when(repository.findByTenantId(new TenantId(tenantUuid))).thenReturn(stored);

		RedirectUriPolicy result = service.getPolicy(null);

		assertThat(result).isEqualTo(stored);
	}

	@Test
	@DisplayName("updatePolicy saves policy and publishes REDIRECT_URI_POLICY_UPDATED event")
	void updatePolicySavesAndPublishesEvent() {
		TenantId tenantId = TenantId.generate();
		UUID actorUserId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorUserId);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		RedirectUriPolicy newPolicy = RedirectUriPolicy.of("https://app1.com", "https://app2.com");
		when(repository.save(eq(tenantId), eq(newPolicy))).thenReturn(newPolicy);

		RedirectUriPolicy result = service.updatePolicy(new UpdateRedirectUriPolicyCommand(tenantId, newPolicy));

		assertThat(result).isEqualTo(newPolicy);
		verify(repository).save(tenantId, newPolicy);

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(captor.capture());
		IamEvent event = captor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.REDIRECT_URI_POLICY_UPDATED);
		assertThat(event.tenantId()).isEqualTo(tenantId.value());
		assertThat(event.actor()).isEqualTo(actorUserId.toString());
	}

	@Test
	@DisplayName("addUri adds URI and publishes REDIRECT_URI_ADDED event")
	void addUriAddsAndPublishesEvent() {
		TenantId tenantId = TenantId.generate();
		UUID actorUserId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorUserId);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		RedirectUriPolicy updated = RedirectUriPolicy.of("https://new.org");
		when(repository.findByTenantId(tenantId)).thenReturn(updated);

		RedirectUriPolicy result = service.addUri(tenantId, "https://new.org");

		assertThat(result).isEqualTo(updated);
		verify(repository).addUri(tenantId, "https://new.org");

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(captor.capture());
		IamEvent event = captor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.REDIRECT_URI_ADDED);
	}

	@Test
	@DisplayName("removeUri removes URI and publishes REDIRECT_URI_REMOVED event")
	void removeUriRemovesAndPublishesEvent() {
		TenantId tenantId = TenantId.generate();
		RedirectUriPolicy updated = RedirectUriPolicy.empty();
		when(repository.findByTenantId(tenantId)).thenReturn(updated);

		RedirectUriPolicy result = service.removeUri(tenantId, "https://old.org");

		assertThat(result).isEqualTo(updated);
		verify(repository).removeUri(tenantId, "https://old.org");

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(captor.capture());
		IamEvent event = captor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.REDIRECT_URI_REMOVED);
	}

	@Test
	@DisplayName("isAllowedHost checks tenant policy, global policy, and fallback resolver")
	void isAllowedHostEvaluatesInOrder() {
		TenantId tenantId = TenantId.generate();

		// Tenant policy allows portal.clinic.org
		when(repository.findByTenantId(tenantId)).thenReturn(RedirectUriPolicy.of("portal.clinic.org"));
		when(repository.findByTenantId(null)).thenReturn(RedirectUriPolicy.of("global.org"));

		assertThat(service.isAllowedHost("portal.clinic.org", tenantId)).isTrue();
		assertThat(service.isAllowedHost("global.org", tenantId)).isTrue();

		// If not in database, fallback resolver is queried
		when(fallbackResolver.isAllowedHost("fallback.org", tenantId)).thenReturn(true);
		assertThat(service.isAllowedHost("fallback.org", tenantId)).isTrue();

		// Completely untrusted host
		assertThat(service.isAllowedHost("evil.com", tenantId)).isFalse();
	}
}
