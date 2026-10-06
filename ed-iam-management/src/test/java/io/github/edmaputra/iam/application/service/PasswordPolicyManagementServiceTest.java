package io.github.edmaputra.iam.application.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.application.port.in.UpdatePasswordPolicyCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.repository.PasswordPolicyRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordPolicyManagementService}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class PasswordPolicyManagementServiceTest {

	private PasswordPolicyRepository repository;
	private CurrentActorProvider currentActorProvider;
	private EventPublisherPort eventPublisher;
	private PasswordPolicyManagementService service;

	@BeforeEach
	void setUp() {
		repository = mock(PasswordPolicyRepository.class);
		currentActorProvider = mock(CurrentActorProvider.class);
		eventPublisher = mock(EventPublisherPort.class);
		service = new PasswordPolicyManagementService(repository, currentActorProvider, eventPublisher);
	}

	@Test
	@DisplayName("getPolicy returns default policy when none is configured for tenant")
	void getPolicyReturnsDefaultWhenNoneConfigured() {
		TenantId tenantId = TenantId.generate();
		when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

		PasswordPolicy policy = service.getPolicy(tenantId);

		assertThat(policy).isEqualTo(PasswordPolicy.defaultPolicy());
		assertThat(policy.minLength()).isEqualTo(8);
	}

	@Test
	@DisplayName("getPolicy returns stored tenant policy when configured")
	void getPolicyReturnsStoredTenantPolicy() {
		TenantId tenantId = TenantId.generate();
		PasswordPolicy customPolicy = new PasswordPolicy(12, 64, 1, 1, 1, 1, null, null, true);
		when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(customPolicy));

		PasswordPolicy policy = service.getPolicy(tenantId);

		assertThat(policy).isEqualTo(customPolicy);
		assertThat(policy.minLength()).isEqualTo(12);
	}

	@Test
	@DisplayName("getPolicy resolves tenant from currentActor when tenantId is null")
	void getPolicyResolvesTenantFromCurrentActor() {
		UUID tenantUuid = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.tenantId()).thenReturn(tenantUuid);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		PasswordPolicy customPolicy = new PasswordPolicy(10, 64, 1, 1, 1, 1, null, null, true);
		when(repository.findByTenantId(new TenantId(tenantUuid))).thenReturn(Optional.of(customPolicy));

		PasswordPolicy policy = service.getPolicy(null);

		assertThat(policy).isEqualTo(customPolicy);
	}

	@Test
	@DisplayName("updatePolicy saves policy and publishes PASSWORD_POLICY_UPDATED event")
	void updatePolicySavesAndPublishesEvent() {
		TenantId tenantId = TenantId.generate();
		UUID actorUserId = UUID.randomUUID();
		CurrentActor actor = mock(CurrentActor.class);
		when(actor.userId()).thenReturn(actorUserId);
		when(currentActorProvider.currentActor()).thenReturn(Optional.of(actor));

		PasswordPolicy newPolicy = new PasswordPolicy(14, 100, 2, 2, 2, 1, null, null, true);
		when(repository.save(eq(tenantId), eq(newPolicy))).thenReturn(newPolicy);

		PasswordPolicy result = service.updatePolicy(new UpdatePasswordPolicyCommand(tenantId, newPolicy));

		assertThat(result).isEqualTo(newPolicy);
		verify(repository).save(tenantId, newPolicy);

		ArgumentCaptor<IamEvent> eventCaptor = ArgumentCaptor.forClass(IamEvent.class);
		verify(eventPublisher).publish(eventCaptor.capture());
		IamEvent event = eventCaptor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.PASSWORD_POLICY_UPDATED);
		assertThat(event.tenantId()).isEqualTo(tenantId.value());
		assertThat(event.actor()).isEqualTo(actorUserId.toString());
	}

	@Test
	@DisplayName("validatePassword succeeds when password conforms to configured tenant policy")
	void validatePasswordSucceedsWithConformingPassword() {
		TenantId tenantId = TenantId.generate();
		PasswordPolicy customPolicy = new PasswordPolicy(10, 50, 2, 2, 2, 1, "^[A-Za-z0-9!@#\\$%\\^&\\*]+$", "Must be valid alphanumeric or symbol", true);
		when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(customPolicy));

		assertThatCode(() -> service.validatePassword("AAaa11!valid", "user1@company.com", tenantId))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("validatePassword fails when password violates custom uppercase requirement")
	void validatePasswordFailsWhenViolatesUppercase() {
		TenantId tenantId = TenantId.generate();
		PasswordPolicy customPolicy = new PasswordPolicy(10, 50, 3, 1, 1, 1, null, null, false);
		when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(customPolicy));

		assertThatThrownBy(() -> service.validatePassword("Aa1!password", "user@company.com", tenantId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 3 uppercase");
	}
}
