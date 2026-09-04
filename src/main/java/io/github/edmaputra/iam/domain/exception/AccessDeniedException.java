package io.github.edmaputra.iam.domain.exception;

/**
 * Thrown when an authenticated actor lacks required permissions or scope access to execute an operation.
 *
 * @author edmaputra
 */
public class AccessDeniedException extends RuntimeException {

	/**
	 * Constructs the exception with a detail message.
	 *
	 * @param message the detail message
	 */
	public AccessDeniedException(String message) {
		super(message);
	}
}
