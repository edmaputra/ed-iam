package io.github.edmaputra.iam.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.application.port.in.ManagePolicyUseCase;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.CreatePolicyCommand;
import io.github.edmaputra.iam.application.port.in.PolicyCommands.UpdatePolicyCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.PolicyNotFoundException;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.repository.PolicyRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service implementing {@link ManagePolicyUseCase} for ABAC policy lifecycle management.
 *
 * @author edmaputra
 * @since 0.14.0
 */
public class PolicyManagementService implements ManagePolicyUseCase {

	private final PolicyRepository policyRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public PolicyManagementService(
			PolicyRepository policyRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.policyRepository = Objects.requireNonNull(policyRepository, "PolicyRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public PolicyManagementService(
			PolicyRepository policyRepository,
			CurrentActorProvider currentActorProvider) {
		this(policyRepository, currentActorProvider, null);
	}

	public PolicyManagementService(PolicyRepository policyRepository) {
		this(policyRepository, null, null);
	}

	@Override
	@Transactional
	public Policy createPolicy(CreatePolicyCommand command) {
		Objects.requireNonNull(command, "CreatePolicyCommand must not be null.");

		if (command.tenantId() != null) {
			checkTenantAccess(command.tenantId());
			if (policyRepository.existsByNameAndTenantId(command.name(), command.tenantId())) {
				throw new IllegalArgumentException("Policy name already exists for tenant: " + command.name());
			}
		} else {
			checkSuperAdminAccess("Only platform superadmins can create global system policies.");
			if (policyRepository.existsByNameAndTenantId(command.name(), null)) {
				throw new IllegalArgumentException("Global policy name already exists: " + command.name());
			}
		}

		Policy policy = new Policy(
				PolicyId.random(),
				command.tenantId(),
				command.name(),
				command.description(),
				command.effect(),
				command.priority(),
				command.enabled(),
				command.target(),
				command.constraints(),
				Instant.now(),
				Instant.now()
		);

		Policy saved = policyRepository.save(policy);
		publishEvent(IamEventTypes.POLICY_CREATED, saved);
		return saved;
	}

	@Override
	@Transactional
	public Policy updatePolicy(UpdatePolicyCommand command) {
		Objects.requireNonNull(command, "UpdatePolicyCommand must not be null.");

		Policy existing = policyRepository.findById(command.id())
				.orElseThrow(() -> new PolicyNotFoundException(command.id()));

		if (existing.tenantId() != null) {
			checkTenantAccess(existing.tenantId());
		} else {
			checkSuperAdminAccess("Only platform superadmins can modify global system policies.");
		}

		TenantId targetTenant = command.tenantId() != null ? command.tenantId() : existing.tenantId();
		if (!existing.name().equalsIgnoreCase(command.name())) {
			if (policyRepository.existsByNameAndTenantId(command.name(), targetTenant)) {
				throw new IllegalArgumentException("Policy name already exists: " + command.name());
			}
		}

		Policy updated = new Policy(
				existing.id(),
				targetTenant,
				command.name(),
				command.description(),
				command.effect(),
				command.priority(),
				command.enabled(),
				command.target(),
				command.constraints(),
				existing.createdAt(),
				Instant.now()
		);

		Policy saved = policyRepository.save(updated);
		publishEvent(IamEventTypes.POLICY_UPDATED, saved);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Policy> getPolicy(PolicyId id) {
		Objects.requireNonNull(id, "PolicyId must not be null.");
		return policyRepository.findById(id).filter(policy -> {
			if (policy.tenantId() != null && currentActorProvider != null) {
				return currentActorProvider.currentActor()
						.map(actor -> actor.isPlatformSuperAdmin() || policy.tenantId().value().equals(actor.tenantId()))
						.orElse(true);
			}
			return true;
		});
	}

	@Override
	@Transactional(readOnly = true)
	public List<Policy> listPolicies(TenantId tenantId) {
		if (tenantId != null) {
			checkTenantAccess(tenantId);
		}
		return policyRepository.findByTenantId(tenantId);
	}

	@Override
	@Transactional
	public void deletePolicy(PolicyId id) {
		Objects.requireNonNull(id, "PolicyId must not be null.");
		Policy existing = policyRepository.findById(id)
				.orElseThrow(() -> new PolicyNotFoundException(id));

		if (existing.tenantId() != null) {
			checkTenantAccess(existing.tenantId());
		} else {
			checkSuperAdminAccess("Only platform superadmins can delete global system policies.");
		}

		policyRepository.deleteById(id);
		publishEvent(IamEventTypes.POLICY_DELETED, existing);
	}

	private void checkTenantAccess(TenantId tenantId) {
		if (currentActorProvider == null) {
			return;
		}
		currentActorProvider.currentActor().ifPresent(actor -> {
			if (actor.isPlatformSuperAdmin()) {
				return;
			}
			if (actor.tenantId() == null || !actor.tenantId().equals(tenantId.value())) {
				throw new AccessDeniedException("Access denied: Actor does not belong to tenant " + tenantId.value());
			}
		});
	}

	private void checkSuperAdminAccess(String message) {
		if (currentActorProvider == null) {
			return;
		}
		currentActorProvider.currentActor().ifPresent(actor -> {
			if (!actor.isPlatformSuperAdmin()) {
				throw new AccessDeniedException(message);
			}
		});
	}

	private void publishEvent(String eventType, Policy policy) {
		if (eventPublisher == null) {
			return;
		}
		String actor = currentActorProvider != null
				? currentActorProvider.currentActor().map(CurrentActor::email).orElse("system")
				: "system";

		eventPublisher.publish(IamEvent.of(
				eventType,
				policy.tenantId() != null ? policy.tenantId().value() : null,
				policy.id().value(),
				"POLICY",
				Map.of("name", policy.name(), "effect", policy.effect().name(), "priority", policy.priority()),
				actor
		));
	}
}
