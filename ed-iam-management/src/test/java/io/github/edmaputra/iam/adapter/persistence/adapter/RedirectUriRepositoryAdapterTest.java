package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.adapter.persistence.entity.RedirectUriJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.RedirectUriJpaRepository;
import io.github.edmaputra.iam.domain.security.RedirectUriPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedirectUriRepositoryAdapter}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class RedirectUriRepositoryAdapterTest {

	private RedirectUriJpaRepository jpaRepository;
	private RedirectUriRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		jpaRepository = mock(RedirectUriJpaRepository.class);
		adapter = new RedirectUriRepositoryAdapter(jpaRepository);
	}

	@Test
	@DisplayName("findByTenantId maps entities to domain RedirectUriPolicy")
	void findByTenantIdMapsToPolicy() {
		UUID tenantUuid = UUID.randomUUID();
		RedirectUriJpaEntity entity1 = new RedirectUriJpaEntity(UUID.randomUUID(), tenantUuid, "https://app1.org", Instant.now(), Instant.now());
		RedirectUriJpaEntity entity2 = new RedirectUriJpaEntity(UUID.randomUUID(), tenantUuid, "https://app2.org", Instant.now(), Instant.now());

		when(jpaRepository.findByTenantId(tenantUuid)).thenReturn(List.of(entity1, entity2));

		RedirectUriPolicy policy = adapter.findByTenantId(new TenantId(tenantUuid));

		assertThat(policy.allowedUris()).containsExactlyInAnyOrder("https://app1.org", "https://app2.org");
	}

	@Test
	@DisplayName("save deletes existing entities and inserts new policy URIs")
	void saveDeletesAndInserts() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		RedirectUriPolicy policy = RedirectUriPolicy.of("https://app.org", "portal.org");

		RedirectUriPolicy result = adapter.save(tenantId, policy);

		assertThat(result.allowedUris()).containsExactlyInAnyOrder("https://app.org", "portal.org");
		verify(jpaRepository).deleteByTenantId(tenantUuid);
		verify(jpaRepository, org.mockito.Mockito.times(2)).save(any(RedirectUriJpaEntity.class));
	}

	@Test
	@DisplayName("addUri saves entity if not already present")
	void addUriInsertsWhenAbsent() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		when(jpaRepository.findByTenantIdAndUriIgnoreCase(tenantUuid, "https://new.org")).thenReturn(Optional.empty());

		adapter.addUri(tenantId, "https://new.org");

		ArgumentCaptor<RedirectUriJpaEntity> captor = ArgumentCaptor.forClass(RedirectUriJpaEntity.class);
		verify(jpaRepository).save(captor.capture());
		assertThat(captor.getValue().getUri()).isEqualTo("https://new.org");
		assertThat(captor.getValue().getTenantId()).isEqualTo(tenantUuid);
	}

	@Test
	@DisplayName("addUri skips save when already present")
	void addUriSkipsWhenPresent() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		when(jpaRepository.findByTenantIdAndUriIgnoreCase(tenantUuid, "https://existing.org"))
				.thenReturn(Optional.of(new RedirectUriJpaEntity()));

		adapter.addUri(tenantId, "https://existing.org");

		verify(jpaRepository, never()).save(any());
	}

	@Test
	@DisplayName("removeUri invokes deleteByTenantIdAndUriIgnoreCase")
	void removeUriDeletesEntity() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);

		adapter.removeUri(tenantId, "https://remove.org");

		verify(jpaRepository).deleteByTenantIdAndUriIgnoreCase(tenantUuid, "https://remove.org");
	}
}
