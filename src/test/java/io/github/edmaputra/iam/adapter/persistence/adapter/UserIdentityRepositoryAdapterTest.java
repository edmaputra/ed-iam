package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.adapter.persistence.entity.UserIdentityJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserIdentityJpaRepository;
import io.github.edmaputra.iam.domain.model.ProviderType;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserIdentity;
import io.github.edmaputra.iam.domain.model.UserIdentityId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserIdentityRepositoryAdapterTest {

	private UserIdentityJpaRepository repository;
	private UserIdentityRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(UserIdentityJpaRepository.class);
		adapter = new UserIdentityRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should find identity by id and map properly")
	void shouldFindById() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UserIdentityJpaEntity entity = new UserIdentityJpaEntity(
				id,
				userId,
				"OIDC_GENERIC",
				"sub-12345",
				"https://auth.hospital.org",
				Instant.now());

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<UserIdentity> result = adapter.findById(new UserIdentityId(id));

		assertThat(result).isPresent();
		assertThat(result.get().getProviderType()).isEqualTo(ProviderType.OIDC_GENERIC);
		assertThat(result.get().getExternalSubjectId()).isEqualTo("sub-12345");
		assertThat(result.get().optionalIssuerUrl()).contains("https://auth.hospital.org");
	}

	@Test
	@DisplayName("Should find identity by provider type and external subject id")
	void shouldFindByProviderAndSubject() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UserIdentityJpaEntity entity = new UserIdentityJpaEntity(
				id,
				userId,
				"OIDC_GENERIC",
				"sub-12345",
				"https://auth.hospital.org",
				Instant.now());

		when(repository.findByProviderTypeAndExternalSubjectId("OIDC_GENERIC", "sub-12345"))
				.thenReturn(Optional.of(entity));

		Optional<UserIdentity> result = adapter.findByProviderTypeAndExternalSubjectId(ProviderType.OIDC_GENERIC, "sub-12345");

		assertThat(result).isPresent();
		assertThat(result.get().getUserId().value()).isEqualTo(userId);
	}

	@Test
	@DisplayName("Should save identity and map properly")
	void shouldSaveIdentity() {
		UserIdentity identity = UserIdentity.create(
				UserId.generate(),
				ProviderType.OIDC_GENERIC,
				"sub-999",
				"https://accounts.google.com");

		UserIdentityJpaEntity savedEntity = new UserIdentityJpaEntity(
				identity.getId().value(),
				identity.getUserId().value(),
				identity.getProviderType().name(),
				identity.getExternalSubjectId(),
				identity.optionalIssuerUrl().orElse(null),
				identity.getCreatedAt());

		when(repository.save(any(UserIdentityJpaEntity.class))).thenReturn(savedEntity);

		UserIdentity saved = adapter.save(identity);

		assertThat(saved).isNotNull();
		assertThat(saved.getExternalSubjectId()).isEqualTo("sub-999");
	}

	@Test
	@DisplayName("Should delete identity by id")
	void shouldDeleteIdentity() {
		UserIdentityId identityId = UserIdentityId.generate();
		adapter.delete(identityId);
		verify(repository).deleteById(identityId.value());
	}
}
