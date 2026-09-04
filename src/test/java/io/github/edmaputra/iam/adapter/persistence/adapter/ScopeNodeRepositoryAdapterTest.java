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
import io.github.edmaputra.iam.adapter.persistence.entity.ScopeNodeEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.ScopeNodeJpaRepository;
import io.github.edmaputra.iam.domain.model.ScopeNode;
import io.github.edmaputra.iam.domain.model.ScopeNodeId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopeNodeRepositoryAdapterTest {

	private ScopeNodeJpaRepository repository;
	private ScopeNodeRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(ScopeNodeJpaRepository.class);
		adapter = new ScopeNodeRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should save and map ScopeNode to JPA entity and back")
	void shouldSaveAndMapScopeNode() {
		TenantId tenantId = TenantId.generate();
		ScopeNode node = ScopeNode.createRoot(tenantId, "MAIN", "Main Facility");

		when(repository.save(any(ScopeNodeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScopeNode saved = adapter.save(node);

		assertThat(saved.getId()).isEqualTo(node.getId());
		assertThat(saved.getTenantId()).isEqualTo(tenantId);
		assertThat(saved.getCode()).isEqualTo("MAIN");
		assertThat(saved.getPath()).isEqualTo(node.getPath());
		verify(repository).save(any(ScopeNodeEntity.class));
	}

	@Test
	@DisplayName("Should find by ID and tenant code")
	void shouldFindByIdAndTenantCode() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		ScopeNodeEntity entity = new ScopeNodeEntity(
				id,
				tenantId,
				null,
				"MAIN",
				"Main Facility",
				"/" + tenantId + "/" + id + "/",
				Instant.now(),
				Instant.now());

		when(repository.findById(id)).thenReturn(Optional.of(entity));
		when(repository.findByTenantIdAndCode(tenantId, "MAIN")).thenReturn(Optional.of(entity));

		Optional<ScopeNode> byId = adapter.findById(ScopeNodeId.of(id));
		assertThat(byId).isPresent();
		assertThat(byId.get().getCode()).isEqualTo("MAIN");

		Optional<ScopeNode> byCode = adapter.findByTenantIdAndCode(TenantId.from(tenantId.toString()), "main");
		assertThat(byCode).isPresent();
		assertThat(byCode.get().getCode()).isEqualTo("MAIN");
	}

	@Test
	@DisplayName("Should find descendants by path prefix")
	void shouldFindDescendantsByPathPrefix() {
		String prefix = "/tenant-1/root-1/";
		UUID tenantId = UUID.randomUUID();
		ScopeNodeEntity child = new ScopeNodeEntity(
				UUID.randomUUID(),
				tenantId,
				UUID.randomUUID(),
				"CHILD",
				"Child",
				prefix + "child-1/",
				Instant.now(),
				Instant.now());

		when(repository.findByPathStartingWith(prefix)).thenReturn(List.of(child));

		List<ScopeNode> descendants = adapter.findDescendantsByPathPrefix(prefix);
		assertThat(descendants).hasSize(1);
		assertThat(descendants.getFirst().getCode()).isEqualTo("CHILD");
	}

	@Test
	@DisplayName("Should delegate path prefix updates and delete")
	void shouldDelegateUpdatesAndDelete() {
		adapter.updatePathPrefix("/old/", "/new/");
		verify(repository).updatePathPrefix(any(), any(), any());

		ScopeNodeId nodeId = ScopeNodeId.generate();
		adapter.delete(nodeId);
		verify(repository).deleteById(nodeId.value());
	}
}
