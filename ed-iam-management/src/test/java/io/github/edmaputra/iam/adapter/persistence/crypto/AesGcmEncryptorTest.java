package io.github.edmaputra.iam.adapter.persistence.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AesGcmEncryptor}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class AesGcmEncryptorTest {

	private AesGcmEncryptor encryptor;

	@BeforeEach
	void setUp() {
		encryptor = new AesGcmEncryptor("test-master-encryption-secret-minimum-256-bits-length!");
	}

	@Test
	@DisplayName("Should successfully encrypt and decrypt plaintext TOTP secret")
	void shouldEncryptAndDecryptSuccessfully() {
		String secret = "JBSWY3DPEHPK3PXP";
		String encrypted = encryptor.encrypt(secret);

		assertThat(encrypted).startsWith(AesGcmEncryptor.ENCRYPTED_PREFIX);
		assertThat(encrypted).isNotEqualTo(secret);

		String decrypted = encryptor.decrypt(encrypted);
		assertThat(decrypted).isEqualTo(secret);
	}

	@Test
	@DisplayName("Should transparently return unencrypted legacy values during decryption")
	void shouldReturnLegacyUnencryptedValues() {
		String legacyPlainSecret = "LEGACYPLAINSECRET";
		String result = encryptor.decrypt(legacyPlainSecret);
		assertThat(result).isEqualTo(legacyPlainSecret);
	}

	@Test
	@DisplayName("Should handle null inputs gracefully")
	void shouldHandleNull() {
		assertThat(encryptor.encrypt(null)).isNull();
		assertThat(encryptor.decrypt(null)).isNull();
	}

	@Test
	@DisplayName("Should not double-encrypt already encrypted ciphertext")
	void shouldNotDoubleEncrypt() {
		String secret = "JBSWY3DPEHPK3PXP";
		String encrypted = encryptor.encrypt(secret);
		String doubleEncrypted = encryptor.encrypt(encrypted);

		assertThat(doubleEncrypted).isEqualTo(encrypted);
	}

	@Test
	@DisplayName("Should fail authentication when tampered ciphertext is decrypted")
	void shouldFailAuthenticationOnTamperedCiphertext() {
		String secret = "JBSWY3DPEHPK3PXP";
		String encrypted = encryptor.encrypt(secret);

		// Tamper with payload characters
		char lastChar = encrypted.charAt(encrypted.length() - 1);
		char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
		String tampered = encrypted.substring(0, encrypted.length() - 1) + tamperedChar;

		assertThatThrownBy(() -> encryptor.decrypt(tampered))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Authentication failed");
	}
}
