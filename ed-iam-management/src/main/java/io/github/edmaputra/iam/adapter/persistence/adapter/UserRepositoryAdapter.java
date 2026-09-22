package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.UserJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserJpaRepository;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserRepository;

/**
 * Persistence adapter implementing {@link UserRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements UserRepository {

	private final UserJpaRepository repository;

	@Override
	public Optional<User> findById(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		return repository.findById(id.value()).map(this::toDomain);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		return repository.findByEmailIgnoreCase(email.trim()).map(this::toDomain);
	}

	@Override
	public boolean existsByEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		return repository.existsByEmailIgnoreCase(email.trim());
	}

	@Override
	@Transactional
	public User save(User user) {
		Objects.requireNonNull(user, "User must not be null.");
		UserJpaEntity entity = toEntity(user);
		UserJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	@Transactional
	public void delete(UserId id) {
		Objects.requireNonNull(id, "UserId must not be null.");
		repository.deleteById(id.value());
	}

	private User toDomain(UserJpaEntity entity) {
		return new User(
				new UserId(entity.getId()),
				entity.getEmail(),
				entity.getPasswordHash(),
				entity.getFullName(),
				UserStatus.valueOf(entity.getStatus()),
				entity.isPlatformSuperAdmin(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private UserJpaEntity toEntity(User user) {
		return new UserJpaEntity(
				user.getId().value(),
				user.getEmail(),
				user.getPasswordHash(),
				user.getFullName(),
				user.getStatus().name(),
				user.isPlatformSuperAdmin(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
