package io.github.edmaputra.iam.application.port.out;

import io.github.edmaputra.iam.domain.model.MagicLinkToken;

/**
 * Outbound SPI port responsible for dispatching magic link login URLs to users.
 *
 * @author edmaputra
 * @since 0.6.0
 */
public interface MagicLinkNotifierPort {

	/**
	 * Sends the magic link authentication URL to the destination specified in the token.
	 *
	 * @param magicLinkToken  the issued magic link token
	 * @param verificationUrl the full absolute verification URL for the user to click
	 */
	void sendMagicLink(MagicLinkToken magicLinkToken, String verificationUrl);
}
