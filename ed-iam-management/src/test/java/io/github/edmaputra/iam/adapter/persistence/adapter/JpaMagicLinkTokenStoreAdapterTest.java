package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.persistence.entity.MagicLinkTokenJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.MagicLinkTokenJpaRepository;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JpaMagicLinkTokenStoreAdapter}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class JpaMagicLinkTokenStoreAdapterTest {

	@Mock
	private MagicLinkTokenJpaRepository jpaRepository;

	private JpaMagicLinkTokenStoreAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new JpaMagicLinkTokenStoreAdapter(jpaRepository);
	}

	@Test
	@DisplayName("Should save MagicLinkToken domain model to entity")
	void shouldSaveToken() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("tok-123", userId, tenantId, "user@test.com", now.plusSeconds(300), now);

		adapter.save(token);

		ArgumentCaptor<MagicLinkTokenJpaEntity> captor = ArgumentCaptor.forClass(MagicLinkTokenJpaEntity.class);
		verify(jpaRepository).save(captor.capture());
		MagicLinkTokenJpaEntity entity = captor.getValue();
		assertThat(entity.getId()).isEqualTo(token.id().value());
		assertThat(entity.getToken()).isEqualTo("tok-123");
		assertThat(entity.getUserId()).isEqualTo(userId.value());
		assertThat(entity.getTenantId()).isEqualTo(tenantId.value().toString());
		assertThat(entity.getEmail()).isEqualTo("user@test.com");
	}

	@Test
	@DisplayName("Should find token by secret token string")
	void shouldFindByToken() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		MagicLinkTokenJpaEntity entity = new MagicLinkTokenJpaEntity(
				id, "secret-tok", userId, null, "user@test.com", now.plusSeconds(300), null, now);

		when(jpaRepository.findByToken("secret-tok")).thenReturn(Optional.of(entity));

		Optional<MagicLinkToken> result = adapter.findByToken("secret-tok");
		assertThat(result).isPresent();
		assertThat(result.get().id().value()).isEqualTo(id);
		assertThat(result.get().token()).isEqualTo("secret-tok");
		assertThat(result.get().userId().value()).isEqualTo(userId);
		assertThat(result.get().tenantId()).isNull();
	}

	@Test
	@DisplayName("Should consume token when repository successfully marks as consumed")
	void shouldConsumeToken() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		MagicLinkTokenJpaEntity entity = new MagicLinkTokenJpaEntity(
				id, "tok-to-consume", userId, null, "user@test.com", now.plusSeconds(300), now, now);

		when(jpaRepository.markConsumed("tok-to-consume", now)).thenReturn(1);
		when(jpaRepository.findByToken("tok-to-consume")).thenReturn(Optional.of(entity));

		Optional<MagicLinkToken> consumed = adapter.consume("tok-to-consume", now);
		assertThat(consumed).isPresent();
		assertThat(consumed.get().isConsumed()).isTrue();
	}

	@Test
	@DisplayName("Should return empty when consume fails (already consumed or expired)")
	void shouldReturnEmptyWhenConsumeFails() {
		Instant now = Instant.now();
		when(jpaRepository.markConsumed("already-used", now)).thenReturn(0);

		Optional<MagicLinkToken> consumed = adapter.consume("already-used", now);
		assertThat(consumed).isEmpty();
	}

	@Test
	@DisplayName("Should delete expired tokens")
	void shouldDeleteExpired() {
		Instant now = Instant.now();
		when(jpaRepository.deleteExpired(now)).thenReturn(5);

		int deleted = adapter.deleteExpired(now);
		assertThat(deleted).isEqualTo(5);
		verify(jpaRepository).deleteExpired(now);
	}

	@Test
	@DisplayName("Should enforce null checks")
	void shouldEnforceNullChecks() {
		assertThatThrownBy(() -> adapter.save(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.findByToken(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.consume(null, Instant.now())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.consume("tok", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.deleteExpired(null)).isInstanceOf(NullPointerException.class);
	}
}
