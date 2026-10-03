package io.github.edmaputra.iam.playground.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.edmaputra.iam.application.port.out.MagicLinkNotifierPort;
import io.github.edmaputra.iam.domain.model.MagicLinkToken;

/**
 * Simulated in-memory email inbox service implementing {@link MagicLinkNotifierPort}
 * for the interactive playground and automated integration tests.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@Service
public class PlaygroundSimulatedMailService implements MagicLinkNotifierPort {

	private static final Logger log = LoggerFactory.getLogger(PlaygroundSimulatedMailService.class);

	/**
	 * Model representing an email received in the simulated inbox.
	 */
	public record SimulatedEmail(
			String toEmail,
			String token,
			String verificationUrl,
			Instant receivedAt) {
	}

	private final Deque<SimulatedEmail> inbox = new ArrayDeque<>();

	@Override
	public void sendMagicLink(MagicLinkToken magicLinkToken, String verificationUrl) {
		SimulatedEmail email = new SimulatedEmail(
				magicLinkToken.email(),
				magicLinkToken.token(),
				verificationUrl,
				Instant.now());

		synchronized (inbox) {
			inbox.addFirst(email);
			while (inbox.size() > 50) {
				inbox.removeLast();
			}
		}

		log.info("Simulated Mail Inbox received magic link for email='{}' | URL: {}", magicLinkToken.email(), verificationUrl);
	}

	/**
	 * Retrieves the most recent email delivered to the simulated inbox.
	 *
	 * @return optional latest email
	 */
	public Optional<SimulatedEmail> getLatestEmail() {
		synchronized (inbox) {
			return Optional.ofNullable(inbox.peekFirst());
		}
	}

	/**
	 * Retrieves the most recent email delivered to a specific email address.
	 *
	 * @param email target recipient email
	 * @return optional email if found
	 */
	public Optional<SimulatedEmail> getLatestEmailFor(String email) {
		if (email == null) {
			return Optional.empty();
		}
		synchronized (inbox) {
			return inbox.stream()
					.filter(e -> e.toEmail().equalsIgnoreCase(email.trim()))
					.findFirst();
		}
	}

	/**
	 * Clears all messages from the simulated inbox.
	 */
	public void clear() {
		synchronized (inbox) {
			inbox.clear();
		}
	}
}
