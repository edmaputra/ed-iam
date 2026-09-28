package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.model.LockoutStatus;
import io.github.edmaputra.iam.domain.model.UserId;

/**
 * Inbound port for inspecting and administrative unlocking of accounts locked due to failed authentication attempts.
 *
 * @author edmaputra
 * @since 0.3.0
 */
public interface ManageLockoutUseCase {

	/**
	 * Inspects the current lockout status for the given user.
	 *
	 * @param userId the user ID
	 * @return the user's lockout status
	 */
	LockoutStatus getLockoutStatus(UserId userId);

	/**
	 * Resets failed attempt counters and removes the lockout state for the given user.
	 *
	 * @param userId the user ID
	 */
	void unlockUser(UserId userId);
}
