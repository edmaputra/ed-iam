package io.github.edmaputra.iam.domain.exception;

/**
 * Exception thrown when a magic link token is invalid, unrecognized, or malformed.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public class InvalidMagicLinkException extends AuthenticationException {

	public InvalidMagicLinkException(String message) {
		super(message);
	}

	public InvalidMagicLinkException(String message, Throwable cause) {
		super(message, cause);
	}
}
