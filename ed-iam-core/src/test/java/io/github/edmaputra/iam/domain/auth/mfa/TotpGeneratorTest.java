package io.github.edmaputra.iam.domain.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests verifying RFC 6238 TOTP computation, RFC 4648 Base32 encoding, and backup recovery code utilities.
 *
 * @author edmaputra
 * @since 0.6.0
 */
class TotpGeneratorTest {

	@Test
	@DisplayName("Should encode and decode Base32 according to RFC 4648 test vectors")
	void shouldEncodeAndDecodeBase32() {
		// RFC 4648 test vectors (without padding)
		assertThat(TotpGenerator.encodeBase32("".getBytes(StandardCharsets.UTF_8))).isEmpty();
		assertThat(TotpGenerator.encodeBase32("f".getBytes(StandardCharsets.UTF_8))).isEqualTo("MY");
		assertThat(TotpGenerator.encodeBase32("fo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXQ");
		assertThat(TotpGenerator.encodeBase32("foo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6");
		assertThat(TotpGenerator.encodeBase32("foob".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YQ");
		assertThat(TotpGenerator.encodeBase32("foobar".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YTBOI");

		// Round trip test
		String text = "Hello, Clinical IAM Multi-Tenant World!";
		byte[] raw = text.getBytes(StandardCharsets.UTF_8);
		String encoded = TotpGenerator.encodeBase32(raw);
		byte[] decoded = TotpGenerator.decodeBase32(encoded);
		assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(text);

		// Case insensitive and padding stripping
		byte[] decodedWithPadding = TotpGenerator.decodeBase32("mzxw6ytboi======");
		assertThat(new String(decodedWithPadding, StandardCharsets.UTF_8)).isEqualTo("foobar");
	}

	@Test
	@DisplayName("Should match RFC 6238 test vectors for HMAC-SHA1 TOTP")
	void shouldMatchRfc6238TestVectors() {
		// RFC 6238 Appendix B test vector key: "12345678901234567890" (ASCII)
		byte[] keyBytes = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
		String base32Key = TotpGenerator.encodeBase32(keyBytes);

		// Time = 59L -> "287082"
		assertThat(TotpGenerator.generateTotp(base32Key, Instant.ofEpochSecond(59L))).isEqualTo("287082");

		// Time = 1111111109L -> "081804"
		assertThat(TotpGenerator.generateTotp(base32Key, Instant.ofEpochSecond(1111111109L))).isEqualTo("081804");

		// Time = 1111111111L -> "050471"
		assertThat(TotpGenerator.generateTotp(base32Key, Instant.ofEpochSecond(1111111111L))).isEqualTo("050471");

		// Time = 1234567890L -> "005924"
		assertThat(TotpGenerator.generateTotp(base32Key, Instant.ofEpochSecond(1234567890L))).isEqualTo("005924");

		// Time = 2000000000L -> "279037"
		assertThat(TotpGenerator.generateTotp(base32Key, Instant.ofEpochSecond(2000000000L))).isEqualTo("279037");
	}

	@Test
	@DisplayName("Should verify TOTP codes with clock drift window")
	void shouldVerifyTotpWithWindowDrift() {
		String secret = TotpGenerator.generateSecret();
		Instant now = Instant.now();

		String currentCode = TotpGenerator.generateTotp(secret, now);
		assertThat(TotpGenerator.verifyTotp(secret, currentCode, now, 1)).isTrue();

		// Verification within 1 step (30s ago)
		Instant pastInstant = now.minusSeconds(25);
		String pastCode = TotpGenerator.generateTotp(secret, pastInstant);
		assertThat(TotpGenerator.verifyTotp(secret, pastCode, now, 1)).isTrue();

		// Verification within 1 step (25s in future)
		Instant futureInstant = now.plusSeconds(25);
		String futureCode = TotpGenerator.generateTotp(secret, futureInstant);
		assertThat(TotpGenerator.verifyTotp(secret, futureCode, now, 1)).isTrue();

		// Verification outside window (> 60s)
		Instant farPast = now.minusSeconds(90);
		String farPastCode = TotpGenerator.generateTotp(secret, farPast);
		assertThat(TotpGenerator.verifyTotp(secret, farPastCode, now, 1)).isFalse();

		// Invalid codes
		assertThat(TotpGenerator.verifyTotp(secret, "12345", now, 1)).isFalse();
		assertThat(TotpGenerator.verifyTotp(secret, "ABCDEF", now, 1)).isFalse();
		assertThat(TotpGenerator.verifyTotp(null, currentCode, now, 1)).isFalse();
	}

	@Test
	@DisplayName("Should generate standard OTP Auth URI")
	void shouldGenerateOtpAuthUri() {
		String uri = TotpGenerator.generateOtpAuthUri("ed-iam", "doctor@metro.org", "JBSWY3DPEHPK3PXP");
		assertThat(uri).isEqualTo("otpauth://totp/ed-iam:doctor%40metro.org?secret=JBSWY3DPEHPK3PXP&issuer=ed-iam&algorithm=SHA1&digits=6&period=30");
	}

	@Test
	@DisplayName("Should generate, format, and hash backup recovery codes")
	void shouldGenerateAndHashBackupCodes() {
		List<String> codes = TotpGenerator.generateBackupCodes(8);
		assertThat(codes).hasSize(8);
		for (String code : codes) {
			assertThat(code).matches("^[0-9A-F]{4}-[0-9A-F]{4}$");
			String hash = TotpGenerator.hashBackupCode(code);
			assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
			// Hashing is case and hyphen-insensitive
			assertThat(TotpGenerator.hashBackupCode(code.toLowerCase().replace("-", ""))).isEqualTo(hash);
		}

		assertThatThrownBy(() -> TotpGenerator.generateBackupCodes(0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
