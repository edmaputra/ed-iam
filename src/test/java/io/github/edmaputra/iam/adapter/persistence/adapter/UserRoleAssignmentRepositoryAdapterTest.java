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
import io.github.edmaputra.iam.adapter.persistence.entity.UserRoleAssignmentJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserRoleAssignmentJpaRepository;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserRoleAssignment;
import io.github.edmaputra.iam.domain.model.UserRoleAssignmentId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class UserRoleAssignmentRepositoryAdapterTest {

	private UserRoleAssignmentJpaRepository repository;
	private UserRoleAssignmentRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(UserRoleAssignmentJpaRepository.class);
		adapter = new UserRoleAssignmentRepositoryAdapter(repository);
	}

	@Test
	@DisplayName("Should find user role assignment by id")
	void shouldFindById() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID roleId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();

		UserRoleAssignmentJpaEntity entity = new UserRoleAssignmentJpaEntity(
				id,
				userId,
				roleId,
				tenantId,
				null,
				true,
				Instant.now());

		when(repository.findById(id)).thenReturn(Optional.of(entity));

		Optional<UserRoleAssignment> result = adapter.findById(new UserRoleAssignmentId(id));

		assertThat(result).isPresent();
		assertThat(result.get().isTenantWide()).isTrue();
		assertThat(result.get().getUserId().value()).isEqualTo(userId);
	}

	@Test
	@DisplayName("Should find all assignments by user id and tenant id")
	void shouldFindByUserAndTenant() {
		UUID userId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();

		UserRoleAssignmentJpaEntity entity = new UserRoleAssignmentJpaEntity(
				UUID.randomUUID(),
				userId,
				UUID.randomUUID(),
				tenantId,
				null,
				true,
				Instant.now());

		when(repository.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(entity));

		List<UserRoleAssignment> list = adapter.findAllByUserIdAndTenantId(new UserId(userId), new TenantId(tenantId));

		assertThat(list).hasSize(1);
	}
}
