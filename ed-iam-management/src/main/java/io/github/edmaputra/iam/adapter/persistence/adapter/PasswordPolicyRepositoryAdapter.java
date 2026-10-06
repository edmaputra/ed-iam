package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.PasswordPolicyJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PasswordPolicyJpaRepository;
import io.github.edmaputra.iam.domain.repository.PasswordPolicyRepository;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.domain.util.UuidV7;

/**
 * Persistence adapter implementing {@link PasswordPolicyRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordPolicyRepositoryAdapter implements PasswordPolicyRepository {

	private final PasswordPolicyJpaRepository repository;

	@Override
	public Optional<PasswordPolicy> findByTenantId(TenantId tenantId) {
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;
		Optional<PasswordPolicyJpaEntity> entity = tenantUuid != null
				? repository.findByTenantId(tenantUuid)
				: repository.findByTenantIdIsNull();
		return entity.map(this::toDomain);
	}

	@Override
	@Transactional
	public PasswordPolicy save(TenantId tenantId, PasswordPolicy policy) {
		Objects.requireNonNull(policy, "PasswordPolicy must not be null.");
		UUID tenantUuid = tenantId != null ? tenantId.value() : null;
		Optional<PasswordPolicyJpaEntity> existing = tenantUuid != null
				? repository.findByTenantId(tenantUuid)
				: repository.findByTenantIdIsNull();

		PasswordPolicyJpaEntity entity = existing.orElseGet(PasswordPolicyJpaEntity::new);
		if (entity.getId() == null) {
			entity.setId(UuidV7.generate());
			entity.setTenantId(tenantUuid);
			entity.setCreatedAt(Instant.now());
		}
		entity.setMinLength(policy.minLength());
		entity.setMaxLength(policy.maxLength());
		entity.setMinUppercase(policy.minUppercase());
		entity.setMinLowercase(policy.minLowercase());
		entity.setMinNumbers(policy.minNumbers());
		entity.setMinSpecialCharacters(policy.minSpecialCharacters());
		entity.setCustomRegex(policy.customRegex());
		entity.setRegexDescription(policy.regexDescription());
		entity.setDisallowUsername(policy.disallowUsername());
		entity.setUpdatedAt(Instant.now());

		PasswordPolicyJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	private PasswordPolicy toDomain(PasswordPolicyJpaEntity entity) {
		return new PasswordPolicy(
				entity.getMinLength(),
				entity.getMaxLength(),
				entity.getMinUppercase(),
				entity.getMinLowercase(),
				entity.getMinNumbers(),
				entity.getMinSpecialCharacters(),
				entity.getCustomRegex(),
				entity.getRegexDescription(),
				entity.isDisallowUsername()
		);
	}
}
