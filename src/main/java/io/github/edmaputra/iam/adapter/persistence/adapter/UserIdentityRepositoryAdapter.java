package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.edmaputra.iam.adapter.persistence.entity.UserIdentityJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserIdentityJpaRepository;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.model.UserIdentityId;
import io.github.edmaputra.iam.domain.repository.UserIdentityRepository;

/**
 * Persistence adapter implementing {@link UserIdentityRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class UserIdentityRepositoryAdapter implements UserIdentityRepository {

	private final UserIdentityJpaRepository repository;

	/**
	 * Constructs the adapter with the underlying Spring Data repository.
	 *
	 * @param repository the Spring Data repository
	 */
	public UserIdentityRepositoryAdapter(UserIdentityJpaRepository repository) {
		this.repository = Objects.requireNonNull(repository, "UserIdentityJpaRepository must not be null.");
	}

	@Override
	public Optional<UserIdentity> findById(UserIdentityId id) {
		Objects.requireNonNull(id, "UserIdentityId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<UserIdentity> findByProviderTypeAndExternalSubjectId(ProviderType providerType, String externalSubjectId) {
		Objects.requireNonNull(providerType, "ProviderType must not be null.");
		Objects.requireNonNull(externalSubjectId, "ExternalSubjectId must not be null.");
		return repository.findByProviderTypeAndExternalSubjectId(providerType.name(), externalSubjectId.trim())
				.map(this::toDomain);
	}

	@Override
	public List<UserIdentity> findAllByUserId(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return repository.findAllByUserId(userId.value()).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public UserIdentity save(UserIdentity identity) {
		Objects.requireNonNull(identity, "UserIdentity must not be null.");
		UserIdentityJpaEntity entity = toEntity(identity);
		UserIdentityJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public void delete(UserIdentityId id) {
		Objects.requireNonNull(id, "UserIdentityId must not be null.");
		repository.deleteById(id.value());
	}

	private UserIdentity toDomain(UserIdentityJpaEntity entity) {
		return new UserIdentity(
				new UserIdentityId(entity.getId()),
				new UserId(entity.getUserId()),
				ProviderType.valueOf(entity.getProviderType()),
				entity.getExternalSubjectId(),
				entity.getIssuerUrl(),
				entity.getCreatedAt());
	}

	private UserIdentityJpaEntity toEntity(UserIdentity identity) {
		return new UserIdentityJpaEntity(
				identity.getId().value(),
				identity.getUserId().value(),
				identity.getProviderType().name(),
				identity.getExternalSubjectId(),
				identity.optionalIssuerUrl().orElse(null),
				identity.getCreatedAt());
	}
}
