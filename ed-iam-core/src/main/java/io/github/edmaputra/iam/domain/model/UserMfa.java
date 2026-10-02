package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

/**
 * Pure domain model representing Multi-Factor Authentication (MFA / TOTP) enrollment,
 * secret key, active state, and hashed backup recovery codes for a user account.
 *
 * @author edmaputra
 * @since 0.5.0
 */
@Getter
public class UserMfa {

	private final UserId userId;
	private String secret;
	private boolean enabled;
	private List<String> backupCodes;
	private final Instant createdAt;
	private Instant updatedAt;

	/**
	 * Canonical constructor for reconstructing MFA domain models from persistence.
	 *
	 * @param userId      the user ID
	 * @param secret      the Base32-encoded TOTP secret key
	 * @param enabled     whether MFA is actively enforced for authentication
	 * @param backupCodes list of hashed single-use backup recovery codes
	 * @param createdAt   creation timestamp
	 * @param updatedAt   last modified timestamp
	 */
	public UserMfa(
			UserId userId,
			String secret,
			boolean enabled,
			List<String> backupCodes,
			Instant createdAt,
			Instant updatedAt) {
		this.userId = Objects.requireNonNull(userId, "UserId must not be null.");
		this.secret = validateSecret(secret);
		this.enabled = enabled;
		this.backupCodes = backupCodes == null ? new ArrayList<>() : new ArrayList<>(backupCodes);
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	/**
	 * Factory method initializing a new pending MFA configuration (not yet active until verified).
	 *
	 * @param userId            the user ID
	 * @param secret            the generated Base32 secret key
	 * @param hashedBackupCodes list of hashed backup codes
	 * @return new {@link UserMfa} instance in unactivated state
	 */
	public static UserMfa create(UserId userId, String secret, List<String> hashedBackupCodes) {
		Instant now = Instant.now();
		return new UserMfa(userId, secret, false, hashedBackupCodes, now, now);
	}

	/**
	 * Activates MFA after the user successfully confirms their setup with a valid TOTP code.
	 */
	public void activate() {
		this.enabled = true;
		this.updatedAt = Instant.now();
	}

	/**
	 * Disables MFA enforcement for the user account.
	 */
	public void disable() {
		this.enabled = false;
		this.updatedAt = Instant.now();
	}

	/**
	 * Reconfigures the MFA secret and backup codes, resetting active status to pending confirmation.
	 *
	 * @param newSecret         the new Base32 secret
	 * @param hashedBackupCodes the new hashed backup codes
	 */
	public void resetSecret(String newSecret, List<String> hashedBackupCodes) {
		this.secret = validateSecret(newSecret);
		this.enabled = false;
		this.backupCodes = hashedBackupCodes == null ? new ArrayList<>() : new ArrayList<>(hashedBackupCodes);
		this.updatedAt = Instant.now();
	}

	/**
	 * Returns an unmodifiable view of the hashed single-use backup codes.
	 *
	 * @return list of hashed backup codes
	 */
	public List<String> backupCodes() {
		return Collections.unmodifiableList(backupCodes);
	}

	/**
	 * Consumes a single-use backup recovery code if present in the stored hashed list.
	 *
	 * @param hashedBackupCode the SHA-256 hash of the entered backup code
	 * @return {@code true} if code was present and consumed, {@code false} otherwise
	 */
	public boolean consumeBackupCode(String hashedBackupCode) {
		if (hashedBackupCode == null || hashedBackupCode.isBlank()) {
			return false;
		}
		boolean removed = this.backupCodes.remove(hashedBackupCode.trim().toLowerCase());
		if (removed) {
			this.updatedAt = Instant.now();
		}
		return removed;
	}

	private static String validateSecret(String secret) {
		Objects.requireNonNull(secret, "Secret must not be null.");
		String trimmed = secret.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Secret must not be blank.");
		}
		return trimmed;
	}
}
