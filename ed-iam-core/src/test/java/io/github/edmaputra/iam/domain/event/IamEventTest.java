package io.github.edmaputra.iam.domain.event;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.domain.context.OperationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test verifying IAM domain event creation and actor context immutability.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class IamEventTest {

	@Test
	@DisplayName("Should create IamEvent with OperationContext")
	void shouldCreateIamEventWithContext() {
		UUID tenantId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OperationContext context = OperationContext.of("admin@hospital.org", "trace-12345");

		IamEvent event = IamEvent.of(
				IamEventTypes.USER_CREATED,
				tenantId,
				userId,
				"USER",
				"payloadData",
				context);

		assertThat(event.eventType()).isEqualTo(IamEventTypes.USER_CREATED);
		assertThat(event.tenantId()).isEqualTo(tenantId);
		assertThat(event.entityId()).isEqualTo(userId);
		assertThat(event.entityType()).isEqualTo("USER");
		assertThat(event.payload()).isEqualTo("payloadData");
		assertThat(event.actor()).isEqualTo("admin@hospital.org");
		assertThat(event.correlationId()).isEqualTo("trace-12345");
		assertThat(event.occurredAt()).isNotNull();
	}

	@Test
	@DisplayName("Should validate required fields")
	void shouldValidateRequiredFields() {
		UUID id = UUID.randomUUID();
		assertThatThrownBy(() -> IamEvent.of(null, id, id, "USER", null, "actor", null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> IamEvent.of("   ", id, id, "USER", null, "actor", null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
