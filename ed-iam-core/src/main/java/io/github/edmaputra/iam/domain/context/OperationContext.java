package io.github.edmaputra.iam.domain.context;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.edmaputra.iam.domain.tenancy.TenantId;

/**
 * Cross-cutting operation context carrying actor identification, actor type, optional tenant ID,
 * and optional correlation ID.
 *
 * @param actor         the identifier of the initiating actor (must not be blank)
 * @param actorType     the type of actor initiating the operation (must not be null)
 * @param tenantId      the tenant context ID, or null for platform-wide/system operations
 * @param correlationId optional tracing or correlation ID
 * @author edmaputra
 * @since 0.0.1
 */
public record OperationContext(
		String actor,
		ActorType actorType,
		TenantId tenantId,
		String correlationId) {

	public OperationContext {
		Objects.requireNonNull(actor, "Actor must not be null.");
		if (actor.isBlank()) {
			throw new IllegalArgumentException("Actor must not be blank.");
		}
		Objects.requireNonNull(actorType, "ActorType must not be null.");
	}

	public Optional<TenantId> optionalTenantId() {
		return Optional.ofNullable(tenantId);
	}

	public Optional<UUID> optionalTenantUuid() {
		return Optional.ofNullable(tenantId).map(TenantId::value);
	}

	public Optional<String> optionalCorrelationId() {
		return Optional.ofNullable(correlationId);
	}

	public static OperationContext of(String actor, ActorType actorType, TenantId tenantId, String correlationId) {
		return new OperationContext(actor, actorType, tenantId, correlationId);
	}

	public static OperationContext of(String actor, ActorType actorType, UUID tenantId, String correlationId) {
		return new OperationContext(actor, actorType, tenantId != null ? new TenantId(tenantId) : null, correlationId);
	}

	public static OperationContext of(String actor, ActorType actorType, TenantId tenantId) {
		return new OperationContext(actor, actorType, tenantId, null);
	}

	public static OperationContext of(String actor, ActorType actorType, UUID tenantId) {
		return of(actor, actorType, tenantId, null);
	}

	public static OperationContext of(String actor, ActorType actorType) {
		return new OperationContext(actor, actorType, (TenantId) null, null);
	}

	public static OperationContext of(String actor, String correlationId) {
		return new OperationContext(actor, ActorType.USER, (TenantId) null, correlationId);
	}

	public static OperationContext of(String actor) {
		return new OperationContext(actor, ActorType.USER, (TenantId) null, null);
	}

	public static OperationContext system() {
		return new OperationContext("system", ActorType.SYSTEM, (TenantId) null, null);
	}

	public static OperationContext system(TenantId tenantId) {
		return new OperationContext("system", ActorType.SYSTEM, tenantId, null);
	}

	public static OperationContext system(UUID tenantId) {
		return new OperationContext("system", ActorType.SYSTEM, tenantId != null ? new TenantId(tenantId) : null, null);
	}

	public static OperationContext user(String actor, TenantId tenantId, String correlationId) {
		return new OperationContext(actor, ActorType.USER, tenantId, correlationId);
	}

	public static OperationContext user(String actor, UUID tenantId, String correlationId) {
		return new OperationContext(actor, ActorType.USER, tenantId != null ? new TenantId(tenantId) : null, correlationId);
	}

	public static OperationContext user(String actor, TenantId tenantId) {
		return user(actor, tenantId, null);
	}

	public static OperationContext user(String actor, UUID tenantId) {
		return user(actor, tenantId, null);
	}

	public static OperationContext machine(String actor, TenantId tenantId, String correlationId) {
		return new OperationContext(actor, ActorType.MACHINE, tenantId, correlationId);
	}

	public static OperationContext machine(String actor, UUID tenantId, String correlationId) {
		return new OperationContext(actor, ActorType.MACHINE, tenantId != null ? new TenantId(tenantId) : null, correlationId);
	}

	public static OperationContext machine(String actor, TenantId tenantId) {
		return machine(actor, tenantId, null);
	}

	public static OperationContext machine(String actor, UUID tenantId) {
		return machine(actor, tenantId, null);
	}
}
