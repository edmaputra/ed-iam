package io.github.edmaputra.iam.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.ValidatingEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link IamEventValidator} and {@link ValidatingEventPublisher}.
 *
 * @author edmaputra
 * @since 0.9.0
 */
class IamEventValidatorTest {

	private final IamEventValidator validator = new IamEventValidator();

	@Test
	@DisplayName("Should validate valid event successfully")
	void shouldValidateValidEvent() {
		IamEvent event = new IamEvent(
				IamEventTypes.USER_CREATED,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"USER",
				Map.of("email", "test@hospital.org"),
				"admin@hospital.org",
				"corr-1",
				Instant.now()
		);

		IamEvent validated = validator.validateAndSanitize(event);
		assertThat(validated).isNotNull();
		assertThat(validated.eventType()).isEqualTo(IamEventTypes.USER_CREATED);
	}

	@Test
	@DisplayName("Should reject event with invalid eventType characters")
	void shouldRejectInvalidEventType() {
		IamEvent event = new IamEvent(
				"INVALID EVENT TYPE with spaces!",
				UUID.randomUUID(),
				UUID.randomUUID(),
				"USER",
				Map.of(),
				"admin",
				null,
				Instant.now()
		);

		assertThatThrownBy(() -> validator.validate(event))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("contains invalid characters");
	}

	@Test
	@DisplayName("Should sanitize sensitive keys from payload map")
	void shouldSanitizeSensitiveKeys() {
		Map<String, Object> payload = Map.of(
				"email", "user@hospital.org",
				"password", "SuperSecret123!",
				"rawPassword", "plainTextPass",
				"clientSecret", "my-client-secret",
				"token", "bearer-token-val"
		);

		IamEvent event = new IamEvent(
				IamEventTypes.LOGIN_SUCCESS,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"AUTH",
				payload,
				"user@hospital.org",
				null,
				Instant.now()
		);

		IamEvent sanitized = validator.validateAndSanitize(event);
		@SuppressWarnings("unchecked")
		Map<String, Object> sanitizedPayload = (Map<String, Object>) sanitized.payload();

		assertThat(sanitizedPayload.get("email")).isEqualTo("user@hospital.org");
		assertThat(sanitizedPayload.get("password")).isEqualTo("[PROTECTED]");
		assertThat(sanitizedPayload.get("rawPassword")).isEqualTo("[PROTECTED]");
		assertThat(sanitizedPayload.get("clientSecret")).isEqualTo("[PROTECTED]");
		assertThat(sanitizedPayload.get("token")).isEqualTo("[PROTECTED]");
	}

	@Test
	@DisplayName("ValidatingEventPublisher should sanitize and delegate to target publisher")
	void shouldValidateAndDelegate() {
		EventPublisherPort delegate = mock(EventPublisherPort.class);
		ValidatingEventPublisher publisher = ValidatingEventPublisher.of(delegate);

		IamEvent event = new IamEvent(
				IamEventTypes.LOGIN_SUCCESS,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"AUTH",
				Map.of("password", "secret"),
				"admin",
				null,
				Instant.now()
		);

		publisher.publish(event);
		verify(delegate).publish(any(IamEvent.class));
	}

	@Test
	@DisplayName("ValidatingEventPublisher noop should not throw on publish")
	void shouldHandleNoopSafely() {
		ValidatingEventPublisher noop = ValidatingEventPublisher.noop();
		IamEvent event = new IamEvent(
				IamEventTypes.LOGIN_SUCCESS,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"AUTH",
				Map.of(),
				"admin",
				null,
				Instant.now()
		);

		noop.publish(event);
		noop.publish(null);
	}
}
