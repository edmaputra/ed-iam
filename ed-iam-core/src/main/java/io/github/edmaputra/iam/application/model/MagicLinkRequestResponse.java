package io.github.edmaputra.iam.application.model;

import java.util.Objects;

/**
 * Safe, enumeration-resistant response model returning the dispatch status of a magic link request.
 * Secret tokens are never included in HTTP response payloads and are dispatched exclusively
 * out-of-band via {@link io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort}.
 *
 * @param message human-readable status message
 * @author edmaputra
 * @since 0.5.0
 */
public record MagicLinkRequestResponse(String message) {

	public MagicLinkRequestResponse {
		Objects.requireNonNull(message, "Message must not be null.");
	}

	/**
	 * Creates a new {@link MagicLinkRequestResponse}.
	 *
	 * @param message status message
	 * @return new {@link MagicLinkRequestResponse}
	 */
	public static MagicLinkRequestResponse of(String message) {
		return new MagicLinkRequestResponse(message);
	}
}
