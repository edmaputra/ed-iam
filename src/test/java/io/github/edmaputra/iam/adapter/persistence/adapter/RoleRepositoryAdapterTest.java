package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.RoleJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.RoleJpaRepository;
import io.github.edmaputra.iam.domain.model.Role;
import io.github.edmaputra.iam.domain.model.RoleId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RoleRepositoryAdapterTest {

	private RoleJpaRepository repository;
	private RoleRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(RoleJpaRepository.class);
		adapter = new RoleRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should find role by id and map properly")
	void shouldFindById() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Instant now = Instant.now();

		RoleJpaEntity entity = new RoleJpaEntity(
				id,
				tenantId,
				"ADMIN",
				"Administrator",
				"Admin role",
				false,
				Set.of("PATIENT_READ"),
				now,
				now);

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<Role> result = adapter.findById(new RoleId(id));

		assertThat(result).isPresent();
		assertThat(result.get().getCode()).isEqualTo("ADMIN");
		assertThat(result.get().permissions()).containsExactly("PATIENT_READ");
	}

	@Test
	@DisplayName("Should find all roles by tenant id or global")
	void shouldFindAllByTenantIdOrGlobal() {
		TenantId tenantId = TenantId.generate();
		RoleJpaEntity entity = new RoleJpaEntity(
				UUID.randomUUID(),
				tenantId.value(),
				"ADMIN",
				"Admin",
				null,
				false,
				Set.of(),
				Instant.now(),
				Instant.now());

		when(repository.findAllByTenantIdOrGlobal(tenantId.value())).thenReturn(List.of(entity));

		List<Role> roles = adapter.findAllByTenantIdOrGlobal(tenantId);

		assertThat(roles).hasSize(1);
		assertThat(roles.getFirst().getCode()).isEqualTo("ADMIN");
	}
}
