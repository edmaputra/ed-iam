package io.github.edmaputra.iam.adapter.security.store;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.model.MagicLinkToken;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryMagicLinkTokenStore}.
 *
 * @author edmaputra
 * @since 0.5.0
 */
class InMemoryMagicLinkTokenStoreTest {

	private InMemoryMagicLinkTokenStore store;

	@BeforeEach
	void setUp() {
		store = new InMemoryMagicLinkTokenStore();
	}

	@Test
	@DisplayName("Should save, find, and consume token atomically")
	void shouldSaveFindAndConsume() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("secret-token", userId, tenantId, "user@test.com", now.plusSeconds(300), now);

		store.save(token);

		Optional<MagicLinkToken> found = store.findByToken("secret-token");
		assertThat(found).isPresent();
		assertThat(found.get().token()).isEqualTo("secret-token");

		// Consume
		Instant consumeTime = now.plusSeconds(10);
		Optional<MagicLinkToken> consumed = store.consume("secret-token", consumeTime);
		assertThat(consumed).isPresent();
		assertThat(consumed.get().isConsumed()).isTrue();
		assertThat(consumed.get().consumedAt()).isEqualTo(consumeTime);

		// Second consume should return empty
		Optional<MagicLinkToken> secondConsume = store.consume("secret-token", consumeTime.plusSeconds(5));
		assertThat(secondConsume).isEmpty();
	}

	@Test
	@DisplayName("Should not consume expired token")
	void shouldNotConsumeExpired() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		MagicLinkToken token = MagicLinkToken.issue("expired-token", userId, null, "user@test.com", now.minusSeconds(10), now.minusSeconds(60));

		store.save(token);

		Optional<MagicLinkToken> consumed = store.consume("expired-token", now);
		assertThat(consumed).isEmpty();
	}

	@Test
	@DisplayName("Should return empty when consuming non-existent token")
	void shouldReturnEmptyWhenNonExistent() {
		Optional<MagicLinkToken> consumed = store.consume("does-not-exist", Instant.now());
		assertThat(consumed).isEmpty();
	}

	@Test
	@DisplayName("Should delete expired or consumed tokens")
	void shouldDeleteExpiredTokens() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();

		MagicLinkToken expired = MagicLinkToken.issue("exp", userId, null, "exp@test.com", now.minusSeconds(5), now.minusSeconds(100));
		MagicLinkToken valid = MagicLinkToken.issue("val", userId, null, "val@test.com", now.plusSeconds(300), now);

		store.save(expired);
		store.save(valid);

		int deleted = store.deleteExpired(now);
		assertThat(deleted).isEqualTo(1);
		assertThat(store.findByToken("exp")).isEmpty();
		assertThat(store.findByToken("val")).isPresent();
	}

	@Test
	@DisplayName("Should enforce null checks")
	void shouldEnforceNullChecks() {
		assertThatThrownBy(() -> store.save(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> store.findByToken(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> store.consume(null, Instant.now())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> store.consume("tok", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> store.deleteExpired(null)).isInstanceOf(NullPointerException.class);
	}
}
