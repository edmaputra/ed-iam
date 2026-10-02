package io.github.edmaputra.iam.application.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Response payload describing the current Multi-Factor Authentication (MFA) status of a user account.
 *
 * @param enabled    whether MFA is currently active and enforced for authentication
 * @param enrolledAt timestamp when MFA was activated, or null if not enabled
 * @author edmaputra
 * @since 0.5.0
 */
public record MfaStatusResponse(
		boolean enabled,
		Instant enrolledAt) {

	/**
	 * Returns the optional enrolled timestamp.
	 *
	 * @return optional enrollment instant
	 */
	public Optional<Instant> optionalEnrolledAt() {
		return Optional.ofNullable(enrolledAt);
	}
}
