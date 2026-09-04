package io.github.edmaputra.iam.domain.model;

/**
 * Enumeration of account lifecycle statuses in the IAM subsystem.
 *
 * @author edmaputra
 */
public enum UserStatus {
	/** Active account permitted to authenticate and access authorized resources. */
	ACTIVE,
	/** Temporarily suspended account prevented from logging in. */
	SUSPENDED,
	/** Deactivated/terminated account blocked from system access. */
	DEACTIVATED
}
