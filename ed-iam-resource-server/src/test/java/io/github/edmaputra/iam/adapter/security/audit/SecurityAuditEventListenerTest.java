package io.github.edmaputra.iam.adapter.security.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.edmaputra.iam.domain.event.IamEvent;
import io.github.edmaputra.iam.domain.event.IamEventTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link SecurityAuditEventListener}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class SecurityAuditEventListenerTest {

	@Test
	@DisplayName("Should process and log security audit event without exception")
	void shouldProcessSecurityEvent() {
		SecurityAuditEventListener listener = new SecurityAuditEventListener();
		IamEvent event = new IamEvent(
				IamEventTypes.LOGIN_SUCCESS,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"AUTH",
				Map.of("ip", "127.0.0.1"),
				"doctor@hospital.org",
				"trace-999",
				Instant.now());

		assertThatNoException().isThrownBy(() -> listener.onSecurityEvent(event));
	}

	@Test
	@DisplayName("Should handle null or disabled audit logging gracefully")
	void shouldHandleDisabledAudit() {
		IamAuditProperties props = new IamAuditProperties();
		props.setLoggingEnabled(false);
		SecurityAuditEventListener listener = new SecurityAuditEventListener(props);

		assertThatNoException().isThrownBy(() -> listener.onSecurityEvent(null));

		IamEvent event = IamEvent.of(
				IamEventTypes.ACCESS_DENIED,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"SECURITY",
				null,
				"anonymous");

		assertThatNoException().isThrownBy(() -> listener.onSecurityEvent(event));
	}
}
