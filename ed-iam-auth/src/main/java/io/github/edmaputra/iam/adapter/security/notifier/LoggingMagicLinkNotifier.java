package io.github.edmaputra.iam.adapter.security.notifier;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;

/**
 * Default fallback implementation of {@link MagicLinkNotifierPort} that logs magic link URLs.
 * Suitable for local development, integration tests, and sandbox environments.
 *
 * @author edmaputra
 * @since 0.5.0
 */
public class LoggingMagicLinkNotifier implements MagicLinkNotifierPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingMagicLinkNotifier.class);

	@Override
	public void sendMagicLink(MagicLinkToken magicLinkToken, String verificationUrl) {
		Objects.requireNonNull(magicLinkToken, "MagicLinkToken must not be null.");
		Objects.requireNonNull(verificationUrl, "Verification URL must not be null.");

		log.info("Dispatched magic link authentication to email='{}' with token='{}' | URL: {}",
				magicLinkToken.email(), magicLinkToken.token(), verificationUrl);
	}
}
