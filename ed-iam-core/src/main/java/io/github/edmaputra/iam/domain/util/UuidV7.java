package io.github.edmaputra.iam.domain.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Utility for generating time-ordered UUIDv7 instances compliant with RFC 9562.
 *
 * <p>UUIDv7 encodes a 48-bit millisecond timestamp in the most significant bits,
 * providing natural time-sorting and index locality in B-Tree databases.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {}

	/**
	 * Generates a new time-ordered UUIDv7 using current system time.
	 *
	 * @return a new UUIDv7 instance
	 */
	public static UUID generate() {
		return generate(System.currentTimeMillis());
	}

	/**
	 * Generates a new UUIDv7 for the specified timestamp.
	 *
	 * @param timestamp the instant to encode
	 * @return a new UUIDv7 instance
	 */
	public static UUID generate(Instant timestamp) {
		Objects.requireNonNull(timestamp, "Timestamp must not be null.");
		return generate(timestamp.toEpochMilli());
	}

	/**
	 * Generates a new UUIDv7 for the specified millisecond epoch.
	 *
	 * @param epochMillis milliseconds since Unix epoch
	 * @return a new UUIDv7 instance
	 */
	public static UUID generate(long epochMillis) {
		long randomA = RANDOM.nextLong();
		long randomB = RANDOM.nextLong();

		// 48-bit timestamp + 4-bit version (0x7) + 12-bit random
		long mostSigBits = (epochMillis << 16) | (0x7000L) | (randomA & 0x0FFFL);

		// 2-bit variant (0b10 / 0x80) + 62-bit random
		long leastSigBits = (0x8000000000000000L) | (randomB & 0x3FFFFFFFFFFFFFFFL);

		return new UUID(mostSigBits, leastSigBits);
	}

	/**
	 * Extracts the embedded creation timestamp from a UUIDv7 instance.
	 *
	 * @param uuid the UUIDv7 to extract from
	 * @return the encoded Instant
	 */
	public static Instant extractTimestamp(UUID uuid) {
		Objects.requireNonNull(uuid, "UUID must not be null.");
		if (uuid.version() != 7) {
			throw new IllegalArgumentException("Expected UUID version 7 but found version " + uuid.version());
		}
		long epochMillis = uuid.getMostSignificantBits() >>> 16;
		return Instant.ofEpochMilli(epochMillis);
	}
}
