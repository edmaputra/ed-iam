package io.github.edmaputra.iam.domain.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying lifecycle, invariants, and backup code consumption for {@link UserMfa}.
 *
 * @author edmaputra
 * @since 0.6.0
 */
class UserMfaTest {

	@Test
	@DisplayName("Should create unactivated UserMfa and activate/disable properly")
	void shouldManageMfaLifecycle() {
		UserId userId = UserId.generate();
		String secret = TotpGenerator.generateSecret();
		List<String> plainCodes = TotpGenerator.generateBackupCodes(8);
		List<String> hashedCodes = plainCodes.stream().map(TotpGenerator::hashBackupCode).toList();

		UserMfa mfa = UserMfa.create(userId, secret, hashedCodes);

		assertThat(mfa.getUserId()).isEqualTo(userId);
		assertThat(mfa.getSecret()).isEqualTo(secret);
		assertThat(mfa.isEnabled()).isFalse();
		assertThat(mfa.backupCodes()).hasSize(8);
		assertThat(mfa.getCreatedAt()).isNotNull();
		assertThat(mfa.getUpdatedAt()).isNotNull();

		// Activate
		mfa.activate();
		assertThat(mfa.isEnabled()).isTrue();

		// Consume backup code
		String firstPlainCode = plainCodes.get(0);
		String firstHash = TotpGenerator.hashBackupCode(firstPlainCode);
		boolean consumed = mfa.consumeBackupCode(firstHash);
		assertThat(consumed).isTrue();
		assertThat(mfa.backupCodes()).hasSize(7);
		assertThat(mfa.backupCodes()).doesNotContain(firstHash);

		// Trying to consume the same code again fails
		assertThat(mfa.consumeBackupCode(firstHash)).isFalse();

		// Disable
		mfa.disable();
		assertThat(mfa.isEnabled()).isFalse();

		// Reset secret
		String newSecret = TotpGenerator.generateSecret();
		mfa.resetSecret(newSecret, List.of("hash1", "hash2"));
		assertThat(mfa.getSecret()).isEqualTo(newSecret);
		assertThat(mfa.isEnabled()).isFalse();
		assertThat(mfa.backupCodes()).containsExactly("hash1", "hash2");
	}

	@Test
	@DisplayName("Should enforce non-null invariants")
	void shouldEnforceInvariants() {
		UserId userId = UserId.generate();

		assertThatThrownBy(() -> UserMfa.create(null, "SECRET", List.of()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> UserMfa.create(userId, null, List.of()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> UserMfa.create(userId, "   ", List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
