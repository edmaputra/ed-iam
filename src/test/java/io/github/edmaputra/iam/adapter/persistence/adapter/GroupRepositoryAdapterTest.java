package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.edmaputra.iam.domain.tenancy.TenantId;
import io.github.edmaputra.iam.adapter.persistence.entity.GroupJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.GroupJpaRepository;
import io.github.edmaputra.iam.domain.model.Group;
import io.github.edmaputra.iam.domain.model.GroupId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GroupRepositoryAdapterTest {

	private GroupJpaRepository repository;
	private GroupRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(GroupJpaRepository.class);
		adapter = new GroupRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should find group by id and map properly")
	void shouldFindById() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Instant now = Instant.now();

		GroupJpaEntity entity = new GroupJpaEntity(
				id,
				tenantId,
				"SURGERY",
				"Surgery Team",
				"Description",
				"IDP_GROUP",
				now,
				now);

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<Group> result = adapter.findById(new GroupId(id));

		assertThat(result).isPresent();
		assertThat(result.get().getCode()).isEqualTo("SURGERY");
		assertThat(result.get().getName()).isEqualTo("Surgery Team");
		assertThat(result.get().optionalExternalIdpGroupName()).contains("IDP_GROUP");
	}

	@Test
	@DisplayName("Should find all groups by tenant id")
	void shouldFindAllByTenantId() {
		TenantId tenantId = TenantId.generate();
		GroupJpaEntity entity = new GroupJpaEntity(
				UUID.randomUUID(),
				tenantId.value(),
				"SURGERY",
				"Surgery Team",
				null,
				null,
				Instant.now(),
				Instant.now());

		when(repository.findAllByTenantId(tenantId.value())).thenReturn(List.of(entity));

		List<Group> groups = adapter.findAllByTenantId(tenantId);

		assertThat(groups).hasSize(1);
		assertThat(groups.getFirst().getCode()).isEqualTo("SURGERY");
	}
}
