package io.github.edmaputra.iam.domain.auth.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure Java implementation of RFC 6238 Time-Based One-Time Password (TOTP) algorithm,
 * RFC 4648 Base32 encoding/decoding, and backup recovery code utilities.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public final class TotpGenerator {

	private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	private static final int[] BASE32_LOOKUP = new int[128];
	private static final String HMAC_ALGORITHM = "HmacSHA1";
	private static final int DEFAULT_TIME_STEP_SECONDS = 30;
	private static final int DEFAULT_DIGITS = 6;
	private static final int DEFAULT_SECRET_BYTES = 20; // 160 bits recommended by RFC 4226/6238

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	static {
		Arrays.fill(BASE32_LOOKUP, -1);
		for (int i = 0; i < BASE32_ALPHABET.length(); i++) {
			BASE32_LOOKUP[BASE32_ALPHABET.charAt(i)] = i;
		}
	}

	private TotpGenerator() {
	}

	/**
	 * Generates a cryptographically secure Base32-encoded TOTP secret key (160 bits / 20 bytes).
	 *
	 * @return Base32-encoded secret string
	 */
	public static String generateSecret() {
		byte[] buffer = new byte[DEFAULT_SECRET_BYTES];
		SECURE_RANDOM.nextBytes(buffer);
		return encodeBase32(buffer);
	}

	/**
	 * Generates the 6-digit TOTP code for the specified Base32 secret at the given instant.
	 *
	 * @param base32Secret the Base32-encoded secret key
	 * @param instant      the time instant
	 * @return zero-padded 6-digit TOTP string
	 */
	public static String generateTotp(String base32Secret, Instant instant) {
		Objects.requireNonNull(base32Secret, "Secret must not be null.");
		Objects.requireNonNull(instant, "Instant must not be null.");

		long timeStep = instant.getEpochSecond() / DEFAULT_TIME_STEP_SECONDS;
		return generateTotpForTimeStep(decodeBase32(base32Secret), timeStep);
	}

	/**
	 * Generates the current 6-digit TOTP code for the specified Base32 secret at the current instant.
	 *
	 * @param base32Secret the Base32-encoded secret key
	 * @return zero-padded 6-digit TOTP string
	 */
	public static String generateCurrentTotp(String base32Secret) {
		return generateTotp(base32Secret, Instant.now());
	}

	/**
	 * Verifies whether the provided TOTP code is valid for the given Base32 secret at the specified instant,
	 * allowing for clock drift within {@code windowSteps} (e.g. 1 step = +/- 30 seconds).
	 *
	 * @param base32Secret the Base32-encoded secret key
	 * @param code         the 6-digit candidate code
	 * @param instant      the current time instant
	 * @param windowSteps  the number of past and future time steps to check for clock drift (e.g. 1)
	 * @return {@code true} if valid, {@code false} otherwise
	 */
	public static boolean verifyTotp(String base32Secret, String code, Instant instant, int windowSteps) {
		if (base32Secret == null || code == null || instant == null) {
			return false;
		}
		String cleanCode = code.trim();
		if (cleanCode.length() != DEFAULT_DIGITS || !cleanCode.chars().allMatch(Character::isDigit)) {
			return false;
		}

		byte[] keyBytes;
		try {
			keyBytes = decodeBase32(base32Secret);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}

		long currentStep = instant.getEpochSecond() / DEFAULT_TIME_STEP_SECONDS;
		for (long step = currentStep - windowSteps; step <= currentStep + windowSteps; step++) {
			String candidate = generateTotpForTimeStep(keyBytes, step);
			if (candidate.equals(cleanCode)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Generates a standard Key URI compatible with Google Authenticator, Microsoft Authenticator, and 1Password.
	 * Format: {@code otpauth://totp/{issuer}:{accountName}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30}
	 *
	 * @param issuer       the application or entity name (e.g. "ed-iam")
	 * @param accountName  the user identifier or email
	 * @param base32Secret the Base32-encoded secret
	 * @return formatted OTP Auth URI string
	 */
	public static String generateOtpAuthUri(String issuer, String accountName, String base32Secret) {
		Objects.requireNonNull(issuer, "Issuer must not be null.");
		Objects.requireNonNull(accountName, "Account name must not be null.");
		Objects.requireNonNull(base32Secret, "Secret must not be null.");

		String encodedIssuer = URLEncoder.encode(issuer.trim(), StandardCharsets.UTF_8).replace("+", "%20");
		String encodedAccount = URLEncoder.encode(accountName.trim(), StandardCharsets.UTF_8).replace("+", "%20");

		return String.format(
				"otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
				encodedIssuer,
				encodedAccount,
				base32Secret.trim(),
				encodedIssuer,
				DEFAULT_DIGITS,
				DEFAULT_TIME_STEP_SECONDS);
	}

	/**
	 * Generates a list of single-use backup recovery codes formatted as {@code XXXX-XXXX}.
	 *
	 * @param count the number of backup codes to generate
	 * @return list of plaintext backup recovery codes
	 */
	public static List<String> generateBackupCodes(int count) {
		if (count <= 0) {
			throw new IllegalArgumentException("Count must be greater than zero.");
		}
		List<String> codes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			byte[] bytes = new byte[4];
			SECURE_RANDOM.nextBytes(bytes);
			String hex = String.format("%02X%02X-%02X%02X", bytes[0], bytes[1], bytes[2], bytes[3]);
			codes.add(hex);
		}
		return List.copyOf(codes);
	}

	/**
	 * Computes the SHA-256 hash of a backup recovery code for secure persistent storage.
	 *
	 * @param plainCode the raw backup recovery code
	 * @return 64-character lowercase hexadecimal SHA-256 hash
	 */
	public static String hashBackupCode(String plainCode) {
		Objects.requireNonNull(plainCode, "Backup code must not be null.");
		String normalized = plainCode.trim().toUpperCase().replace("-", "");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				hexString.append(String.format("%02x", b));
			}
			return hexString.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 message digest algorithm is not available.", ex);
		}
	}

	/**
	 * Encodes arbitrary bytes into RFC 4648 Base32 string (without '=' padding).
	 *
	 * @param data input byte array
	 * @return Base32 encoded string
	 */
	public static String encodeBase32(byte[] data) {
		Objects.requireNonNull(data, "Data must not be null.");
		StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bitsLeft = 0;

		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				bitsLeft -= 5;
				int index = (buffer >> bitsLeft) & 0x1F;
				out.append(BASE32_ALPHABET.charAt(index));
			}
		}

		if (bitsLeft > 0) {
			int index = (buffer << (5 - bitsLeft)) & 0x1F;
			out.append(BASE32_ALPHABET.charAt(index));
		}

		return out.toString();
	}

	/**
	 * Decodes an RFC 4648 Base32 string into a byte array.
	 *
	 * @param base32 input Base32 string
	 * @return decoded byte array
	 */
	public static byte[] decodeBase32(String base32) {
		Objects.requireNonNull(base32, "Base32 string must not be null.");
		String clean = base32.trim().toUpperCase().replace("=", "").replace(" ", "").replace("-", "");
		if (clean.isEmpty()) {
			return new byte[0];
		}

		ByteBuffer out = ByteBuffer.allocate((clean.length() * 5) / 8 + 1);
		int buffer = 0;
		int bitsLeft = 0;

		for (int i = 0; i < clean.length(); i++) {
			char c = clean.charAt(i);
			if (c >= BASE32_LOOKUP.length || BASE32_LOOKUP[c] == -1) {
				throw new IllegalArgumentException("Invalid Base32 character: " + c);
			}
			buffer = (buffer << 5) | BASE32_LOOKUP[c];
			bitsLeft += 5;

			if (bitsLeft >= 8) {
				bitsLeft -= 8;
				out.put((byte) ((buffer >> bitsLeft) & 0xFF));
			}
		}

		byte[] result = new byte[out.position()];
		out.flip();
		out.get(result);
		return result;
	}

	private static String generateTotpForTimeStep(byte[] keyBytes, long timeStep) {
		try {
			byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
			byte[] hash = mac.doFinal(timeBytes);

			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
					| ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8)
					| (hash[offset + 3] & 0xFF);

			int otp = binary % 1_000_000;
			return String.format("%06d", otp);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to calculate HMAC-SHA1 for TOTP.", ex);
		}
	}
}
