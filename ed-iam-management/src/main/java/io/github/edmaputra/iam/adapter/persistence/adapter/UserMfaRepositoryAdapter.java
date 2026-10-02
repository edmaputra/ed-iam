package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import io.github.edmaputra.iam.adapter.persistence.entity.UserMfaJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserMfaJpaRepository;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;

/**
 * Persistence adapter implementing {@link UserMfaRepository} backed by Spring Data JPA.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserMfaRepositoryAdapter implements UserMfaRepository {

	private final UserMfaJpaRepository repository;

	@Override
	public Optional<UserMfa> findByUserId(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		return repository.findById(userId.value()).map(this::toDomain);
	}

	@Override
	@Transactional
	public UserMfa save(UserMfa userMfa) {
		Objects.requireNonNull(userMfa, "UserMfa must not be null.");
		UserMfaJpaEntity entity = toEntity(userMfa);
		UserMfaJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	@Transactional
	public void deleteByUserId(UserId userId) {
		Objects.requireNonNull(userId, "UserId must not be null.");
		repository.deleteById(userId.value());
	}

	private UserMfa toDomain(UserMfaJpaEntity entity) {
		List<String> backupCodes = (entity.getBackupCodes() == null || entity.getBackupCodes().isBlank())
				? List.of()
				: Arrays.stream(entity.getBackupCodes().split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList();

		return new UserMfa(
				new UserId(entity.getUserId()),
				entity.getSecret(),
				entity.isEnabled(),
				backupCodes,
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private UserMfaJpaEntity toEntity(UserMfa userMfa) {
		String serializedBackupCodes = String.join(",", userMfa.backupCodes());
		return new UserMfaJpaEntity(
				userMfa.getUserId().value(),
				userMfa.getSecret(),
				userMfa.isEnabled(),
				serializedBackupCodes,
				userMfa.getCreatedAt(),
				userMfa.getUpdatedAt());
	}
}
