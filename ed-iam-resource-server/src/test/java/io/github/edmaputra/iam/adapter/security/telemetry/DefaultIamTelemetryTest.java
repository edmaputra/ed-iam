package io.github.edmaputra.iam.adapter.security.telemetry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultIamTelemetry}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class DefaultIamTelemetryTest {

	private MeterRegistry meterRegistry;
	private IamTelemetryProperties properties;
	private DefaultIamTelemetry telemetry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		properties = new IamTelemetryProperties();
		telemetry = new DefaultIamTelemetry(meterRegistry, null, properties);
	}

	@Test
	@DisplayName("Should record authentication latency timer with tags")
	void shouldRecordAuthenticationLatency() {
		telemetry.recordAuthenticationLatency("password", "success", "tenant-1", Duration.ofMillis(120));

		assertThat(meterRegistry.find("iam.auth.latency")
				.tag("auth_type", "password")
				.tag("status", "success")
				.tag("tenant_id", "tenant-1")
				.timer())
				.isNotNull()
				.satisfies(timer -> {
					assertThat(timer.count()).isEqualTo(1);
					assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(100);
				});
	}

	@Test
	@DisplayName("Should record token validation time timer")
	void shouldRecordTokenValidationTime() {
		telemetry.recordTokenValidationTime("valid", Duration.ofMillis(15));
		telemetry.recordTokenValidationTime("revoked", Duration.ofMillis(25));

		assertThat(meterRegistry.find("iam.token.validation.time")
				.tag("status", "valid")
				.timer())
				.isNotNull()
				.satisfies(timer -> assertThat(timer.count()).isEqualTo(1));

		assertThat(meterRegistry.find("iam.token.validation.time")
				.tag("status", "revoked")
				.timer())
				.isNotNull()
				.satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
	}

	@Test
	@DisplayName("Should record access denied counter with tags")
	void shouldRecordAccessDenied() {
		telemetry.recordAccessDenied("missing_permission", "USER_READ", "tenant-1", "user-123");

		assertThat(meterRegistry.find("iam.access.denied")
				.tag("reason", "missing_permission")
				.tag("permission", "USER_READ")
				.tag("tenant_id", "tenant-1")
				.tag("actor", "user-123")
				.counter())
				.isNotNull()
				.satisfies(counter -> assertThat(counter.count()).isEqualTo(1.0));
	}

	@Test
	@DisplayName("Should record authentication attempt counter")
	void shouldRecordAuthenticationAttempt() {
		telemetry.recordAuthenticationAttempt("password", "failure", "tenant-1");

		assertThat(meterRegistry.find("iam.auth.attempts")
				.tag("auth_type", "password")
				.tag("status", "failure")
				.tag("tenant_id", "tenant-1")
				.counter())
				.isNotNull()
				.satisfies(counter -> assertThat(counter.count()).isEqualTo(1.0));
	}

	@Test
	@DisplayName("Should trace execution and record exception on failure")
	void shouldTraceExecution() {
		String result = telemetry.trace("iam.test.span", Map.of("tag1", "val1"), () -> "success-value");
		assertThat(result).isEqualTo("success-value");

		assertThatThrownBy(() -> telemetry.trace("iam.error.span", Map.of(), () -> {
			throw new IllegalStateException("span error");
		})).isInstanceOf(IllegalStateException.class).hasMessage("span error");
	}

	@Test
	@DisplayName("Should trace runnable without return value")
	void shouldTraceRunnable() {
		AtomicBoolean executed = new AtomicBoolean(false);
		telemetry.traceRunnable("iam.runnable.span", Map.of(), () -> executed.set(true));
		assertThat(executed.get()).isTrue();
	}

	@Test
	@DisplayName("Should respect disabled metrics property")
	void shouldRespectDisabledMetrics() {
		properties.setMetricsEnabled(false);
		telemetry.recordAccessDenied("missing_permission", "USER_READ", "tenant-1", "user-123");

		assertThat(meterRegistry.find("iam.access.denied").counter()).isNull();
	}
}
