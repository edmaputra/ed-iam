package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.auth.AuthCredentialType;
import io.github.edmaputra.iam.domain.auth.MagicLinkAuthCredentials;
import io.github.edmaputra.iam.domain.exception.MagicLinkConsumedException;
import io.github.edmaputra.iam.domain.exception.MagicLinkExpiredException;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying domain invariants for {@link MagicLinkToken}, {@link MagicLinkId},
 * and {@link MagicLinkAuthCredentials}.
 *
 * @author edmaputra
 * @since 0.6.0
 */
class MagicLinkTokenTest {

	@Test
	@DisplayName("Should create valid MagicLinkToken and verify lifecycle states")
	void shouldCreateAndVerifyLifecycle() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(900);

		MagicLinkToken token = MagicLinkToken.issue("test-token-123", userId, tenantId, "user@example.com", expiresAt, now);

		assertThat(token.id()).isNotNull();
		assertThat(token.token()).isEqualTo("test-token-123");
		assertThat(token.userId()).isEqualTo(userId);
		assertThat(token.tenantId()).isEqualTo(tenantId);
		assertThat(token.email()).isEqualTo("user@example.com");
		assertThat(token.expiresAt()).isEqualTo(expiresAt);
		assertThat(token.consumedAt()).isNull();
		assertThat(token.createdAt()).isEqualTo(now);

		assertThat(token.isConsumed()).isFalse();
		assertThat(token.isExpired(now)).isFalse();
		assertThat(token.isValid(now)).isTrue();

		// Consume
		Instant consumeTime = now.plusSeconds(30);
		MagicLinkToken consumed = token.consume(consumeTime);
		assertThat(consumed.isConsumed()).isTrue();
		assertThat(consumed.consumedAt()).isEqualTo(consumeTime);
		assertThat(consumed.isValid(consumeTime)).isFalse();

		// Double consume should fail
		assertThatThrownBy(() -> consumed.consume(consumeTime.plusSeconds(1)))
				.isInstanceOf(MagicLinkConsumedException.class)
				.hasMessageContaining("already been consumed");
	}

	@Test
	@DisplayName("Should throw MagicLinkExpiredException when consuming expired token")
	void shouldThrowWhenConsumingExpired() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(60);

		MagicLinkToken token = MagicLinkToken.issue("test-token-456", userId, null, "user@example.com", expiresAt, now);

		Instant afterExpiry = now.plusSeconds(61);
		assertThat(token.isExpired(afterExpiry)).isTrue();
		assertThat(token.isValid(afterExpiry)).isFalse();

		assertThatThrownBy(() -> token.consume(afterExpiry))
				.isInstanceOf(MagicLinkExpiredException.class)
				.hasMessageContaining("expired");
	}

	@Test
	@DisplayName("Should enforce invariants on MagicLinkToken constructor")
	void shouldEnforceInvariantsOnMagicLinkToken() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();

		assertThatThrownBy(() -> new MagicLinkToken(null, "t", userId, null, "a@b.com", now, null, now))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), null, userId, null, "a@b.com", now, null, now))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), " ", userId, null, "a@b.com", now, null, now))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), "t", null, null, "a@b.com", now, null, now))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), "t", userId, null, null, now, null, now))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), "t", userId, null, " ", now, null, now))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), "t", userId, null, "a@b.com", null, null, now))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkToken(MagicLinkId.generate(), "t", userId, null, "a@b.com", now, null, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Should create MagicLinkId via factory methods")
	void shouldCreateMagicLinkId() {
		MagicLinkId generated = MagicLinkId.generate();
		assertThat(generated.value()).isNotNull();
		assertThat(generated.toString()).isEqualTo(generated.value().toString());

		UUID rawUuid = UUID.randomUUID();
		MagicLinkId fromUuid = MagicLinkId.of(rawUuid);
		assertThat(fromUuid.value()).isEqualTo(rawUuid);

		MagicLinkId fromString = MagicLinkId.of(rawUuid.toString());
		assertThat(fromString).isEqualTo(fromUuid);

		assertThatThrownBy(() -> new MagicLinkId(null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> MagicLinkId.of((String) null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Should validate MagicLinkAuthCredentials")
	void shouldValidateMagicLinkAuthCredentials() {
		MagicLinkAuthCredentials credentials = new MagicLinkAuthCredentials("token-xyz");
		assertThat(credentials.token()).isEqualTo("token-xyz");
		assertThat(credentials.credentialType()).isEqualTo(AuthCredentialType.MAGIC_LINK);

		assertThatThrownBy(() -> new MagicLinkAuthCredentials(null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MagicLinkAuthCredentials("   "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
