package io.github.edmaputra.iam.domain.exception;

/**
 * Exception thrown when an invalid TOTP code or backup code is supplied during MFA verification.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public class InvalidTotpException extends AuthenticationException {

	public InvalidTotpException(String message) {
		super(message);
	}
}
