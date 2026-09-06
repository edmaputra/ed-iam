package io.github.edmaputra.iam.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.edmaputra.iam.domain.context.OperationContext;

/**
 * Universal domain event envelope for all Identity and Access Management mutations.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public record IamEvent(
		String eventType,
		UUID tenantId,
		UUID entityId,
		String entityType,
		Object payload,
		String actor,
		String correlationId,
		Instant occurredAt) {

	public IamEvent {
		Objects.requireNonNull(eventType, "Event type must not be null.");
		if (eventType.isBlank()) {
			throw new IllegalArgumentException("Event type must not be blank.");
		}
		Objects.requireNonNull(entityId, "Entity ID must not be null.");
		Objects.requireNonNull(entityType, "Entity type must not be null.");
		Objects.requireNonNull(actor, "Actor must not be null.");
		Objects.requireNonNull(occurredAt, "Occurred-at timestamp must not be null.");
	}

	public static IamEvent of(
			String eventType,
			UUID tenantId,
			UUID entityId,
			String entityType,
			Object payload,
			OperationContext context) {
		Objects.requireNonNull(context, "Operation context must not be null.");
		return new IamEvent(
				eventType,
				tenantId,
				entityId,
				entityType,
				payload,
				context.actor(),
				context.correlationId(),
				Instant.now());
	}

	public static IamEvent of(
			String eventType,
			UUID tenantId,
			UUID entityId,
			String entityType,
			Object payload,
			String actor,
			String correlationId) {
		return new IamEvent(
				eventType,
				tenantId,
				entityId,
				entityType,
				payload,
				actor,
				correlationId,
				Instant.now());
	}

	public Optional<UUID> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	public Optional<String> optionalCorrelationId() {
		return Optional.ofNullable(correlationId);
	}

	public Optional<Object> optionalPayload() {
		return Optional.ofNullable(payload);
	}
}
