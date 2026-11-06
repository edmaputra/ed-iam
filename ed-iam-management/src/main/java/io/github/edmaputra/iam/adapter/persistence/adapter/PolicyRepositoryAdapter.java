package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.PolicyJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PolicyJpaRepository;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.repository.PolicyRepository;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Persistence adapter implementing {@link PolicyRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.14.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyRepositoryAdapter implements PolicyRepository {

	private final PolicyJpaRepository repository;
	private final PolicyConstraintJsonConverter constraintConverter;

	@Override
	@Transactional
	public Policy save(Policy policy) {
		Objects.requireNonNull(policy, "Policy must not be null.");
		PolicyJpaEntity entity = toEntity(policy);
		PolicyJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public Optional<Policy> findById(PolicyId id) {
		Objects.requireNonNull(id, "PolicyId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<Policy> findByNameAndTenantId(String name, TenantId tenantId) {
		Objects.requireNonNull(name, "Policy name must not be null.");
		if (tenantId != null) {
			return repository.findByTenantIdAndNameIgnoreCase(tenantId.value(), name.trim()).map(this::toDomain);
		}
		return repository.findByTenantIdIsNullAndNameIgnoreCase(name.trim()).map(this::toDomain);
	}

	@Override
	public List<Policy> findByTenantId(TenantId tenantId) {
		if (tenantId != null) {
			return repository.findAllByTenantIdOrGlobal(tenantId.value()).stream()
					.map(this::toDomain)
					.toList();
		}
		return repository.findByTenantIdIsNullOrderByPriorityDesc().stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public List<Policy> findAllActive(TenantId tenantId) {
		if (tenantId != null) {
			return repository.findAllActiveByTenantIdOrGlobal(tenantId.value()).stream()
					.map(this::toDomain)
					.toList();
		}
		return repository.findAllActiveGlobal().stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public boolean existsByNameAndTenantId(String name, TenantId tenantId) {
		Objects.requireNonNull(name, "Policy name must not be null.");
		if (tenantId != null) {
			return repository.existsByTenantIdAndNameIgnoreCase(tenantId.value(), name.trim());
		}
		return repository.existsByTenantIdIsNullAndNameIgnoreCase(name.trim());
	}

	@Override
	@Transactional
	public void deleteById(PolicyId id) {
		Objects.requireNonNull(id, "PolicyId must not be null.");
		repository.deleteById(id.value());
	}

	private PolicyJpaEntity toEntity(Policy policy) {
		PolicyJpaEntity entity = new PolicyJpaEntity();
		entity.setId(policy.id().value());
		entity.setTenantId(policy.tenantId() != null ? policy.tenantId().value() : null);
		entity.setName(policy.name());
		entity.setDescription(policy.description());
		entity.setEffect(policy.effect().name());
		entity.setPriority(policy.priority());
		entity.setEnabled(policy.enabled());

		if (policy.target() != null) {
			entity.setTargetActions(String.join(",", policy.target().actions()));
			entity.setTargetResourceTypes(String.join(",", policy.target().resourceTypes()));
			entity.setTargetRoles(String.join(",", policy.target().roles()));
		}

		entity.setConstraintsJson(constraintConverter.toJson(policy.constraints()));
		entity.setCreatedAt(policy.createdAt());
		entity.setUpdatedAt(policy.updatedAt());
		return entity;
	}

	private Policy toDomain(PolicyJpaEntity entity) {
		TenantId tenantId = entity.getTenantId() != null ? new TenantId(entity.getTenantId()) : null;
		PolicyEffect effect = PolicyEffect.valueOf(entity.getEffect());
		PolicyTarget target = parseTarget(entity.getTargetActions(), entity.getTargetResourceTypes(), entity.getTargetRoles());

		return new Policy(
				new PolicyId(entity.getId()),
				tenantId,
				entity.getName(),
				entity.getDescription(),
				effect,
				entity.getPriority(),
				entity.isEnabled(),
				target,
				constraintConverter.fromJson(entity.getConstraintsJson()),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
		);
	}

	private PolicyTarget parseTarget(String actionsStr, String resourceTypesStr, String rolesStr) {
		Set<String> actions = parseCommaSeparated(actionsStr, Set.of("*"));
		Set<String> resourceTypes = parseCommaSeparated(resourceTypesStr, Set.of("*"));
		Set<String> roles = parseCommaSeparated(rolesStr, Set.of());
		return new PolicyTarget(actions, resourceTypes, roles);
	}

	private Set<String> parseCommaSeparated(String str, Set<String> defaultIfEmpty) {
		if (str == null || str.isBlank()) {
			return defaultIfEmpty;
		}
		Set<String> set = Arrays.stream(str.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
		return set.isEmpty() ? defaultIfEmpty : set;
	}
}
