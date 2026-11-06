package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.PolicyCommands.CreatePolicyCommand;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.UpdatePolicyCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.PolicyNotFoundException;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.repository.PolicyRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PolicyManagementService}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
class PolicyManagementServiceTest {

	private PolicyRepository repository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;
	private PolicyManagementService service;

	@BeforeEach
	void setUp() {
		repository = mock(PolicyRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);
		service = new PolicyManagementService(repository, currentActorProvider, eventPublisher);
	}

	@Test
	@DisplayName("Should create tenant policy successfully and publish event")
	void shouldCreateTenantPolicy() {
		TenantId tenantId = TenantId.generate();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.email()).thenReturn("admin@tenant.io");
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(repository.existsByNameAndTenantId("RecordPolicy", tenantId)).thenReturn(false);
		when(repository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

		CreatePolicyCommand command = new CreatePolicyCommand(
				tenantId,
				"RecordPolicy",
				"Allows doctors to read records",
				PolicyEffect.PERMIT,
				10,
				true,
				PolicyTarget.all(),
				List.of()
		);

		Policy created = service.createPolicy(command);
		assertThat(created).isNotNull();
		assertThat(created.name()).isEqualTo("RecordPolicy");
		assertThat(created.tenantId()).isEqualTo(tenantId);

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		assertThat(eventCaptor.getValue().eventType()).isEqualTo(IamEventTypes.POLICY_CREATED);
	}

	@Test
	@DisplayName("Should reject policy creation when name already exists")
	void shouldRejectDuplicateName() {
		TenantId tenantId = TenantId.generate();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		when(repository.existsByNameAndTenantId("DuplicatePolicy", tenantId)).thenReturn(true);

		CreatePolicyCommand command = new CreatePolicyCommand(
				tenantId,
				"DuplicatePolicy",
				"Description",
				PolicyEffect.PERMIT,
				10,
				true,
				PolicyTarget.all(),
				List.of()
		);

		assertThatThrownBy(() -> service.createPolicy(command))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("already exists");
	}

	@Test
	@DisplayName("Should prevent cross-tenant policy creation")
	void shouldPreventCrossTenantCreation() {
		TenantId actorTenant = TenantId.generate();
		TenantId targetTenant = TenantId.generate();

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(actorTenant.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		CreatePolicyCommand command = new CreatePolicyCommand(
				targetTenant,
				"CrossTenantPolicy",
				"Description",
				PolicyEffect.PERMIT,
				10,
				true,
				PolicyTarget.all(),
				List.of()
		);

		assertThatThrownBy(() -> service.createPolicy(command))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("Actor does not belong to tenant");
	}

	@Test
	@DisplayName("Should update existing policy and publish event")
	void shouldUpdatePolicy() {
		TenantId tenantId = TenantId.generate();
		PolicyId policyId = PolicyId.random();

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.email()).thenReturn("admin@tenant.io");
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		Policy existing = new Policy(
				policyId, tenantId, "OldName", "OldDesc",
				PolicyEffect.PERMIT, 5, true, PolicyTarget.all(), List.of(), Instant.now(), Instant.now()
		);
		when(repository.findById(policyId)).thenReturn(Optional.of(existing));
		when(repository.existsByNameAndTenantId("NewName", tenantId)).thenReturn(false);
		when(repository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

		UpdatePolicyCommand command = new UpdatePolicyCommand(
				policyId, tenantId, "NewName", "NewDesc",
				PolicyEffect.DENY, 20, false, PolicyTarget.all(), List.of()
		);

		Policy updated = service.updatePolicy(command);
		assertThat(updated.name()).isEqualTo("NewName");
		assertThat(updated.effect()).isEqualTo(PolicyEffect.DENY);
		assertThat(updated.priority()).isEqualTo(20);
		assertThat(updated.enabled()).isFalse();

		verify(eventPublisher).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should delete policy and publish event")
	void shouldDeletePolicy() {
		TenantId tenantId = TenantId.generate();
		PolicyId policyId = PolicyId.random();

		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantId.value());
		when(actor.isPlatformSuperAdmin()).thenReturn(false);
		when(actor.email()).thenReturn("admin@tenant.io");
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		Policy existing = new Policy(
				policyId, tenantId, "PolicyToDelete", "Desc",
				PolicyEffect.PERMIT, 5, true, PolicyTarget.all(), List.of(), Instant.now(), Instant.now()
		);
		when(repository.findById(policyId)).thenReturn(Optional.of(existing));

		service.deletePolicy(policyId);
		verify(repository).deleteById(policyId);
		verify(eventPublisher).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("Should throw PolicyNotFoundException when deleting non-existent policy")
	void shouldThrowOnDeleteNonExistent() {
		PolicyId policyId = PolicyId.random();
		when(repository.findById(policyId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deletePolicy(policyId))
				.isInstanceOf(PolicyNotFoundException.class);
	}
}
