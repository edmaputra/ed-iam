package io.github.edmaputra.iam.domain.exception;

/**
 * Exception thrown when a magic link token has already been consumed and cannot be reused.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public class MagicLinkConsumedException extends InvalidMagicLinkException {

	public MagicLinkConsumedException(String message) {
		super(message);
	}
}
