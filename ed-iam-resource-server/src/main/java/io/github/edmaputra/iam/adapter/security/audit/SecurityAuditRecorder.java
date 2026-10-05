package io.github.edmaputra.iam.adapter.security.audit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.ValidatingEventPublisher;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import io.github.edmaputra.iam.domain.event.IamEventValidator;

/**
 * Unified facade coordinating security audit event validation, SIEM event publication,
 * and Micrometer metrics instrumentation.
 * <p>
 * Eliminates boilerplate null-checks, repetitive duration calculations, and raw Map construction
 * across authentication, authorization, and resource server components.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class SecurityAuditRecorder {

	private static final UUID SYSTEM_ENTITY_ID = new UUID(0L, 0L);

	private final IamTelemetry telemetry;
	private final EventPublisherPort eventPublisher;
	private final IamEventValidator validator;

	public SecurityAuditRecorder(
			IamTelemetry telemetry,
			EventPublisherPort eventPublisher,
			IamEventValidator validator) {
		this.telemetry = telemetry;
		this.eventPublisher = eventPublisher != null ? ValidatingEventPublisher.of(eventPublisher) : null;
		this.validator = validator != null ? validator : new IamEventValidator();
	}

	public SecurityAuditRecorder(IamTelemetry telemetry, EventPublisherPort eventPublisher) {
		this(telemetry, eventPublisher, new IamEventValidator());
	}

	public SecurityAuditRecorder() {
		this(null, null, new IamEventValidator());
	}

	/**
	 * Creates a safe no-op security audit recorder.
	 *
	 * @return no-op recorder instance
	 */
	public static SecurityAuditRecorder noop() {
		return new SecurityAuditRecorder(null, null);
	}

	/**
	 * Records metrics and publishes an audit event for a successful authentication attempt.
	 *
	 * @param authMethod authentication provider type (e.g., "password", "magic_link")
	 * @param actor      authenticated actor principal (e.g., email or username)
	 * @param userId     authenticated user ID
	 * @param tenantId   resolved tenant ID
	 * @param ipAddress  client IP address
	 * @param duration   elapsed duration of the authentication operation
	 */
	public void recordLoginSuccess(
			String authMethod,
			String actor,
			UUID userId,
			UUID tenantId,
			String ipAddress,
			Duration duration) {
		String tenantStr = tenantId != null ? tenantId.toString() : null;

		if (telemetry != null && duration != null) {
			telemetry.recordAuthenticationLatency(authMethod, "success", tenantStr, duration);
			telemetry.recordAuthenticationAttempt(authMethod, "success", tenantStr);
		}

		if (eventPublisher != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("email", actor);
			payload.put("ip", ipAddress != null ? ipAddress : "unknown");

			eventPublisher.publish(IamEvent.of(
					IamEventTypes.LOGIN_SUCCESS,
					tenantId,
					userId != null ? userId : SYSTEM_ENTITY_ID,
					"AUTH",
					payload,
					actor != null ? actor : "anonymous"
			));
		}
	}

	/**
	 * Convenience overload recording successful login with start nano time.
	 */
	public void recordLoginSuccess(
			String authMethod,
			String actor,
			UUID userId,
			UUID tenantId,
			String ipAddress,
			long startNanoTime) {
		recordLoginSuccess(authMethod, actor, userId, tenantId, ipAddress, elapsed(startNanoTime));
	}

	/**
	 * Records metrics and publishes an audit event for a failed authentication attempt.
	 *
	 * @param authMethod   authentication provider type (e.g., "password")
	 * @param actor        attempted principal
	 * @param tenantId     target tenant ID
	 * @param denialReason high-level denial reason (e.g., "bad_credentials")
	 * @param errorMessage exception message
	 * @param ipAddress    client IP address
	 * @param duration     elapsed duration
	 */
	public void recordLoginFailure(
			String authMethod,
			String actor,
			UUID tenantId,
			String denialReason,
			String errorMessage,
			String ipAddress,
			Duration duration) {
		String tenantStr = tenantId != null ? tenantId.toString() : null;

		if (telemetry != null) {
			if (duration != null) {
				telemetry.recordAuthenticationLatency(authMethod, "failure", tenantStr, duration);
			}
			telemetry.recordAuthenticationAttempt(authMethod, "failure", tenantStr);
			telemetry.recordAccessDenied(denialReason, null, tenantStr, actor);
		}

		if (eventPublisher != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("email", actor);
			payload.put("reason", errorMessage != null ? errorMessage : denialReason);
			payload.put("ip", ipAddress != null ? ipAddress : "unknown");

			eventPublisher.publish(IamEvent.of(
					IamEventTypes.LOGIN_FAILED,
					tenantId,
					SYSTEM_ENTITY_ID,
					"AUTH",
					payload,
					actor != null ? actor : "anonymous"
			));
		}
	}

	/**
	 * Convenience overload recording failed login with start nano time.
	 */
	public void recordLoginFailure(
			String authMethod,
			String actor,
			UUID tenantId,
			String denialReason,
			String errorMessage,
			String ipAddress,
			long startNanoTime) {
		recordLoginFailure(authMethod, actor, tenantId, denialReason, errorMessage, ipAddress, elapsed(startNanoTime));
	}

	/**
	 * Records metrics and publishes an audit event when authentication is blocked due to account lockout.
	 *
	 * @param authMethod  authentication provider type
	 * @param actor       attempted principal
	 * @param tenantId    target tenant ID
	 * @param lockedUntil lockout expiration timestamp description
	 * @param duration    elapsed duration
	 */
	public void recordAccountLocked(
			String authMethod,
			String actor,
			UUID tenantId,
			String lockedUntil,
			Duration duration) {
		String tenantStr = tenantId != null ? tenantId.toString() : null;

		if (telemetry != null) {
			if (duration != null) {
				telemetry.recordAuthenticationLatency(authMethod, "locked", tenantStr, duration);
			}
			telemetry.recordAuthenticationAttempt(authMethod, "locked", tenantStr);
			telemetry.recordAccessDenied("account_locked", null, tenantStr, actor);
		}

		if (eventPublisher != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("email", actor);
			if (lockedUntil != null) {
				payload.put("lockedUntil", lockedUntil);
			}

			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ACCOUNT_LOCKED,
					tenantId,
					SYSTEM_ENTITY_ID,
					"USER",
					payload,
					actor != null ? actor : "anonymous"
			));
		}
	}

	/**
	 * Convenience overload recording account lockout with start nano time.
	 */
	public void recordAccountLocked(
			String authMethod,
			String actor,
			UUID tenantId,
			String lockedUntil,
			long startNanoTime) {
		recordAccountLocked(authMethod, actor, tenantId, lockedUntil, elapsed(startNanoTime));
	}

	/**
	 * Records metrics when multi-factor authentication is required to complete login.
	 *
	 * @param authMethod authentication provider type
	 * @param tenantId   target tenant ID
	 * @param duration   elapsed duration
	 */
	public void recordMfaRequired(String authMethod, UUID tenantId, Duration duration) {
		String tenantStr = tenantId != null ? tenantId.toString() : null;
		if (telemetry != null) {
			if (duration != null) {
				telemetry.recordAuthenticationLatency(authMethod, "mfa_required", tenantStr, duration);
			}
			telemetry.recordAuthenticationAttempt(authMethod, "mfa_required", tenantStr);
		}
	}

	/**
	 * Convenience overload recording MFA required with start nano time.
	 */
	public void recordMfaRequired(String authMethod, UUID tenantId, long startNanoTime) {
		recordMfaRequired(authMethod, tenantId, elapsed(startNanoTime));
	}

	/**
	 * Records metrics and publishes an audit event for access denial (authorization failure).
	 *
	 * @param reason             denial reason (e.g., "missing_permission", "missing_auth_header", "token_revoked")
	 * @param requiredPermission required permission code if applicable
	 * @param tenantId           target tenant ID
	 * @param actor              actor principal or user ID
	 * @param path               requested resource path
	 * @param details            additional contextual payload map
	 */
	public void recordAccessDenied(
			String reason,
			String requiredPermission,
			UUID tenantId,
			String actor,
			String path,
			Map<String, Object> details) {
		String tenantStr = tenantId != null ? tenantId.toString() : null;
		String actorStr = actor != null ? actor : "anonymous";

		if (telemetry != null) {
			telemetry.recordAccessDenied(reason, requiredPermission, tenantStr, actorStr);
		}

		if (eventPublisher != null) {
			Map<String, Object> payload = new HashMap<>();
			payload.put("reason", reason);
			if (path != null) {
				payload.put("path", path);
			}
			if (requiredPermission != null) {
				payload.put("requiredPermission", requiredPermission);
			}
			if (details != null) {
				payload.putAll(details);
			}

			eventPublisher.publish(IamEvent.of(
					IamEventTypes.ACCESS_DENIED,
					tenantId,
					SYSTEM_ENTITY_ID,
					"SECURITY",
					payload,
					actorStr
			));
		}
	}

	/**
	 * Records JWT token validation time metric.
	 *
	 * @param status   validation status ("valid", "invalid", "revoked", "expired")
	 * @param duration validation duration
	 */
	public void recordTokenValidation(String status, Duration duration) {
		if (telemetry != null && duration != null) {
			telemetry.recordTokenValidationTime(status, duration);
		}
	}

	/**
	 * Convenience overload recording JWT token validation time with start nano time.
	 */
	public void recordTokenValidation(String status, long startNanoTime) {
		recordTokenValidation(status, elapsed(startNanoTime));
	}

	/**
	 * Validates, sanitizes, and publishes a generic IAM domain event.
	 *
	 * @param event the domain event to publish
	 */
	public void publish(IamEvent event) {
		if (eventPublisher != null && event != null) {
			IamEvent sanitized = validator.validateAndSanitize(event);
			eventPublisher.publish(sanitized);
		}
	}

	/**
	 * Returns the underlying event publisher port if present.
	 *
	 * @return event publisher port or null
	 */
	public EventPublisherPort getEventPublisher() {
		return eventPublisher;
	}

	/**
	 * Returns the underlying telemetry recorder if present.
	 *
	 * @return telemetry recorder or null
	 */
	public IamTelemetry getTelemetry() {
		return telemetry;
	}

	private Duration elapsed(long startNanoTime) {
		return Duration.ofNanos(System.nanoTime() - startNanoTime);
	}
}
