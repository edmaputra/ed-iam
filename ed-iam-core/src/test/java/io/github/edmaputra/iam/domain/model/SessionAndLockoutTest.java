package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.exception.AccountLockedException;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying session and lockout domain models.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class SessionAndLockoutTest {

	@Test
	@DisplayName("SessionId should validate invariants and support generation and parsing")
	void testSessionId() {
		SessionId id = SessionId.generate();
		assertThat(id.value()).isNotNull();
		assertThat(id.toString()).isEqualTo(id.value().toString());

		UUID uuid = UUID.randomUUID();
		SessionId fromUuid = SessionId.of(uuid);
		assertThat(fromUuid.value()).isEqualTo(uuid);

		SessionId fromStr = SessionId.of(uuid.toString());
		assertThat(fromStr).isEqualTo(fromUuid);

		assertThatThrownBy(() -> new SessionId(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("UserSession should create active session and handle expiration, touch, and revocation")
	void testUserSession() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		String tokenId = UUID.randomUUID().toString();

		UserSession session = UserSession.create(userId, tenantId, tokenId, 3600, "127.0.0.1", "Mozilla/5.0");

		assertThat(session.id()).isNotNull();
		assertThat(session.userId()).isEqualTo(userId);
		assertThat(session.tenantId()).isEqualTo(tenantId);
		assertThat(session.tokenIdentifier()).isEqualTo(tokenId);
		assertThat(session.ipAddress()).isEqualTo("127.0.0.1");
		assertThat(session.userAgent()).isEqualTo("Mozilla/5.0");
		assertThat(session.revoked()).isFalse();

		Instant now = Instant.now();
		assertThat(session.isActive(now)).isTrue();
		assertThat(session.isExpired(now)).isFalse();

		UserSession revoked = session.withRevoked();
		assertThat(revoked.revoked()).isTrue();
		assertThat(revoked.isActive(now)).isFalse();

		Instant futureTouch = now.plusSeconds(100);
		UserSession touched = session.touch(futureTouch);
		assertThat(touched.lastAccessAt()).isEqualTo(futureTouch);

		Instant farFuture = now.plusSeconds(7200);
		assertThat(session.isExpired(farFuture)).isTrue();
		assertThat(session.isActive(farFuture)).isFalse();
	}

	@Test
	@DisplayName("LockoutStatus and AccountLockedException should handle unlocked and locked states")
	void testLockoutStatusAndException() {
		LockoutStatus unlocked = LockoutStatus.unlocked(2);
		assertThat(unlocked.locked()).isFalse();
		assertThat(unlocked.failedAttempts()).isEqualTo(2);
		assertThat(unlocked.lockedUntil()).isNull();

		Instant lockedUntil = Instant.now().plusSeconds(900);
		LockoutStatus locked = LockoutStatus.locked(5, lockedUntil);
		assertThat(locked.locked()).isTrue();
		assertThat(locked.failedAttempts()).isEqualTo(5);
		assertThat(locked.lockedUntil()).isEqualTo(lockedUntil);

		AccountLockedException ex = new AccountLockedException("Locked", lockedUntil);
		assertThat(ex.getMessage()).isEqualTo("Locked");
		assertThat(ex.getLockedUntil()).isEqualTo(lockedUntil);
	}
}
