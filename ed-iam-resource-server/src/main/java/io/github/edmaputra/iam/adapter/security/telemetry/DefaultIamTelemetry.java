package io.github.edmaputra.iam.adapter.security.telemetry;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Default implementation of {@link IamTelemetry} combining native Micrometer metrics
 * and OpenTelemetry distributed tracing spans.
 *
 * @author edmaputra
 * @since 0.9.0
 */
public class DefaultIamTelemetry implements IamTelemetry {

	private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.edmaputra.iam";

	private final MeterRegistry meterRegistry;
	private final Tracer tracer;
	private final IamTelemetryProperties properties;

	public DefaultIamTelemetry(
			MeterRegistry meterRegistry,
			Tracer tracer,
			IamTelemetryProperties properties) {
		this.meterRegistry = meterRegistry;
		this.tracer = tracer != null ? tracer : GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
		this.properties = properties != null ? properties : new IamTelemetryProperties();
	}

	public DefaultIamTelemetry(MeterRegistry meterRegistry, Tracer tracer) {
		this(meterRegistry, tracer, new IamTelemetryProperties());
	}

	public DefaultIamTelemetry(MeterRegistry meterRegistry) {
		this(meterRegistry, null, new IamTelemetryProperties());
	}

	public DefaultIamTelemetry() {
		this(null, null, new IamTelemetryProperties());
	}

	@Override
	public void recordAuthenticationLatency(String authType, String status, String tenantId, Duration duration) {
		if (!isMetricsEnabled() || meterRegistry == null || duration == null) {
			return;
		}

		Timer.builder("iam.auth.latency")
				.description("Latency of IAM authentication requests")
				.tag("auth_type", sanitize(authType, "unknown"))
				.tag("status", sanitize(status, "unknown"))
				.tag("tenant_id", sanitize(tenantId, "anonymous"))
				.register(meterRegistry)
				.record(duration);
	}

	@Override
	public void recordTokenValidationTime(String status, Duration duration) {
		if (!isMetricsEnabled() || meterRegistry == null || duration == null) {
			return;
		}

		Timer.builder("iam.token.validation.time")
				.description("Time taken to validate JWT tokens")
				.tag("status", sanitize(status, "unknown"))
				.register(meterRegistry)
				.record(duration);
	}

	@Override
	public void recordAccessDenied(String reason, String permission, String tenantId, String actor) {
		if (!isMetricsEnabled() || meterRegistry == null) {
			return;
		}

		Counter.builder("iam.access.denied")
				.description("Total number of access denial events")
				.tag("reason", sanitize(reason, "unknown"))
				.tag("permission", sanitize(permission, "none"))
				.tag("tenant_id", sanitize(tenantId, "anonymous"))
				.tag("actor", sanitize(actor, "anonymous"))
				.register(meterRegistry)
				.increment();
	}

	@Override
	public void recordAuthenticationAttempt(String authType, String status, String tenantId) {
		if (!isMetricsEnabled() || meterRegistry == null) {
			return;
		}

		Counter.builder("iam.auth.attempts")
				.description("Total number of authentication attempts")
				.tag("auth_type", sanitize(authType, "unknown"))
				.tag("status", sanitize(status, "unknown"))
				.tag("tenant_id", sanitize(tenantId, "anonymous"))
				.register(meterRegistry)
				.increment();
	}

	@Override
	public Span startSpan(String spanName) {
		if (!isTracingEnabled() || tracer == null) {
			return Span.getInvalid();
		}
		return tracer.spanBuilder(spanName).startSpan();
	}

	@Override
	public <T> T trace(String spanName, Map<String, String> initialAttributes, Supplier<T> action) {
		Objects.requireNonNull(action, "Action must not be null.");
		if (!isTracingEnabled() || tracer == null) {
			return action.get();
		}

		Span span = startSpan(spanName);
		if (initialAttributes != null) {
			initialAttributes.forEach((k, v) -> {
				if (k != null && v != null) {
					span.setAttribute(k, v);
				}
			});
		}

		try (Scope scope = span.makeCurrent()) {
			T result = action.get();
			span.setStatus(StatusCode.OK);
			return result;
		}
		catch (Throwable t) {
			span.recordException(t);
			span.setStatus(StatusCode.ERROR, t.getMessage());
			throw t;
		}
		finally {
			span.end();
		}
	}

	private boolean isMetricsEnabled() {
		return properties.isEnabled() && properties.isMetricsEnabled();
	}

	private boolean isTracingEnabled() {
		return properties.isEnabled() && properties.isTracingEnabled();
	}

	private String sanitize(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value.trim();
	}
}
