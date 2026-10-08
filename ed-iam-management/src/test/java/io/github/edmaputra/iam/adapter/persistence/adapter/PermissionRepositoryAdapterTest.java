package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.adapter.persistence.entity.PermissionJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PermissionJpaRepository;
import io.github.edmaputra.iam.domain.model.Permission;
import io.github.edmaputra.iam.domain.model.PermissionId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PermissionRepositoryAdapter}.
 *
 * @author edmaputra
 * @since 0.10.0
 */
class PermissionRepositoryAdapterTest {

	private PermissionJpaRepository repository;
	private PermissionRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = mock(PermissionJpaRepository.class);
		adapter = new PermissionRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("findById maps entity to domain correctly")
	void findByIdMapsToDomain() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Instant now = Instant.now();
		PermissionJpaEntity entity = new PermissionJpaEntity(
				id, tenantId, "user:read", "Read User", "Desc", "USER", false, now, now);

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<Permission> result = adapter.findById(new PermissionId(id));
		assertThat(result).isPresent();
		assertThat(result.get().id().value()).isEqualTo(id);
		assertThat(result.get().tenantId().value()).isEqualTo(tenantId);
		assertThat(result.get().code()).isEqualTo("user:read");
		assertThat(result.get().name()).isEqualTo("Read User");
		assertThat(result.get().description()).isEqualTo("Desc");
		assertThat(result.get().category()).isEqualTo("USER");
		assertThat(result.get().systemPermission()).isFalse();
	}

	@Test
	@DisplayName("save maps domain to entity and returns saved domain")
	void saveMapsToEntityAndBack() {
		TenantId tenantId = TenantId.generate();
		Permission domain = Permission.createCustom(tenantId, "perm:save", "Save Perm", "Desc", "CAT");

		when(repository.save(any(PermissionJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Permission saved = adapter.save(domain);
		assertThat(saved.code()).isEqualTo("perm:save");
		assertThat(saved.name()).isEqualTo("Save Perm");

		ArgumentCaptor<PermissionJpaEntity> captor = ArgumentCaptor.forClass(PermissionJpaEntity.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getCode()).isEqualTo("perm:save");
		assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId.value());
	}

	@Test
	@DisplayName("findAllByTenantIdOrGlobal delegates to repository")
	void findAllByTenantIdOrGlobalDelegates() {
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		PermissionJpaEntity entity = new PermissionJpaEntity(
				UUID.randomUUID(), tenantId.value(), "perm:1", "Perm 1", null, "CAT", false, now, now);

		when(repository.findAllByTenantIdOrGlobal(tenantId.value())).thenReturn(List.of(entity));

		List<Permission> list = adapter.findAllByTenantIdOrGlobal(tenantId);
		assertThat(list).hasSize(1);
		assertThat(list.get(0).code()).isEqualTo("perm:1");
	}

	@Test
	@DisplayName("delete invokes repository deleteById")
	void deleteInvokesRepository() {
		PermissionId id = PermissionId.generate();
		adapter.delete(id);
		verify(repository).deleteById(id.value());
	}
}
