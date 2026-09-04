package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.adapter.persistence.entity.UserJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserJpaRepository;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRepositoryAdapterTest {

	private UserJpaRepository repository;
	private UserRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(UserJpaRepository.class);
		adapter = new UserRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should find user by id and map properly")
	void shouldFindById() {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		UserJpaEntity entity = new UserJpaEntity(
				id,
				"doc@hospital.org",
				"hash",
				"Dr. Alice",
				"ACTIVE",
				false,
				now,
				now);

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<User> result = adapter.findById(new UserId(id));

		assertThat(result).isPresent();
		assertThat(result.get().getId().value()).isEqualTo(id);
		assertThat(result.get().getEmail()).isEqualTo("doc@hospital.org");
		assertThat(result.get().getFullName()).isEqualTo("Dr. Alice");
		assertThat(result.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("Should save user and map properly")
	void shouldSaveUser() {
		User user = User.create("doc@hospital.org", "hash", "Dr. Alice", false);
		UserJpaEntity savedEntity = new UserJpaEntity(
				user.getId().value(),
				user.getEmail(),
				user.getPasswordHash(),
				user.getFullName(),
				user.getStatus().name(),
				user.isPlatformSuperAdmin(),
				user.getCreatedAt(),
				user.getUpdatedAt());

		when(repository.save(any(UserJpaEntity.class))).thenReturn(savedEntity);

		User saved = adapter.save(user);

		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(user.getId());
		verify(repository).save(any(UserJpaEntity.class));
	}

	@Test
	@DisplayName("Should delete user by id")
	void shouldDeleteUser() {
		UserId userId = UserId.generate();
		adapter.delete(userId);
		verify(repository).deleteById(userId.value());
	}
}
