package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.adapter.persistence.entity.PasswordPolicyJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.PasswordPolicyJpaRepository;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordPolicyRepositoryAdapter}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class PasswordPolicyRepositoryAdapterTest {

	private PasswordPolicyJpaRepository jpaRepository;
	private PasswordPolicyRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		jpaRepository = mock(PasswordPolicyJpaRepository.class);
		adapter = new PasswordPolicyRepositoryAdapter(jpaRepository);
	}

	@Test
	@DisplayName("findByTenantId maps entity to domain record")
	void findByTenantIdReturnsDomain() {
		UUID tenantUuid = UUID.randomUUID();
		PasswordPolicyJpaEntity entity = new PasswordPolicyJpaEntity(
				UUID.randomUUID(),
				tenantUuid,
				12,
				64,
				2,
				2,
				2,
				2,
				"^[A-Za-z0-9]+$",
				"Alphanumeric only",
				true,
				Instant.now(),
				Instant.now()
		);
		when(jpaRepository.findByTenantId(tenantUuid)).thenReturn(Optional.of(entity));

		Optional<PasswordPolicy> result = adapter.findByTenantId(new TenantId(tenantUuid));

		assertThat(result).isPresent();
		PasswordPolicy policy = result.get();
		assertThat(policy.minLength()).isEqualTo(12);
		assertThat(policy.maxLength()).isEqualTo(64);
		assertThat(policy.minUppercase()).isEqualTo(2);
		assertThat(policy.minLowercase()).isEqualTo(2);
		assertThat(policy.minNumbers()).isEqualTo(2);
		assertThat(policy.minSpecialCharacters()).isEqualTo(2);
		assertThat(policy.customRegex()).isEqualTo("^[A-Za-z0-9]+$");
		assertThat(policy.regexDescription()).isEqualTo("Alphanumeric only");
		assertThat(policy.disallowUsername()).isTrue();
	}

	@Test
	@DisplayName("findByTenantId queries findByTenantIdIsNull when tenantId is null")
	void findByTenantIdIsNullWhenNullTenant() {
		when(jpaRepository.findByTenantIdIsNull()).thenReturn(Optional.empty());

		Optional<PasswordPolicy> result = adapter.findByTenantId(null);

		assertThat(result).isEmpty();
		verify(jpaRepository).findByTenantIdIsNull();
	}

	@Test
	@DisplayName("save creates new entity if not existing")
	void saveCreatesNewEntity() {
		UUID tenantUuid = UUID.randomUUID();
		TenantId tenantId = new TenantId(tenantUuid);
		PasswordPolicy policy = new PasswordPolicy(10, 50, 1, 1, 1, 1, null, null, true);

		when(jpaRepository.findByTenantId(tenantUuid)).thenReturn(Optional.empty());
		when(jpaRepository.save(any(PasswordPolicyJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PasswordPolicy saved = adapter.save(tenantId, policy);

		assertThat(saved).isEqualTo(policy);
		ArgumentCaptor<PasswordPolicyJpaEntity> captor = ArgumentCaptor.forClass(PasswordPolicyJpaEntity.class);
		verify(jpaRepository).save(captor.capture());
		PasswordPolicyJpaEntity captured = captor.getValue();
		assertThat(captured.getTenantId()).isEqualTo(tenantUuid);
		assertThat(captured.getMinLength()).isEqualTo(10);
	}
}
