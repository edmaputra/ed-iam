package io.github.edmaputra.iam.adapter.persistence.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * High-performance AES-256-GCM symmetric encryptor for data-at-rest encryption of sensitive credentials,
 * such as TOTP shared secrets (OWASP A02: Cryptographic Failures).
 * <p>
 * Employs authenticated encryption with associated data (AEAD) using 128-bit authentication tags and
 * 12-byte cryptographically secure random initialization vectors (IV).
 * Encrypted strings are prefixed with {@value #ENCRYPTED_PREFIX} for versioning and transparent
 * backward-compatible decryption of unencrypted legacy values.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class AesGcmEncryptor {

	public static final String ENCRYPTED_PREFIX = "enc:v1:";
	private static final String ALGORITHM = "AES";
	private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH_BYTES = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;

	private final SecretKey secretKey;
	private final SecureRandom secureRandom;

	/**
	 * Constructs an encryptor with a raw {@link SecretKey}.
	 *
	 * @param secretKey 256-bit AES secret key
	 */
	public AesGcmEncryptor(SecretKey secretKey) {
		this.secretKey = Objects.requireNonNull(secretKey, "SecretKey must not be null.");
		this.secureRandom = new SecureRandom();
	}

	/**
	 * Constructs an encryptor deriving a 256-bit AES key from the supplied secret passphrase or key string.
	 *
	 * @param secretKeyString secret passphrase or key string
	 */
	public AesGcmEncryptor(String secretKeyString) {
		Objects.requireNonNull(secretKeyString, "Secret key string must not be null.");
		if (secretKeyString.isBlank()) {
			throw new IllegalArgumentException("Secret key string must not be blank.");
		}
		this.secretKey = deriveKey(secretKeyString);
		this.secureRandom = new SecureRandom();
	}

	/**
	 * Encrypts a plaintext string using AES-256-GCM.
	 *
	 * @param plaintext input plaintext string
	 * @return version-prefixed Base64 encoded ciphertext containing IV and auth tag
	 */
	public String encrypt(String plaintext) {
		if (plaintext == null) {
			return null;
		}
		if (plaintext.startsWith(ENCRYPTED_PREFIX)) {
			// Already encrypted
			return plaintext;
		}

		try {
			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

			byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
			byte[] ciphertext = cipher.doFinal(plaintextBytes);

			ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			buffer.put(iv);
			buffer.put(ciphertext);

			return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to encrypt sensitive data with AES-256-GCM.", ex);
		}
	}

	/**
	 * Decrypts a version-prefixed AES-256-GCM ciphertext back to plaintext.
	 * Returns the input unchanged if it was not encrypted (providing transparent backward compatibility).
	 *
	 * @param ciphertext version-prefixed Base64 encoded ciphertext
	 * @return decrypted UTF-8 plaintext string
	 */
	public String decrypt(String ciphertext) {
		if (ciphertext == null || !ciphertext.startsWith(ENCRYPTED_PREFIX)) {
			// Unencrypted legacy value or null: return as-is
			return ciphertext;
		}

		try {
			String encodedPayload = ciphertext.substring(ENCRYPTED_PREFIX.length());
			byte[] combined = Base64.getDecoder().decode(encodedPayload);

			if (combined.length <= GCM_IV_LENGTH_BYTES) {
				throw new IllegalArgumentException("Invalid AES-GCM ciphertext payload length.");
			}

			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

			int ciphertextLength = combined.length - GCM_IV_LENGTH_BYTES;
			byte[] ciphertextBytes = new byte[ciphertextLength];
			System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertextBytes, 0, ciphertextLength);

			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

			byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
			return new String(decryptedBytes, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to decrypt sensitive data with AES-256-GCM. Authentication failed.", ex);
		}
	}

	private static SecretKey deriveKey(String secret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, ALGORITHM);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 message digest algorithm not available.", ex);
		}
	}
}
