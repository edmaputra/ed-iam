package io.github.edmaputra.iam.adapter.security.audit;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SecurityAuditRecorder}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class SecurityAuditRecorderTest {

	private IamTelemetry telemetry;
	private EventPublisherPort publisher;
	private SecurityAuditRecorder recorder;

	@BeforeEach
	void setUp() {
		telemetry = mock(IamTelemetry.class);
		publisher = mock(EventPublisherPort.class);
		recorder = new SecurityAuditRecorder(telemetry, publisher);
	}

	@Test
	@DisplayName("Should record metrics and publish event on login success")
	void shouldRecordLoginSuccess() {
		UUID userId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		Duration duration = Duration.ofMillis(45);

		recorder.recordLoginSuccess("password", "user@hospital.org", userId, tenantId, "127.0.0.1", duration);

		verify(telemetry).recordAuthenticationLatency(eq("password"), eq("success"), eq(tenantId.toString()), eq(duration));
		verify(telemetry).recordAuthenticationAttempt(eq("password"), eq("success"), eq(tenantId.toString()));

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());

		IamEvent event = captor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.LOGIN_SUCCESS);
		assertThat(event.tenantId()).isEqualTo(tenantId);
		assertThat(event.entityId()).isEqualTo(userId);
		assertThat(event.actor()).isEqualTo("user@hospital.org");
	}

	@Test
	@DisplayName("Should record metrics and publish event on login failure")
	void shouldRecordLoginFailure() {
		UUID tenantId = UUID.randomUUID();
		Duration duration = Duration.ofMillis(30);

		recorder.recordLoginFailure("password", "bad@hospital.org", tenantId, "bad_credentials", "Wrong pass", "10.0.0.1", duration);

		verify(telemetry).recordAuthenticationLatency(eq("password"), eq("failure"), eq(tenantId.toString()), eq(duration));
		verify(telemetry).recordAuthenticationAttempt(eq("password"), eq("failure"), eq(tenantId.toString()));
		verify(telemetry).recordAccessDenied(eq("bad_credentials"), eq(null), eq(tenantId.toString()), eq("bad@hospital.org"));

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());

		IamEvent event = captor.getValue();
		assertThat(event.eventType()).isEqualTo(IamEventTypes.LOGIN_FAILED);
		assertThat(event.actor()).isEqualTo("bad@hospital.org");
	}

	@Test
	@DisplayName("Should record account locked")
	void shouldRecordAccountLocked() {
		UUID tenantId = UUID.randomUUID();
		Duration duration = Duration.ofMillis(10);

		recorder.recordAccountLocked("password", "locked@hospital.org", tenantId, "2026-10-05T12:00:00Z", duration);

		verify(telemetry).recordAuthenticationLatency(eq("password"), eq("locked"), eq(tenantId.toString()), eq(duration));
		verify(telemetry).recordAuthenticationAttempt(eq("password"), eq("locked"), eq(tenantId.toString()));
		verify(telemetry).recordAccessDenied(eq("account_locked"), eq(null), eq(tenantId.toString()), eq("locked@hospital.org"));

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.ACCOUNT_LOCKED);
	}

	@Test
	@DisplayName("Should record access denied")
	void shouldRecordAccessDenied() {
		UUID tenantId = UUID.randomUUID();

		recorder.recordAccessDenied("missing_permission", "iam:role:create", tenantId, "actor-1", "/api/v1/roles", Map.of("role", "ADMIN"));

		verify(telemetry).recordAccessDenied(eq("missing_permission"), eq("iam:role:create"), eq(tenantId.toString()), eq("actor-1"));

		ArgumentCaptor<IamEvent> captor = ArgumentCaptor.forClass(IamEvent.class);
		verify(publisher).publish(captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo(IamEventTypes.ACCESS_DENIED);
	}

	@Test
	@DisplayName("Should record token validation")
	void shouldRecordTokenValidation() {
		Duration duration = Duration.ofMillis(5);
		recorder.recordTokenValidation("valid", duration);
		verify(telemetry).recordTokenValidationTime(eq("valid"), eq(duration));
	}

	@Test
	@DisplayName("SecurityAuditRecorder noop should not throw on any method")
	void shouldHandleNoopSafely() {
		SecurityAuditRecorder noop = SecurityAuditRecorder.noop();

		noop.recordLoginSuccess("password", "u", UUID.randomUUID(), UUID.randomUUID(), "ip", Duration.ofSeconds(1));
		noop.recordLoginFailure("password", "u", UUID.randomUUID(), "reason", "msg", "ip", Duration.ofSeconds(1));
		noop.recordAccountLocked("password", "u", UUID.randomUUID(), "locked", Duration.ofSeconds(1));
		noop.recordAccessDenied("reason", "perm", UUID.randomUUID(), "actor", "/path", Map.of());
		noop.recordTokenValidation("valid", Duration.ofMillis(1));
		noop.publish(null);
	}
}
