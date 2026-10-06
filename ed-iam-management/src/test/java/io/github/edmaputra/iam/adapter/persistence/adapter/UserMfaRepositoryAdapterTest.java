package io.github.edmaputra.iam.adapter.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.edmaputra.iam.adapter.persistence.entity.UserMfaJpaEntity;
import io.github.edmaputra.iam.adapter.persistence.repository.UserMfaJpaRepository;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserMfaRepositoryAdapter}.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@ExtendWith(MockitoExtension.class)
class UserMfaRepositoryAdapterTest {

	@Mock
	private UserMfaJpaRepository jpaRepository;

	private UserMfaRepositoryAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new UserMfaRepositoryAdapter(jpaRepository);
	}

	@Test
	@DisplayName("Should find UserMfa by userId and map correctly including backup codes")
	void shouldFindByUserId() {
		UserId userId = UserId.generate();
		Instant now = Instant.now();
		UserMfaJpaEntity entity = new UserMfaJpaEntity(
				userId.value(), "SECRET123", true, "code1,code2,code3", now, now);

		when(jpaRepository.findById(userId.value())).thenReturn(Optional.of(entity));

		Optional<UserMfa> result = adapter.findByUserId(userId);
		assertThat(result).isPresent();
		UserMfa mfa = result.get();
		assertThat(mfa.getUserId()).isEqualTo(userId);
		assertThat(mfa.getSecret()).isEqualTo("SECRET123");
		assertThat(mfa.isEnabled()).isTrue();
		assertThat(mfa.backupCodes()).containsExactly("code1", "code2", "code3");
	}

	@Test
	@DisplayName("Should save UserMfa domain model to entity")
	void shouldSaveUserMfa() {
		UserId userId = UserId.generate();
		UserMfa mfa = UserMfa.create(userId, "SECRET123", List.of("hash1", "hash2"));
		mfa.activate();

		when(jpaRepository.save(any(UserMfaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		UserMfa saved = adapter.save(mfa);
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.isEnabled()).isTrue();

		ArgumentCaptor<UserMfaJpaEntity> captor = ArgumentCaptor.forClass(UserMfaJpaEntity.class);
		verify(jpaRepository).save(captor.capture());
		UserMfaJpaEntity entity = captor.getValue();
		assertThat(entity.getUserId()).isEqualTo(userId.value());
		assertThat(entity.getSecret()).isEqualTo("SECRET123");
		assertThat(entity.isEnabled()).isTrue();
		assertThat(entity.getBackupCodes()).isEqualTo("hash1,hash2");
	}

	@Test
	@DisplayName("Should delete UserMfa by userId")
	void shouldDeleteByUserId() {
		UserId userId = UserId.generate();
		adapter.deleteByUserId(userId);
		verify(jpaRepository).deleteById(userId.value());
	}

	@Test
	@DisplayName("Should enforce null checks")
	void shouldEnforceNullChecks() {
		assertThatThrownBy(() -> adapter.findByUserId(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.save(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> adapter.deleteByUserId(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Should encrypt TOTP secret on save and decrypt on find with AesGcmEncryptor (OWASP A02)")
	void shouldEncryptSecretWithAesGcmEncryptor() {
		io.github.edmaputra.iam.adapter.persistence.crypto.AesGcmEncryptor encryptor =
				new io.github.edmaputra.iam.adapter.persistence.crypto.AesGcmEncryptor("test-key-for-mfa-repository-adapter-test-256!");
		UserMfaRepositoryAdapter cryptoAdapter = new UserMfaRepositoryAdapter(jpaRepository, encryptor);

		UserId userId = UserId.generate();
		UserMfa mfa = UserMfa.create(userId, "PLAIN_SECRET_BASE32", List.of("hash1"));

		when(jpaRepository.save(any(UserMfaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		UserMfa saved = cryptoAdapter.save(mfa);
		assertThat(saved.getSecret()).isEqualTo("PLAIN_SECRET_BASE32");

		ArgumentCaptor<UserMfaJpaEntity> captor = ArgumentCaptor.forClass(UserMfaJpaEntity.class);
		verify(jpaRepository).save(captor.capture());
		UserMfaJpaEntity savedEntity = captor.getValue();

		// Entity secret stored in DB must be encrypted
		assertThat(savedEntity.getSecret()).startsWith("enc:v1:");
		assertThat(savedEntity.getSecret()).isNotEqualTo("PLAIN_SECRET_BASE32");

		// Decrypt when retrieving from entity
		when(jpaRepository.findById(userId.value())).thenReturn(Optional.of(savedEntity));
		Optional<UserMfa> foundOpt = cryptoAdapter.findByUserId(userId);
		assertThat(foundOpt).isPresent();
		assertThat(foundOpt.get().getSecret()).isEqualTo("PLAIN_SECRET_BASE32");
	}
}
