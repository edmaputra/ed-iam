package io.github.edmaputra.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;

/**
 * Pure domain aggregate representing a user account in the Uwati IAM subsystem.
 * Encapsulates lifecycle states, password hashes, and profile metadata.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Getter
public class User {

	private final UserId id;
	private final String email;
	private String passwordHash;
	private String fullName;
	private UserStatus status;
	private final boolean platformSuperAdmin;
	private final Instant createdAt;
	private Instant updatedAt;

	/**
	 * Canonical constructor for reconstructing existing user domain models.
	 *
	 * @param id                 the unique user ID
	 * @param email              the user email address
	 * @param passwordHash       optional password hash
	 * @param fullName           the user's full name
	 * @param status             the user lifecycle status
	 * @param platformSuperAdmin flag indicating platform superadmin status
	 * @param createdAt          creation timestamp
	 * @param updatedAt          last updated timestamp
	 */
	public User(
			UserId id,
			String email,
			String passwordHash,
			String fullName,
			UserStatus status,
			boolean platformSuperAdmin,
			Instant createdAt,
			Instant updatedAt) {
		this.id = Objects.requireNonNull(id, "User ID must not be null.");
		this.email = validateEmail(email);
		this.passwordHash = passwordHash;
		this.fullName = validateFullName(fullName);
		this.status = Objects.requireNonNull(status, "Status must not be null.");
		this.platformSuperAdmin = platformSuperAdmin;
		this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt must not be null.");
		this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt must not be null.");
	}

	/**
	 * Factory method to create a new active local user account.
	 *
	 * @param email              the user email
	 * @param passwordHash       the BCrypt password hash
	 * @param fullName           the user full name
	 * @param platformSuperAdmin whether this user has global superadmin privileges
	 * @return new {@link User}
	 */
	public static User create(
			String email,
			String passwordHash,
			String fullName,
			boolean platformSuperAdmin) {
		Instant now = Instant.now();
		return new User(
				UserId.generate(),
				email,
				passwordHash,
				fullName,
				UserStatus.ACTIVE,
				platformSuperAdmin,
				now,
				now);
	}

	/**
	 * Factory method to create a new external federated user account without local password.
	 *
	 * @param email              the user email
	 * @param fullName           the user full name
	 * @param platformSuperAdmin whether this user has global superadmin privileges
	 * @return new {@link User}
	 */
	public static User createExternal(
			String email,
			String fullName,
			boolean platformSuperAdmin) {
		return create(email, null, fullName, platformSuperAdmin);
	}

	/**
	 * Updates the user's full name profile.
	 *
	 * @param fullName the updated full name
	 */
	public void updateProfile(String fullName) {
		this.fullName = validateFullName(fullName);
		this.updatedAt = Instant.now();
	}

	/**
	 * Updates the user's password hash.
	 *
	 * @param passwordHash the updated BCrypt password hash
	 */
	public void updatePassword(String passwordHash) {
		this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash must not be null.");
		this.updatedAt = Instant.now();
	}

	/**
	 * Transitions the user status to ACTIVE.
	 */
	public void activate() {
		if (this.status == UserStatus.DEACTIVATED) {
			throw new IllegalStateException("Deactivated user cannot be directly activated.");
		}
		this.status = UserStatus.ACTIVE;
		this.updatedAt = Instant.now();
	}

	/**
	 * Transitions the user status to SUSPENDED.
	 */
	public void suspend() {
		if (this.status == UserStatus.DEACTIVATED) {
			throw new IllegalStateException("Deactivated user cannot be suspended.");
		}
		this.status = UserStatus.SUSPENDED;
		this.updatedAt = Instant.now();
	}

	/**
	 * Transitions the user status to DEACTIVATED.
	 */
	public void deactivate() {
		this.status = UserStatus.DEACTIVATED;
		this.updatedAt = Instant.now();
	}

	/**
	 * Returns true if the user account is active.
	 *
	 * @return true if ACTIVE
	 */
	public boolean isActive() {
		return this.status == UserStatus.ACTIVE;
	}

	/**
	 * Returns true if the user account is suspended.
	 *
	 * @return true if SUSPENDED
	 */
	public boolean isSuspended() {
		return this.status == UserStatus.SUSPENDED;
	}

	/**
	 * Returns true if the user account is deactivated.
	 *
	 * @return true if DEACTIVATED
	 */
	public boolean isDeactivated() {
		return this.status == UserStatus.DEACTIVATED;
	}

	/**
	 * Returns the optional password hash.
	 *
	 * @return optional password hash
	 */
	public Optional<String> optionalPasswordHash() {
		return Optional.ofNullable(passwordHash);
	}

	private static String validateEmail(String email) {
		Objects.requireNonNull(email, "Email must not be null.");
		String trimmed = email.trim().toLowerCase();
		if (trimmed.isBlank() || !trimmed.contains("@")) {
			throw new IllegalArgumentException("Invalid email format: " + email);
		}
		return trimmed;
	}

	private static String validateFullName(String fullName) {
		Objects.requireNonNull(fullName, "Full name must not be null.");
		String trimmed = fullName.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException("Full name must not be blank.");
		}
		return trimmed;
	}
}
