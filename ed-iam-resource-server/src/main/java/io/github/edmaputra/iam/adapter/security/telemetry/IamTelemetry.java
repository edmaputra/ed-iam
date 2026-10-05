package io.github.edmaputra.iam.adapter.security.telemetry;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import io.opentelemetry.api.trace.Span;

/**
 * Telemetry interface for OpenTelemetry distributed tracing and native Micrometer metrics.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public interface IamTelemetry {

	/**
	 * Records latency of an authentication attempt.
	 *
	 * @param authType the authentication mechanism (e.g. "password", "api_key", "oidc", "magic_link", "refresh_token")
	 * @param status the outcome status (e.g. "success", "failure", "locked", "mfa_required")
	 * @param tenantId the tenant ID string if known, or null
	 * @param duration elapsed duration
	 */
	void recordAuthenticationLatency(String authType, String status, String tenantId, Duration duration);

	/**
	 * Records time taken to validate a JWT token.
	 *
	 * @param status validation outcome ("valid", "expired", "revoked", "invalid")
	 * @param duration elapsed validation duration
	 */
	void recordTokenValidationTime(String status, Duration duration);

	/**
	 * Records an access denial event.
	 *
	 * @param reason the reason for denial (e.g. "missing_permission", "scope_denied", "token_revoked", "token_expired")
	 * @param permission the requested permission if applicable, or null
	 * @param tenantId target tenant ID string if known, or null
	 * @param actor caller actor identifier if known, or null
	 */
	void recordAccessDenied(String reason, String permission, String tenantId, String actor);

	/**
	 * Records an authentication attempt counter.
	 *
	 * @param authType the authentication mechanism
	 * @param status outcome status
	 * @param tenantId tenant ID string if known
	 */
	void recordAuthenticationAttempt(String authType, String status, String tenantId);

	/**
	 * Starts a new distributed tracing span with the specified name.
	 *
	 * @param spanName name of the span
	 * @return new OpenTelemetry Span
	 */
	Span startSpan(String spanName);

	/**
	 * Executes the given supplier within a traced OpenTelemetry span.
	 *
	 * @param spanName span name
	 * @param initialAttributes initial attributes to populate on the span
	 * @param action action to execute
	 * @param <T> result type
	 * @return result of the action
	 */
	<T> T trace(String spanName, Map<String, String> initialAttributes, Supplier<T> action);

	/**
	 * Executes the given runnable within a traced OpenTelemetry span.
	 *
	 * @param spanName span name
	 * @param initialAttributes initial attributes to populate on the span
	 * @param action action to execute
	 */
	default void traceRunnable(String spanName, Map<String, String> initialAttributes, Runnable action) {
		trace(spanName, initialAttributes, () -> {
			action.run();
			return null;
		});
	}
}
