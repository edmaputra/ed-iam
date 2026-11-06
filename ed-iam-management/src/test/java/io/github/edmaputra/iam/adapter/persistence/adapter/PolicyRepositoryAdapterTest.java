package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.adapter.persistence.entity.PolicyJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PolicyJpaRepository;
import io.github.edmaputra.iam.domain.policy.Policy;
import io.github.edmaputra.iam.domain.policy.PolicyEffect;
import io.github.edmaputra.iam.domain.policy.PolicyId;
import io.github.edmaputra.iam.domain.policy.PolicyTarget;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PolicyRepositoryAdapter}.
 *
 * @author edmaputra
 * @since 0.14.0
 */
class PolicyRepositoryAdapterTest {

	private PolicyJpaRepository repository;
	private PolicyRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = mock(PolicyJpaRepository.class);
		PolicyConstraintJsonConverter converter = new PolicyConstraintJsonConverter(new ObjectMapper());
		adapter = new PolicyRepositoryAdapter(repository, converter);
	}

	@Test
	@DisplayName("findById maps entity to domain correctly")
	void findByIdMapsToDomain() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Instant now = Instant.now();
		PolicyJpaEntity entity = new PolicyJpaEntity(
				id, tenantId, "MedicalRecordPolicy", "Desc", "PERMIT", 100, true,
				"read,write", "patient_record", "DOCTOR", "[]", now, now
		);

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<Policy> result = adapter.findById(new PolicyId(id));
		assertThat(result).isPresent();
		assertThat(result.get().id().value()).isEqualTo(id);
		assertThat(result.get().tenantId().value()).isEqualTo(tenantId);
		assertThat(result.get().name()).isEqualTo("MedicalRecordPolicy");
		assertThat(result.get().effect()).isEqualTo(PolicyEffect.PERMIT);
		assertThat(result.get().priority()).isEqualTo(100);
		assertThat(result.get().enabled()).isTrue();
		assertThat(result.get().target().actions()).containsExactlyInAnyOrder("read", "write");
		assertThat(result.get().target().resourceTypes()).containsExactly("patient_record");
		assertThat(result.get().target().roles()).containsExactly("DOCTOR");
	}

	@Test
	@DisplayName("save maps domain to entity and returns saved policy")
	void saveMapsToEntity() {
		UUID id = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Policy policy = new Policy(
				new PolicyId(id),
				new TenantId(tenantId),
				"SavePolicy",
				"Desc",
				PolicyEffect.DENY,
				50,
				true,
				new PolicyTarget(Set.of("delete"), Set.of("user"), Set.of("ADMIN")),
				List.of(),
				Instant.now(),
				Instant.now()
		);

		when(repository.save(any(PolicyJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Policy saved = adapter.save(policy);
		assertThat(saved.id().value()).isEqualTo(id);
		assertThat(saved.effect()).isEqualTo(PolicyEffect.DENY);

		ArgumentCaptor<PolicyJpaEntity> captor = ArgumentCaptor.forClass(PolicyJpaEntity.class);
		verify(repository).save(captor.capture());
		PolicyJpaEntity captured = captor.getValue();
		assertThat(captured.getId()).isEqualTo(id);
		assertThat(captured.getTenantId()).isEqualTo(tenantId);
		assertThat(captured.getName()).isEqualTo("SavePolicy");
		assertThat(captured.getEffect()).isEqualTo("DENY");
		assertThat(captured.getPriority()).isEqualTo(50);
		assertThat(captured.getTargetActions()).isEqualTo("delete");
	}

	@Test
	@DisplayName("deleteById delegates to repository")
	void deleteByIdDelegates() {
		UUID id = UUID.randomUUID();
		adapter.deleteById(new PolicyId(id));
		verify(repository).deleteById(id);
	}
}
