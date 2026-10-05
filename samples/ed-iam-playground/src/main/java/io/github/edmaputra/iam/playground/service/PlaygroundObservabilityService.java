package io.github.edmaputra.iam.playground.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.github.edmaputra.iam.domain.event.IamEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * In-memory service capturing published {@link IamEvent} security audit events
 * and aggregating Micrometer IAM metrics for the interactive playground dashboard.
 *
 * @author edmaputra
 * @since 0.9.0
 */
@Service
public class PlaygroundObservabilityService {

	private static final int MAX_BUFFERED_EVENTS = 200;

	private final MeterRegistry meterRegistry;
	private final Deque<IamEvent> auditLog = new ArrayDeque<>();

	public PlaygroundObservabilityService(Optional<MeterRegistry> meterRegistry) {
		this.meterRegistry = meterRegistry.orElse(null);
	}

	/**
	 * Listens to domain events published across the application.
	 *
	 * @param event the published IAM domain event
	 */
	@EventListener
	public void onIamEvent(IamEvent event) {
		if (event == null) {
			return;
		}
		synchronized (auditLog) {
			auditLog.addFirst(event);
			while (auditLog.size() > MAX_BUFFERED_EVENTS) {
				auditLog.removeLast();
			}
		}
	}

	/**
	 * Retrieves recent audit events, optionally filtered by event type.
	 *
	 * @param eventTypeFilter optional event type filter (e.g., "LOGIN_SUCCESS", "ACCESS_DENIED")
	 * @param limit           maximum number of events to return (default 50)
	 * @return list of matching audit events
	 */
	public List<IamEvent> getRecentEvents(String eventTypeFilter, int limit) {
		int max = limit > 0 ? Math.min(limit, MAX_BUFFERED_EVENTS) : 50;
		synchronized (auditLog) {
			return auditLog.stream()
					.filter(e -> eventTypeFilter == null || eventTypeFilter.isBlank() || "ALL".equalsIgnoreCase(eventTypeFilter)
							|| e.eventType().equalsIgnoreCase(eventTypeFilter))
					.limit(max)
					.toList();
		}
	}

	/**
	 * Clears the buffered audit log.
	 */
	public void clearEvents() {
		synchronized (auditLog) {
			auditLog.clear();
		}
	}

	/**
	 * Returns the total count of buffered audit events.
	 *
	 * @return total captured events
	 */
	public int getEventCount() {
		synchronized (auditLog) {
			return auditLog.size();
		}
	}

	/**
	 * Aggregates a structured metrics snapshot from the Micrometer registry.
	 *
	 * @return metrics snapshot
	 */
	public PlaygroundMetricsSnapshot getMetricsSnapshot() {
		if (meterRegistry == null) {
			return PlaygroundMetricsSnapshot.empty();
		}

		// 1. Auth Attempts
		Collection<Counter> authAttemptCounters = meterRegistry.find("iam.auth.attempts").counters();
		long totalAttempts = 0;
		long successfulAttempts = 0;
		long failedAttempts = 0;
		Map<String, Long> attemptsByMethod = new HashMap<>();

		for (Counter c : authAttemptCounters) {
			long count = (long) c.count();
			totalAttempts += count;
			String status = c.getId().getTag("status");
			if ("success".equalsIgnoreCase(status)) {
				successfulAttempts += count;
			} else {
				failedAttempts += count;
			}
			String method = c.getId().getTag("auth_type");
			if (method != null) {
				attemptsByMethod.merge(method, count, Long::sum);
			}
		}

		// 2. Auth Latency
		Collection<Timer> authLatencyTimers = meterRegistry.find("iam.auth.latency").timers();
		long latencyCount = 0;
		double latencyTotalMs = 0;
		double latencyMaxMs = 0;

		for (Timer t : authLatencyTimers) {
			latencyCount += t.count();
			latencyTotalMs += t.totalTime(TimeUnit.MILLISECONDS);
			latencyMaxMs = Math.max(latencyMaxMs, t.max(TimeUnit.MILLISECONDS));
		}
		double latencyMeanMs = latencyCount > 0 ? (latencyTotalMs / latencyCount) : 0;

		// 3. Token Validation Time
		Collection<Timer> tokenTimers = meterRegistry.find("iam.token.validation.time").timers();
		long tokenValidationCount = 0;
		double tokenTotalMs = 0;
		double tokenMaxMs = 0;
		Map<String, Long> tokenCountByStatus = new HashMap<>();

		for (Timer t : tokenTimers) {
			long count = t.count();
			tokenValidationCount += count;
			tokenTotalMs += t.totalTime(TimeUnit.MILLISECONDS);
			tokenMaxMs = Math.max(tokenMaxMs, t.max(TimeUnit.MILLISECONDS));
			String status = t.getId().getTag("status");
			if (status != null) {
				tokenCountByStatus.merge(status, count, Long::sum);
			}
		}
		double tokenMeanMs = tokenValidationCount > 0 ? (tokenTotalMs / tokenValidationCount) : 0;

		// 4. Access Denied
		Collection<Counter> accessDeniedCounters = meterRegistry.find("iam.access.denied").counters();
		long totalAccessDenied = 0;
		Map<String, Long> deniedByReason = new HashMap<>();
		Map<String, Long> deniedByPermission = new HashMap<>();

		for (Counter c : accessDeniedCounters) {
			long count = (long) c.count();
			totalAccessDenied += count;
			String reason = c.getId().getTag("reason");
			if (reason != null) {
				deniedByReason.merge(reason, count, Long::sum);
			}
			String perm = c.getId().getTag("permission");
			if (perm != null && !"none".equalsIgnoreCase(perm)) {
				deniedByPermission.merge(perm, count, Long::sum);
			}
		}

		return new PlaygroundMetricsSnapshot(
				new AuthMetrics(totalAttempts, successfulAttempts, failedAttempts, attemptsByMethod,
						latencyCount, round(latencyMeanMs), round(latencyMaxMs)),
				new TokenMetrics(tokenValidationCount, tokenCountByStatus, round(tokenMeanMs), round(tokenMaxMs)),
				new AccessDeniedMetrics(totalAccessDenied, deniedByReason, deniedByPermission)
		);
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	public record PlaygroundMetricsSnapshot(
			AuthMetrics auth,
			TokenMetrics token,
			AccessDeniedMetrics accessDenied
	) {
		public static PlaygroundMetricsSnapshot empty() {
			return new PlaygroundMetricsSnapshot(
					new AuthMetrics(0, 0, 0, Map.of(), 0, 0, 0),
					new TokenMetrics(0, Map.of(), 0, 0),
					new AccessDeniedMetrics(0, Map.of(), Map.of())
			);
		}
	}

	public record AuthMetrics(
			long totalAttempts,
			long successfulAttempts,
			long failedAttempts,
			Map<String, Long> attemptsByMethod,
			long latencySampleCount,
			double meanLatencyMs,
			double maxLatencyMs
	) {}

	public record TokenMetrics(
			long totalValidations,
			Map<String, Long> validationsByStatus,
			double meanValidationMs,
			double maxValidationMs
	) {}

	public record AccessDeniedMetrics(
			long totalDenied,
			Map<String, Long> deniedByReason,
			Map<String, Long> deniedByPermission
	) {}
}
