package io.github.edmaputra.iam.domain.exception;

/**
 * Exception thrown when a magic link token has exceeded its validity window.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public class MagicLinkExpiredException extends InvalidMagicLinkException {

	public MagicLinkExpiredException(String message) {
		super(message);
	}
}
