package io.github.edmaputra.iam.application.service;

import java.util.Objects;
import java.util.UUID;

import io.github.edmaputra.iam.application.port.in.ManagePasswordPolicyUseCase;
import io.github.edmaputra.iam.application.port.in.UpdatePasswordPolicyCommand;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.repository.PasswordPolicyRepository;
import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.security.DefaultPasswordValidator;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.security.PasswordValidator;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Application service for retrieving, updating, and enforcing runtime password policies.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class PasswordPolicyManagementService implements ManagePasswordPolicyUseCase, PasswordValidator {

	private final PasswordPolicyRepository policyRepository;
	private final CurrentActorProvider currentActorProvider;
	private final EventPublisherPort eventPublisher;

	public PasswordPolicyManagementService(
			PasswordPolicyRepository policyRepository,
			CurrentActorProvider currentActorProvider,
			EventPublisherPort eventPublisher) {
		this.policyRepository = Objects.requireNonNull(policyRepository, "PasswordPolicyRepository must not be null.");
		this.currentActorProvider = currentActorProvider;
		this.eventPublisher = eventPublisher;
	}

	public PasswordPolicyManagementService(PasswordPolicyRepository policyRepository) {
		this(policyRepository, null, null);
	}

	@Override
	public PasswordPolicy getPolicy(TenantId tenantId) {
		TenantId effectiveTenantId = resolveEffectiveTenantId(tenantId);
		return policyRepository.findByTenantId(effectiveTenantId)
				.orElseGet(PasswordPolicy::defaultPolicy);
	}

	@Override
	public PasswordPolicy updatePolicy(UpdatePasswordPolicyCommand command) {
		Objects.requireNonNull(command, "UpdatePasswordPolicyCommand must not be null.");
		TenantId effectiveTenantId = resolveEffectiveTenantId(command.tenantId());
		PasswordPolicy saved = policyRepository.save(effectiveTenantId, command.policy());

		if (eventPublisher != null) {
			String actorStr = resolveActor();
			UUID entityId = effectiveTenantId != null
					? effectiveTenantId.value()
					: java.util.UUID.nameUUIDFromBytes("GLOBAL_PASSWORD_POLICY".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			eventPublisher.publish(IamEvent.of(
					IamEventTypes.PASSWORD_POLICY_UPDATED,
					effectiveTenantId != null ? effectiveTenantId.value() : null,
					entityId,
					"PASSWORD_POLICY",
					java.util.Map.of(
							"minLength", saved.minLength(),
							"maxLength", saved.maxLength(),
							"minUppercase", saved.minUppercase(),
							"minLowercase", saved.minLowercase(),
							"minNumbers", saved.minNumbers(),
							"minSpecialCharacters", saved.minSpecialCharacters()),
					actorStr
			));
		}
		return saved;
	}

	@Override
	public void validatePassword(String rawPassword, String usernameOrEmail) {
		validatePassword(rawPassword, usernameOrEmail, null);
	}

	@Override
	public void validatePassword(String rawPassword, String usernameOrEmail, TenantId tenantId) {
		PasswordPolicy policy = getPolicy(tenantId);
		new DefaultPasswordValidator(policy).validatePassword(rawPassword, usernameOrEmail);
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
}
