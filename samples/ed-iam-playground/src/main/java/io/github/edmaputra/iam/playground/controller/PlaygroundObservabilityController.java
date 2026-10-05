package io.github.edmaputra.iam.playground.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.playground.service.PlaygroundObservabilityService;
import io.github.edmaputra.iam.playground.service.PlaygroundObservabilityService.PlaygroundMetricsSnapshot;

/**
 * REST controller exposing IAM metrics and SIEM security audit events for the interactive playground.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@RestController
@RequestMapping("/api/v1/playground/observability")
public class PlaygroundObservabilityController {

	private final PlaygroundObservabilityService observabilityService;

	public PlaygroundObservabilityController(PlaygroundObservabilityService observabilityService) {
		this.observabilityService = observabilityService;
	}

	/**
	 * Retrieves recent IAM security audit events.
	 *
	 * @param eventType optional event type filter
	 * @param limit     maximum number of events (default 50)
	 * @return audit event list response
	 */
	@GetMapping("/events")
	public ResponseEntity<PlaygroundEventsResponse> getEvents(
			@RequestParam(required = false) String eventType,
			@RequestParam(defaultValue = "50") int limit) {
		List<IamEvent> events = observabilityService.getRecentEvents(eventType, limit);
		int total = observabilityService.getEventCount();
		return ResponseEntity.ok(new PlaygroundEventsResponse(total, events));
	}

	/**
	 * Clears all captured audit events in the playground memory buffer.
	 *
	 * @return confirmation response
	 */
	@DeleteMapping("/events")
	public ResponseEntity<Map<String, String>> clearEvents() {
		observabilityService.clearEvents();
		return ResponseEntity.ok(Map.of("message", "Audit log cleared successfully"));
	}

	/**
	 * Retrieves the current aggregated IAM metrics snapshot.
	 *
	 * @return metrics snapshot
	 */
	@GetMapping("/metrics")
	public ResponseEntity<PlaygroundMetricsSnapshot> getMetrics() {
		return ResponseEntity.ok(observabilityService.getMetricsSnapshot());
	}

	/**
	 * Response envelope for playground audit events.
	 */
	public record PlaygroundEventsResponse(int totalCaptured, List<IamEvent> events) {}
}
