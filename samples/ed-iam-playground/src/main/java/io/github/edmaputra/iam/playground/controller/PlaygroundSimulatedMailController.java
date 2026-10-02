package io.github.edmaputra.iam.playground.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.playground.service.PlaygroundSimulatedMailService;
import io.github.edmaputra.iam.playground.service.PlaygroundSimulatedMailService.SimulatedEmail;

/**
 * Controller exposing endpoints to inspect the simulated mail inbox.
 *
 * @author edmaputra
 * @since 0.7.0
 */
@RestController
@RequestMapping("/api/playground/inbox")
public class PlaygroundSimulatedMailController {

	private final PlaygroundSimulatedMailService mailService;

	public PlaygroundSimulatedMailController(PlaygroundSimulatedMailService mailService) {
		this.mailService = mailService;
	}

	@GetMapping("/latest")
	public ResponseEntity<SimulatedEmail> getLatestEmail(
			@RequestParam(value = "email", required = false) String email) {
		Optional<SimulatedEmail> mailOpt = (email != null && !email.isBlank())
				? mailService.getLatestEmailFor(email)
				: mailService.getLatestEmail();

		return mailOpt.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
